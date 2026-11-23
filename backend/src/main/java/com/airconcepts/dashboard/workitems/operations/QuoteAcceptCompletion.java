package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.workitems.sync.QuoteMirrorRepository;
import com.airconcepts.dashboard.workitems.sync.QuoteMirrorService;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import com.airconcepts.dashboard.xero.XeroQuoteWriteClient;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records a confirmed Xero acceptance with the same stage rules the quote sync applies. No provider calls here. */
@Service
public class QuoteAcceptCompletion {
  private final QuoteMirrorRepository mirror;
  private final ExternalOperationRepository operations;
  private final Clock clock;

  public QuoteAcceptCompletion(QuoteMirrorRepository mirror, ExternalOperationRepository operations, Clock clock) {
    this.mirror = mirror;
    this.operations = operations;
    this.clock = clock;
  }

  @Transactional
  public void complete(UUID actor, UUID entity, UUID item, UUID tenantId, UUID grantId, UUID operation,
      XeroQuoteWriteClient.Receipt receipt) {
    if (receipt == null || receipt.id() == null || receipt.status() != XeroQuoteClient.Status.ACCEPTED) throw changed();
    var config = mirror.lockConfig(entity).orElseThrow(QuoteAcceptCompletion::changed);
    var mapping = new QuoteMirrorService.Mapping(entity, tenantId, grantId);
    if (!mirror.lockMapping(mapping) || !mirror.provenanceMatches(mapping)) throw changed();
    var stored = mirror.lockQuote(entity, receipt.id()).orElseThrow(QuoteAcceptCompletion::changed);
    var pending = operations.lockOperation(entity, item, operation).orElseThrow(QuoteAcceptCompletion::changed);
    if (!stored.id().equals(item) || pending.kind() != ExternalOperationRepository.Kind.XERO_ACCEPT_QUOTE
        || pending.status() != OperationState.Status.PENDING || stored.state().stage() != WorkItemStage.QUOTED
        || stored.quote().status() != XeroQuoteClient.Status.SENT) throw changed();
    var transition = StageMachine.apply(stored.state(),
        new StageMachine.QuoteObserved(StageMachine.QuoteStatus.ACCEPTED), config.owners());
    var next = transition.state();
    if (next.stage() != WorkItemStage.TO_SCHEDULE) throw changed();
    var quote = stored.quote();
    var accepted = new XeroQuoteClient.Quote(quote.id(), quote.number(), quote.reference(), XeroQuoteClient.Status.ACCEPTED,
        quote.contactId(), quote.contactName(), quote.date(), quote.expiryDate(), quote.updatedAt(), quote.total(),
        quote.currency(), quote.lineAmountTypes(), null);
    if (mirror.update(entity, item, accepted, next, stored.version(), clock.instant(), true, null) != 1) throw changed();
    if (operations.complete(entity, item, operation, OperationState.Result.ACCEPTED, receipt.id().toString()) != 1) throw changed();
    operations.clearResolvedUnknown(entity, item);
    operations.clearResolvedFailed(entity, item);
    operations.auditQuoteAccepted(entity, item, actor, operation, stored.state().stage().name(), next.stage().name(),
        stored.state().ownerId(), next.ownerId());
  }

  private static ApiFailure changed() {
    return new ApiFailure(HttpStatus.CONFLICT, "OPERATION_STATE_CHANGED",
        "The quote or connection changed. Refresh and review the job.");
  }
}
