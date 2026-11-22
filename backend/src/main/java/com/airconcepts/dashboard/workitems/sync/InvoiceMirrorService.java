package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.time.WorkingDays;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceMirrorService {
  private final InvoiceMirrorRepository invoices;
  private final QuoteMirrorRepository quotes;
  private final Clock clock;
  private final WorkingDays workingDays=new WorkingDays(Set.of());
  public InvoiceMirrorService(InvoiceMirrorRepository invoices, QuoteMirrorRepository quotes, Clock clock) {
    this.invoices=invoices; this.quotes=quotes; this.clock=clock;
  }
  @Transactional
  public void apply(QuoteMirrorService.Mapping mapping, UUID itemId, XeroInvoiceClient.WorkflowInvoice invoice, boolean sentVerified) {
    if (invoice==null || invoice.id()==null || invoice.status()==null) throw changed();
    var settings=quotes.lockConfig(mapping.entityId()).orElseThrow(InvoiceMirrorService::changed);
    if (!quotes.lockMapping(mapping)) throw changed();
    var item=invoices.lock(mapping,itemId).orElseThrow(InvoiceMirrorService::changed);
    if (!invoice.id().equals(item.invoiceId()) || (item.state().stage()!=WorkItemStage.TO_INVOICE
        && item.state().stage()!=WorkItemStage.INVOICE_DRAFTED && item.state().stage()!=WorkItemStage.INVOICED)) throw changed();
    var status=StageMachine.InvoiceStatus.valueOf(invoice.status().name());
    var result=StageMachine.apply(item.state(),new StageMachine.InvoiceObserved(status,
        sentVerified && Boolean.TRUE.equals(invoice.sentToContact()),invoice.amountDue()),settings.owners());
    var now=clock.instant();
    var next=result.state();
    if (next.equals(item.state()) && Objects.equals(item.number(),invoice.number())
        && Objects.equals(item.status(),invoice.status().name()) && Objects.equals(item.sent(),invoice.sentToContact())
        && sameAmount(item.total(),invoice.total()) && sameAmount(item.amountDue(),invoice.amountDue())
        && Objects.equals(item.dueDate(),invoice.dueDate())) return;
    if (quotes.hasUnresolvedOperation(mapping.entityId(),itemId))
      throw new ApiFailure(HttpStatus.CONFLICT,"INVOICE_SYNC_OPERATION_IN_PROGRESS","Resolve the existing operation before syncing this invoice.");
    LocalDate late=(next.stage()==WorkItemStage.TO_INVOICE || next.stage()==WorkItemStage.INVOICE_DRAFTED)
        ? workingDays.addWorkingDays(BusinessTime.dateOf(now),settings.threshold()) : null;
    if (invoices.update(mapping,itemId,invoice,next,item.version(),now,late,item.state())!=1) throw changed();
    if (result.event()!=null) invoices.audit(mapping,itemId,invoice,item,next,result.event());
  }
  @Transactional
  public void link(QuoteMirrorService.Mapping mapping, InvoiceMirrorRepository.Unlinked candidate,
      XeroInvoiceClient.WorkflowInvoice invoice, boolean sentVerified) {
    if (invoice==null || invoice.id()==null || invoice.status()==null
        || invoice.status()==com.airconcepts.dashboard.invoices.InvoiceStatus.VOIDED
        || invoice.status()==com.airconcepts.dashboard.invoices.InvoiceStatus.DELETED) throw changed();
    var settings=quotes.lockConfig(mapping.entityId()).orElseThrow(InvoiceMirrorService::changed);
    if (!quotes.lockMapping(mapping)) throw changed();
    var item=invoices.lock(mapping,candidate.itemId()).orElseThrow(InvoiceMirrorService::changed);
    if (item.invoiceId()!=null || item.state().stage()!=WorkItemStage.TO_INVOICE || !invoices.referenceMatches(mapping,candidate)) throw changed();
    if (quotes.hasUnresolvedOperation(mapping.entityId(),candidate.itemId()))
      throw new ApiFailure(HttpStatus.CONFLICT,"INVOICE_SYNC_OPERATION_IN_PROGRESS","Resolve the existing operation before syncing this invoice.");
    var result=StageMachine.apply(item.state(),new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.valueOf(invoice.status().name()),
        sentVerified && Boolean.TRUE.equals(invoice.sentToContact()),invoice.amountDue()),settings.owners());
    var codes=new HashSet<>(result.state().attention());
    codes.remove("QUOTE_INVOICED_IN_XERO"); codes.remove("INVOICE_MULTIPLE_MATCHES");
    var after=result.state();
    var next=new StageMachine.State(after.stage(),after.ownerId(),after.closeReason(),after.closingNote(),after.jobLinked(),after.invoiceLinked(),codes);
    var now=clock.instant();
    var late=next.stage()==WorkItemStage.TO_INVOICE || next.stage()==WorkItemStage.INVOICE_DRAFTED
        ? workingDays.addWorkingDays(BusinessTime.dateOf(now),settings.threshold()) : null;
    try { if (invoices.link(mapping,candidate,invoice,next,item.version(),now,late)!=1) throw changed(); }
    catch (DuplicateKeyException duplicate) { throw changed(); }
    invoices.audit(mapping,candidate.itemId(),invoice,item,next,"INVOICE_LINKED");
    if (result.event()!=null && !"INVOICE_LINKED".equals(result.event()))
      invoices.audit(mapping,candidate.itemId(),invoice,item,next,result.event());
  }
  @Transactional
  public void multiple(QuoteMirrorService.Mapping mapping, InvoiceMirrorRepository.Unlinked candidate) {
    quotes.lockConfig(mapping.entityId()).orElseThrow(InvoiceMirrorService::changed);
    if (!quotes.lockMapping(mapping)) throw changed();
    var item=invoices.lock(mapping,candidate.itemId()).orElseThrow(InvoiceMirrorService::changed);
    if (item.invoiceId()!=null || item.state().stage()!=WorkItemStage.TO_INVOICE || !invoices.referenceMatches(mapping,candidate)) throw changed();
    if (item.state().attention().contains("INVOICE_MULTIPLE_MATCHES")) return;
    if (quotes.hasUnresolvedOperation(mapping.entityId(),candidate.itemId()))
      throw new ApiFailure(HttpStatus.CONFLICT,"INVOICE_SYNC_OPERATION_IN_PROGRESS","Resolve the existing operation before syncing this invoice.");
    if (invoices.markMultiple(mapping,candidate,item.version())!=1) throw changed();
    var codes=new HashSet<>(item.state().attention()); codes.add("INVOICE_MULTIPLE_MATCHES");
    var before=item.state();
    var after=new StageMachine.State(before.stage(),before.ownerId(),before.closeReason(),before.closingNote(),before.jobLinked(),before.invoiceLinked(),codes);
    quotes.audit(mapping.entityId(),candidate.itemId(),before,after,"ATTENTION_RAISED");
  }
  private static boolean sameAmount(java.math.BigDecimal a, java.math.BigDecimal b) {
    return a==null ? b==null : b!=null && a.compareTo(b)==0;
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"INVOICE_SYNC_CHANGED","The invoice or connection changed. Retry sync."); }
}
