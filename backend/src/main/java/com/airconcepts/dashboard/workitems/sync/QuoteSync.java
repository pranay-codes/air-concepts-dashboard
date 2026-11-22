package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import com.airconcepts.dashboard.xero.XeroScopes;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/** Applies a bounded read as individual mirror transactions; only a proven end can complete a run. */
public final class QuoteSync {
  public enum Continuation { MORE, COMPLETE, UNKNOWN }
  public record Page(List<XeroQuoteClient.Quote> quotes, Continuation continuation) {
    public Page { quotes = List.copyOf(quotes); Objects.requireNonNull(continuation); }
  }
  public interface Source {
    Page page(TenantAccess access, XeroQuoteClient.Status status, int page, Instant modifiedSince);
  }
  public interface InvoiceLookup {
    QuoteMirrorService.InvoiceMatch find(TenantAccess access, XeroQuoteClient.Quote quote);
  }
  public record Result(int seen, int created, int updated, int unchanged, int skipped, int stale, boolean complete) {}
  private static final int MAX_PAGES_PER_STATUS = 1000;
  private static final int MAX_PAGE_SIZE = 100;
  private static final List<XeroQuoteClient.Status> STATUSES = List.of(XeroQuoteClient.Status.SENT,
      XeroQuoteClient.Status.ACCEPTED, XeroQuoteClient.Status.DECLINED, XeroQuoteClient.Status.INVOICED,
      XeroQuoteClient.Status.DELETED);
  private final QuoteMirrorService mirror;
  private final Source source;
  private final InvoiceLookup invoiceLookup;
  public QuoteSync(QuoteMirrorService mirror, Source source, InvoiceLookup invoiceLookup) {
    this.mirror = Objects.requireNonNull(mirror); this.source = Objects.requireNonNull(source);
    this.invoiceLookup = Objects.requireNonNull(invoiceLookup);
  }

  public Result run(UUID entity, Supplier<TenantAccess> access, Instant modifiedSince) {
    Objects.requireNonNull(entity); Objects.requireNonNull(access);
    var original = checkAccess(entity, access.get(), null);
    Set<UUID> seenIds = new HashSet<>();
    int seen = 0, created = 0, updated = 0, unchanged = 0, skipped = 0, stale = 0;
    for (var status : STATUSES) {
      boolean complete = false;
      for (int index = 1; index <= MAX_PAGES_PER_STATUS; index++) {
        var current = checkAccess(entity, access.get(), original);
        var page = source.page(current, status, index, modifiedSince);
        if (page == null || page.quotes().size() > MAX_PAGE_SIZE
            || (page.continuation() == Continuation.MORE && page.quotes().isEmpty())) throw invalidPage();
        for (var quote : page.quotes()) {
          if (quote.id() == null || quote.status() != status || !seenIds.add(quote.id())) throw invalidPage();
          current = checkAccess(entity, access.get(), original);
          var outcome = mirror.apply(mapping(current), quote, QuoteMirrorService.InvoiceMatch.UNCHECKED);
          if (outcome == QuoteMirrorService.Outcome.NEEDS_INVOICE_CHECK) {
            current = checkAccess(entity, access.get(), original);
            var match = invoiceLookup.find(current, quote);
            if (match == null || match == QuoteMirrorService.InvoiceMatch.UNCHECKED)
              throw failure("QUOTE_SYNC_INVOICE_CHECK_REQUIRED", "An invoice check is required before completing this sync.");
            current = checkAccess(entity, access.get(), original);
            outcome = mirror.apply(mapping(current), quote, match);
            if (outcome == QuoteMirrorService.Outcome.NEEDS_INVOICE_CHECK)
              throw failure("QUOTE_SYNC_INVOICE_CHECK_REQUIRED", "An invoice check is required before completing this sync.");
          }
          if (outcome == null) throw invalidPage();
          seen++;
          switch (outcome) {
            case CREATED -> created++;
            case UPDATED -> updated++;
            case UNCHANGED -> unchanged++;
            case SKIPPED -> skipped++;
            case STALE -> stale++;
            case NEEDS_INVOICE_CHECK -> throw new IllegalStateException("Unresolved invoice check");
          }
        }
        if (page.continuation() == Continuation.UNKNOWN)
          throw failure("QUOTE_SYNC_COMPLETENESS_UNVERIFIED", "Quote pagination completeness needs verification before sync can succeed.");
        if (page.continuation() == Continuation.COMPLETE) { complete = true; break; }
      }
      if (!complete) throw failure("QUOTE_SYNC_PAGE_LIMIT", "The quote sync exceeded its page limit.");
    }
    checkAccess(entity, access.get(), original);
    return new Result(seen, created, updated, unchanged, skipped, stale, true);
  }

  private static TenantAccess checkAccess(UUID entity, TenantAccess current, TenantAccess original) {
    if (current == null || !entity.equals(current.entityId()) || current.tenantId() == null || current.grantId() == null
        || (original != null && (!original.tenantId().equals(current.tenantId()) || !original.grantId().equals(current.grantId()))))
      throw failure("QUOTE_SYNC_CONNECTION_CHANGED", "The Xero connection changed. Rerun the sync.");
    XeroScopes.require(current.grantedScopes(), XeroScopes.QUOTES_READ);
    return current;
  }
  private static QuoteMirrorService.Mapping mapping(TenantAccess access) {
    return new QuoteMirrorService.Mapping(access.entityId(), access.tenantId(), access.grantId());
  }
  private static ApiFailure invalidPage() { return failure("QUOTE_SYNC_INVALID_PAGE", "Xero returned inconsistent quote pages. Rerun the sync."); }
  private static ApiFailure failure(String code, String message) { return new ApiFailure(HttpStatus.CONFLICT, code, message); }
}
