package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.invoices.InvoiceStatus;
import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.time.WorkingDays;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.*;
import com.airconcepts.dashboard.workitems.sync.*;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirms the invoice and its reserved operation together; no provider calls occur here. */
@Service
public class InvoiceOperationCompletion {
  private final QuoteMirrorRepository mappings;
  private final InvoiceMirrorRepository invoices;
  private final ExternalOperationRepository operations;
  private final Clock clock;
  private final WorkingDays workingDays=new WorkingDays(Set.of());
  public InvoiceOperationCompletion(QuoteMirrorRepository mappings, InvoiceMirrorRepository invoices,
      ExternalOperationRepository operations, Clock clock) {
    this.mappings=mappings; this.invoices=invoices; this.operations=operations; this.clock=clock;
  }
  @Transactional
  public void complete(UUID entity, UUID itemId, InvoiceDraftContext.Context context, UUID operation,
      XeroInvoiceClient.WorkflowInvoice invoice, OperationState.Result result) {
    if (context==null || invoice==null || invoice.id()==null || invoice.status()==null
        || (result!=OperationState.Result.CREATED && result!=OperationState.Result.LINKED_EXISTING)
        || (result==OperationState.Result.CREATED && invoice.status()!=InvoiceStatus.DRAFT)
        || invoice.status()==InvoiceStatus.VOIDED || invoice.status()==InvoiceStatus.DELETED) throw changed();
    var mapping=new QuoteMirrorService.Mapping(entity,context.tenantId(),context.grantId());
    var config=mappings.lockConfig(entity).orElseThrow(InvoiceOperationCompletion::changed);
    if (!mappings.lockMapping(mapping)) throw changed();
    var item=invoices.lock(mapping,itemId).orElseThrow(InvoiceOperationCompletion::changed);
    var candidate=new InvoiceMirrorRepository.Unlinked(itemId,context.jobId(),context.quoteNumber());
    var pending=operations.lockOperation(entity,itemId,operation).orElseThrow(InvoiceOperationCompletion::changed);
    if (item.invoiceId()!=null || item.state().stage()!=WorkItemStage.JOB_COMPLETED || !invoices.referenceMatches(mapping,candidate)
        || pending.kind()!=ExternalOperationRepository.Kind.XERO_CREATE_INVOICE
        || (pending.status()!=OperationState.Status.PENDING && pending.status()!=OperationState.Status.UNKNOWN)
        || (result==OperationState.Result.CREATED && pending.status()!=OperationState.Status.PENDING)
        || pending.lookupReference()==null || invoice.reference()==null
        || !invoice.reference().toLowerCase(Locale.ROOT).contains(pending.lookupReference().toLowerCase(Locale.ROOT))) throw changed();
    var observed=StageMachine.apply(item.state(),new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.valueOf(invoice.status().name()),
        false,invoice.amountDue()),config.owners());
    var after=observed.state();
    var attention=new HashSet<>(after.attention());
    attention.remove("QUOTE_INVOICED_IN_XERO"); attention.remove("INVOICE_MULTIPLE_MATCHES");
    var next=new StageMachine.State(after.stage(),after.ownerId(),after.closeReason(),after.closingNote(),after.jobLinked(),after.invoiceLinked(),attention);
    var now=clock.instant();
    var late=next.stage().awaitsFinance()
        ? workingDays.addWorkingDays(BusinessTime.dateOf(now),config.threshold()) : null;
    try {
      if (invoices.link(mapping,candidate,invoice,next,item.version(),now,late)!=1) throw changed();
      if (operations.complete(entity,itemId,operation,result,invoice.id().toString())!=1) throw changed();
      if (result==OperationState.Result.CREATED) invoices.markReportPending(mapping,itemId);
      operations.clearResolvedUnknown(entity,itemId);
      operations.clearResolvedFailed(entity,itemId);
    } catch (DuplicateKeyException duplicate) { throw changed(); }
    operations.auditInvoiceCompleted(entity,itemId,operation,result);
    if (observed.event()!=null && !"INVOICE_LINKED".equals(observed.event()))
      invoices.audit(mapping,itemId,invoice,item,next,observed.event());
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"OPERATION_STATE_CHANGED","The invoice or connection changed. Refresh and review it."); }
}
