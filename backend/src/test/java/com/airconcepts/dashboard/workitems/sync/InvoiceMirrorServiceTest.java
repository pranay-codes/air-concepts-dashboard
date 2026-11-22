package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.invoices.InvoiceStatus;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoiceMirrorServiceTest {
  private final UUID entity=UUID.randomUUID(), tenant=UUID.randomUUID(), grant=UUID.randomUUID(), item=UUID.randomUUID(), invoice=UUID.randomUUID();
  private final QuoteMirrorService.Mapping mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
  private final InvoiceMirrorRepository invoices=mock(InvoiceMirrorRepository.class);
  private final QuoteMirrorRepository quotes=mock(QuoteMirrorRepository.class);
  private final InvoiceMirrorService service=new InvoiceMirrorService(invoices,quotes,Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"),ZoneOffset.UTC));
  private final StageMachine.Settings settings=new StageMachine.Settings(null,false,null,false);
  private void setup(WorkItemStage stage) {
    when(quotes.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(false,30,2,settings)));
    when(quotes.lockMapping(mapping)).thenReturn(true);
    var state=new StageMachine.State(stage,null,null,null,true,true,Set.of());
    when(invoices.lock(mapping,item)).thenReturn(Optional.of(new InvoiceMirrorRepository.Stored(4,invoice,state)));
    when(invoices.update(any(),any(),any(),any(),anyInt(),any(),any(),any())).thenReturn(1);
  }
  private XeroInvoiceClient.WorkflowInvoice invoice(InvoiceStatus status, String due) {
    return new XeroInvoiceClient.WorkflowInvoice(invoice,"INV-1",status,new BigDecimal("100"),new BigDecimal(due),LocalDate.parse("2026-12-01"),true);
  }
  @Test void gl21_partialPaymentStaysInvoicedAndZeroBalanceClosesPaid() {
    setup(WorkItemStage.INVOICED);
    service.apply(mapping,item,invoice(InvoiceStatus.AUTHORISED,"40"),false);
    verify(invoices).update(eq(mapping),eq(item),any(),argThat(s -> s.stage()==WorkItemStage.INVOICED),eq(4),any(),any(),any());
    service.apply(mapping,item,invoice(InvoiceStatus.PAID,"0"),false);
    verify(invoices).update(eq(mapping),eq(item),any(),argThat(s -> s.stage()==WorkItemStage.CLOSED && "PAID".equals(s.closeReason())),eq(4),any(),any(),any());
  }
  @Test void gl21_voidUnlinksAndRaisesAttention() {
    setup(WorkItemStage.INVOICED);
    service.apply(mapping,item,invoice(InvoiceStatus.VOIDED,"0"),false);
    verify(invoices).update(eq(mapping),eq(item),any(),argThat(s -> s.stage()==WorkItemStage.TO_INVOICE && !s.invoiceLinked() && s.attention().contains("INVOICE_VOIDED")),eq(4),any(),any(),any());
  }
  @Test void gl21_unverifiedSendFlagCannotMoveADraftToInvoiced() {
    setup(WorkItemStage.INVOICE_DRAFTED);
    service.apply(mapping,item,invoice(InvoiceStatus.AUTHORISED,"40"),false);
    verify(invoices).update(eq(mapping),eq(item),any(),argThat(s -> s.stage()==WorkItemStage.INVOICE_DRAFTED),eq(4),any(),any(),any());
  }
  @Test void gl21_identicalObservationDoesNotBumpVersion() {
    setup(WorkItemStage.INVOICED);
    var state=new StageMachine.State(WorkItemStage.INVOICED,null,null,null,true,true,Set.of());
    when(invoices.lock(mapping,item)).thenReturn(Optional.of(new InvoiceMirrorRepository.Stored(4,invoice,state,"INV-1","AUTHORISED",true,
        new BigDecimal("100.0000"),new BigDecimal("40.0000"),LocalDate.parse("2026-12-01"))));
    service.apply(mapping,item,invoice(InvoiceStatus.AUTHORISED,"40"),false);
    verify(invoices,never()).update(any(),any(),any(),any(),anyInt(),any(),any(),any());
  }
  @Test void gl06_changedTenantOrInvoiceIsRejected() {
    setup(WorkItemStage.INVOICED);
    when(quotes.lockMapping(mapping)).thenReturn(false);
    assertThatThrownBy(() -> service.apply(mapping,item,invoice(InvoiceStatus.PAID,"0"),false)).isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    verify(invoices,never()).update(any(),any(),any(),any(),anyInt(),any(),any(),any());
  }
  @Test void gl06_differentLinkedInvoiceCannotBeOverwritten() {
    setup(WorkItemStage.INVOICED);
    var state=new StageMachine.State(WorkItemStage.INVOICED,null,null,null,true,true,Set.of());
    when(invoices.lock(mapping,item)).thenReturn(Optional.of(new InvoiceMirrorRepository.Stored(4,UUID.randomUUID(),state)));
    assertThatThrownBy(() -> service.apply(mapping,item,invoice(InvoiceStatus.PAID,"0"),false))
        .isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    verify(invoices,never()).update(any(),any(),any(),any(),anyInt(),any(),any(),any());
  }
  @Test void gl19_manualInvoiceLinksOnlyWhileReferenceAndMappingMatch() {
    var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
    when(quotes.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(false,30,2,settings)));
    when(quotes.lockMapping(mapping)).thenReturn(true);
    var state=new StageMachine.State(WorkItemStage.TO_INVOICE,null,null,null,true,false,Set.of("QUOTE_INVOICED_IN_XERO"));
    when(invoices.lock(mapping,item)).thenReturn(Optional.of(new InvoiceMirrorRepository.Stored(4,null,state)));
    when(invoices.referenceMatches(mapping,candidate)).thenReturn(true);
    when(invoices.link(eq(mapping),eq(candidate),any(),any(),eq(4),any(),any())).thenReturn(1);
    service.link(mapping,candidate,invoice(InvoiceStatus.DRAFT,"40"),false);
    verify(invoices).link(eq(mapping),eq(candidate),any(),argThat(s -> s.invoiceLinked()
        && s.stage()==WorkItemStage.INVOICE_DRAFTED && !s.attention().contains("QUOTE_INVOICED_IN_XERO")),eq(4),any(),any());
  }
}
