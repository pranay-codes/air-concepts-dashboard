package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.time.WorkingDays;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import java.math.BigDecimal;
import java.time.*;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies one already-read quote. It does not call Xero or assert that a page is complete. */
@Service
public class QuoteMirrorService {
  public enum InvoiceMatch { FOUND, NOT_FOUND, UNCHECKED }
  public enum Outcome { CREATED, UPDATED, UNCHANGED, SKIPPED, NEEDS_INVOICE_CHECK, STALE }
  /** Identity only; provider tokens never enter the repository. */
  public record Mapping(UUID entityId, UUID tenantId, UUID grantId) {
    public Mapping { Objects.requireNonNull(entityId); Objects.requireNonNull(tenantId); Objects.requireNonNull(grantId); }
  }
  private final QuoteMirrorRepository repository;
  private final Clock clock;
  private final WorkingDays workingDays = new WorkingDays(Set.of());
  public QuoteMirrorService(QuoteMirrorRepository repository, Clock clock) {
    this.repository = repository; this.clock = clock;
  }

  @Transactional
  public Outcome apply(Mapping mapping, XeroQuoteClient.Quote quote, InvoiceMatch invoiceMatch) {
    Objects.requireNonNull(mapping, "mapping");
    UUID entity = mapping.entityId();
    Objects.requireNonNull(quote, "quote");
    Objects.requireNonNull(quote.id(), "quote id");
    Objects.requireNonNull(quote.status(), "quote status");
    Objects.requireNonNull(quote.updatedAt(), "quote updated time");
    Objects.requireNonNull(invoiceMatch, "invoiceMatch");
    var config = repository.lockConfig(entity);
    if (config.isEmpty()) return Outcome.SKIPPED;
    if (!repository.lockMapping(mapping)) {
      throw new ApiFailure(HttpStatus.CONFLICT, "QUOTE_SYNC_CONNECTION_CHANGED", "The Xero connection changed. Rerun the sync.");
    }
    if (!repository.provenanceMatches(mapping)) {
      throw new ApiFailure(HttpStatus.CONFLICT, "XERO_TENANT_CHANGED", "Work items need review after the Xero connection changed.");
    }
    repository.lockIdentity(entity, quote.id());
    var old = repository.lockQuote(entity, quote.id());
    var now = clock.instant();
    var status = StageMachine.QuoteStatus.valueOf(quote.status().name());
    if (old.isEmpty()) {
      var today = BusinessTime.dateOf(now);
      var cutoff = today.minusDays(config.get().backfillDays());
      boolean within = (quote.date() != null && !quote.date().isBefore(cutoff))
          || !BusinessTime.dateOf(quote.updatedAt()).isBefore(cutoff);
      if (config.get().firstSync() && status == StageMachine.QuoteStatus.ACCEPTED && within
          && invoiceMatch == InvoiceMatch.UNCHECKED) return Outcome.NEEDS_INVOICE_CHECK;
      var initial = new StageMachine.InitialContext(true, config.get().firstSync(), within, invoiceMatch == InvoiceMatch.FOUND);
      var created = StageMachine.create(status, initial, config.get().owners());
      if (created.isEmpty()) return Outcome.SKIPPED;
      var next = created.get().state();
      String origin = config.get().firstSync() && status == StageMachine.QuoteStatus.ACCEPTED ? "BACKFILL" : "SYNC";
      UUID id = repository.insert(mapping, quote, next, now, lateAfter(next.stage(), now, config.get().threshold()), origin);
      repository.audit(entity, id, null, next, "ITEM_CREATED");
      return Outcome.CREATED;
    }
    var stored = old.get();
    if (stored.quote().updatedAt() != null && quote.updatedAt().isBefore(stored.quote().updatedAt())) return Outcome.STALE;
    var transition = StageMachine.apply(stored.state(), new StageMachine.QuoteObserved(status), config.get().owners());
    if (transition.event() == null && sameMirror(stored.quote(), quote)) return Outcome.UNCHANGED;
    // A changed quote can alter the reference, stage or source data used by an in-flight write.
    // Fail this run rather than advancing its watermark or silently changing reconciliation inputs.
    if (repository.hasUnresolvedOperation(entity, stored.id())) {
      throw new ApiFailure(HttpStatus.CONFLICT, "QUOTE_SYNC_OPERATION_IN_PROGRESS",
          "Resolve the existing operation before synchronising this quote.");
    }
    boolean stageChanged = transition.state().stage() != stored.state().stage();
    var deadline = stageChanged ? lateAfter(transition.state().stage(), now, config.get().threshold()) : stored.lateAfter();
    if (repository.update(entity, stored.id(), quote, transition.state(), stored.version(), now, stageChanged, deadline) != 1) {
      throw new ApiFailure(HttpStatus.CONFLICT, "WORK_ITEM_CHANGED", "This work item changed during synchronisation. Retry.");
    }
    if (transition.event() != null) repository.audit(entity, stored.id(), stored.state(), transition.state(), transition.event());
    return Outcome.UPDATED;
  }

  private LocalDate lateAfter(WorkItemStage stage, Instant entered, int threshold) {
    return stage == WorkItemStage.TO_INVOICE || stage == WorkItemStage.INVOICE_DRAFTED
        ? workingDays.addWorkingDays(BusinessTime.dateOf(entered), threshold) : null;
  }

  private static boolean sameMirror(XeroQuoteClient.Quote a, XeroQuoteClient.Quote b) {
    return Objects.equals(a.number(), b.number()) && Objects.equals(a.reference(), b.reference())
        && a.status() == b.status() && Objects.equals(a.contactId(), b.contactId())
        && Objects.equals(a.contactName(), b.contactName()) && Objects.equals(a.date(), b.date())
        && Objects.equals(a.expiryDate(), b.expiryDate()) && Objects.equals(a.updatedAt(), b.updatedAt())
        && sameNumber(a.total(), b.total()) && Objects.equals(a.currency(), b.currency());
  }
  private static boolean sameNumber(BigDecimal a, BigDecimal b) {
    return a == null ? b == null : b != null && a.compareTo(b) == 0;
  }
}
