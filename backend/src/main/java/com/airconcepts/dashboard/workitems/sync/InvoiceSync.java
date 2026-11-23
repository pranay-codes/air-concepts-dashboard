package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import com.airconcepts.dashboard.xero.XeroScopes;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/** Refreshes linked sales invoices using a server-resolved Xero mapping. */
public final class InvoiceSync {
  public interface Source { XeroInvoiceClient.WorkflowInvoice read(TenantAccess access, UUID invoice); }
  public interface Lookup { Set<UUID> find(TenantAccess access, String jobId, String quoteNumber); }
  private final InvoiceMirrorRepository repository;
  private final InvoiceMirrorService mirror;
  private final Source source;
  private final boolean sentVerified;
  private final Lookup lookup;
  private final boolean earlyLookupVerified;
  private final boolean paidTrackingEnabled;
  private final boolean requireContactMatch;
  public InvoiceSync(InvoiceMirrorRepository repository, InvoiceMirrorService mirror, Source source, boolean sentVerified) {
    this(repository,mirror,source,sentVerified,null);
  }
  public InvoiceSync(InvoiceMirrorRepository repository, InvoiceMirrorService mirror, Source source, boolean sentVerified, Lookup lookup) {
    this(repository,mirror,source,sentVerified,lookup,false);
  }
  public InvoiceSync(InvoiceMirrorRepository repository, InvoiceMirrorService mirror, Source source, boolean sentVerified, Lookup lookup, boolean earlyLookupVerified) {
    this(repository,mirror,source,sentVerified,lookup,earlyLookupVerified,earlyLookupVerified);
  }
  public InvoiceSync(InvoiceMirrorRepository repository, InvoiceMirrorService mirror, Source source, boolean sentVerified, Lookup lookup, boolean earlyLookupVerified, boolean paidTrackingEnabled) {
    this(repository,mirror,source,sentVerified,lookup,earlyLookupVerified,paidTrackingEnabled,false);
  }
  public InvoiceSync(InvoiceMirrorRepository repository, InvoiceMirrorService mirror, Source source, boolean sentVerified, Lookup lookup, boolean earlyLookupVerified, boolean paidTrackingEnabled, boolean requireContactMatch) {
    this.repository=repository; this.mirror=mirror; this.source=source; this.sentVerified=sentVerified; this.lookup=lookup;
    this.earlyLookupVerified=earlyLookupVerified;
    this.paidTrackingEnabled=paidTrackingEnabled;
    this.requireContactMatch=requireContactMatch;
  }
  public int run(UUID entity, Supplier<TenantAccess> resolver) {
    var original=check(entity,resolver.get(),null);
    int seen=0;
    boolean partial=false;
    var candidates = new ArrayList<>(repository.candidates(entity));
    if (paidTrackingEnabled) candidates.addAll(repository.paidCandidates(entity));
    for (var candidate : candidates) {
      try {
      var current=check(entity,resolver.get(),original);
      var invoice=source.read(current,candidate.invoiceId());
      if (invoice==null || !candidate.invoiceId().equals(invoice.id())) throw changed();
      current=check(entity,resolver.get(),original);
      mirror.apply(new QuoteMirrorService.Mapping(entity,current.tenantId(),current.grantId()),candidate.itemId(),invoice,sentVerified);
      seen++;
      } catch (ApiFailure failure) {
        if (!itemFailure(failure)) throw failure;
        partial=true;
      }
    }
    if (lookup!=null) for (var candidate : earlyLookupVerified ? repository.unlinkedAllOpen(entity) : repository.unlinked(entity)) {
      try {
      var current=check(entity,resolver.get(),original);
      var matches=lookup.find(current,candidate.jobId(),candidate.quoteNumber());
      current=check(entity,resolver.get(),original);
      if (matches==null || matches.stream().anyMatch(Objects::isNull)) throw changed();
      var mapping=new QuoteMirrorService.Mapping(entity,current.tenantId(),current.grantId());
      if (earlyLookupVerified) {
        if (matches.isEmpty()) continue;
        if (matches.size()>3) throw new ApiFailure(HttpStatus.CONFLICT,"INVOICE_LOOKUP_INCOMPLETE","Too many possible invoice matches need review.");
        if (repository.conflictingReferences(mapping,candidate)) { mirror.multiple(mapping,candidate); continue; }
        var valid=new ArrayList<XeroInvoiceClient.WorkflowInvoice>();
        for (var id : matches) {
          current=check(entity,resolver.get(),original);
          var invoice=source.read(current,id);
          current=check(entity,resolver.get(),original);
          if (invoice==null || !id.equals(invoice.id())) throw changed();
          if (invoice.status()!=com.airconcepts.dashboard.invoices.InvoiceStatus.VOIDED
              && invoice.status()!=com.airconcepts.dashboard.invoices.InvoiceStatus.DELETED
              && matchesReference(invoice.reference(),candidate,true)
              && (!requireContactMatch || (candidate.contactId()!=null && candidate.contactId().equals(invoice.contactId())))) valid.add(invoice);
        }
        if (valid.size()>1) { mirror.multiple(mapping,candidate); continue; }
        if (valid.isEmpty()) continue;
        if (repository.invoiceConflictsWithOtherWorkItems(mapping,candidate,valid.getFirst())) {
          mirror.multiple(mapping,candidate); continue;
        }
        mirror.link(mapping,candidate,valid.getFirst(),sentVerified);
        seen++;
        continue;
      }
      if (matches.size()>1) { mirror.multiple(mapping,candidate); continue; }
      if (matches.isEmpty()) continue;
      var id=matches.iterator().next();
      var invoice=source.read(current,id);
      current=check(entity,resolver.get(),original);
      if (invoice==null || !id.equals(invoice.id()) || !matchesReference(invoice.reference(),candidate,earlyLookupVerified)) throw changed();
      if (repository.conflictingReferences(mapping,candidate)
          || repository.invoiceConflictsWithOtherWorkItems(mapping,candidate,invoice)) {
        mirror.multiple(mapping,candidate); continue;
      }
      mirror.link(new QuoteMirrorService.Mapping(entity,current.tenantId(),current.grantId()),candidate,invoice,sentVerified);
      seen++;
      } catch (ApiFailure failure) {
        if (Set.of("XERO_DAILY_BUDGET_RESERVED","XERO_DAILY_BUDGET_UNAVAILABLE").contains(failure.code())) {
          partial=true;
          break;
        }
        if (!itemFailure(failure)) throw failure;
        partial=true;
      }
    }
    check(entity,resolver.get(),original);
    if (partial) throw new ApiFailure(HttpStatus.CONFLICT,"INVOICE_SYNC_PARTIAL","Some invoices could not be refreshed. Review and retry.");
    return seen;
  }
  private static boolean itemFailure(ApiFailure failure) {
    return Set.of("INVOICE_LOOKUP_INCOMPLETE","INVOICE_SYNC_CHANGED","INVOICE_SYNC_OPERATION_IN_PROGRESS").contains(failure.code())
        || (failure instanceof com.airconcepts.dashboard.web.ProviderFailure provider && provider.upstreamStatus()==404);
  }
  static boolean matchesReference(String reference, InvoiceMirrorRepository.Unlinked candidate, boolean strict) {
    if (reference==null) return false;
    String text=reference.toLowerCase(Locale.ROOT);
    for (var term : new String[]{candidate.jobId(),candidate.quoteNumber()})
      if (term!=null && !term.isBlank()) {
        String normalized=term.strip().toLowerCase(Locale.ROOT);
        if (strict ? containsIdentifier(text,normalized) : text.contains(normalized)) return true;
      }
    return false;
  }
  private static boolean containsIdentifier(String text, String term) {
    for (int index=text.indexOf(term); index>=0; index=text.indexOf(term,index+1)) {
      int end=index+term.length();
      if ((index==0 || !identifierCharacter(text.charAt(index-1)))
          && (end==text.length() || !identifierCharacter(text.charAt(end)))) return true;
    }
    return false;
  }
  private static boolean identifierCharacter(char c) { return Character.isLetterOrDigit(c) || c=='-' || c=='_'; }
  private static TenantAccess check(UUID entity, TenantAccess current, TenantAccess original) {
    if (current==null || !entity.equals(current.entityId()) || current.tenantId()==null || current.grantId()==null
        || (original!=null && (!original.tenantId().equals(current.tenantId()) || !original.grantId().equals(current.grantId())))) throw changed();
    XeroScopes.require(current.grantedScopes(),XeroScopes.QUOTES_READ);
    return current;
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"INVOICE_SYNC_CONNECTION_CHANGED","The Xero invoice or connection changed. Retry sync."); }
}
