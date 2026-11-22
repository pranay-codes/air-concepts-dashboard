package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.invoices.InvoiceStatus;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.*;
import com.airconcepts.dashboard.workitems.sync.*;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoiceOperationCompletionTest {
  private final UUID entity=UUID.randomUUID(),item=UUID.randomUUID(),tenant=UUID.randomUUID(),grant=UUID.randomUUID(),quote=UUID.randomUUID(),operation=UUID.randomUUID(),invoice=UUID.randomUUID();
  private final InvoiceDraftContext.Context context=new InvoiceDraftContext.Context(quote,"Q-1","J-1",4,tenant,grant,"Xero");
  private final QuoteMirrorService.Mapping mapping=new QuoteMirrorService.Mapping(entity,tenant,grant);
  private final QuoteMirrorRepository mappings=mock(QuoteMirrorRepository.class);
  private final InvoiceMirrorRepository invoices=mock(InvoiceMirrorRepository.class);
  private final ExternalOperationRepository operations=mock(ExternalOperationRepository.class);
  private final InvoiceOperationCompletion service=new InvoiceOperationCompletion(mappings,invoices,operations,Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"),ZoneOffset.UTC));
  private final XeroInvoiceClient.WorkflowInvoice draft=new XeroInvoiceClient.WorkflowInvoice(invoice,"INV-1",InvoiceStatus.DRAFT,BigDecimal.TEN,BigDecimal.TEN,null,false,"J-1 / Q-1");
  private void setup(OperationState.Status status) {
    var state=new StageMachine.State(WorkItemStage.TO_INVOICE,null,null,null,true,false,Set.of());
    when(mappings.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(false,30,2,new StageMachine.Settings(null,false,null,false))));
    when(mappings.lockMapping(mapping)).thenReturn(true);
    when(invoices.lock(mapping,item)).thenReturn(Optional.of(new InvoiceMirrorRepository.Stored(5,null,state)));
    when(invoices.referenceMatches(eq(mapping),any())).thenReturn(true);
    when(operations.lockOperation(entity,item,operation)).thenReturn(Optional.of(new ExternalOperationRepository.Locked(ExternalOperationRepository.Kind.XERO_CREATE_INVOICE,status,"J-1")));
    when(invoices.link(eq(mapping),any(),eq(draft),any(),eq(5),any(),any())).thenReturn(1);
    when(operations.complete(entity,item,operation,OperationState.Result.CREATED,invoice.toString())).thenReturn(1);
  }
  @Test void gl17_confirmedDraftLinksAndCompletesTheReservedOperation() {
    setup(OperationState.Status.PENDING);
    service.complete(entity,item,context,operation,draft,OperationState.Result.CREATED);
    var order=inOrder(invoices,operations);
    order.verify(operations).lockOperation(entity,item,operation);
    order.verify(invoices).link(eq(mapping),any(),eq(draft),argThat(state -> state.stage()==WorkItemStage.INVOICE_DRAFTED),eq(5),any(),any());
    order.verify(operations).complete(entity,item,operation,OperationState.Result.CREATED,invoice.toString());
  }
  @Test void gl22_unknownOutcomeCannotBeClaimedAsCreated() {
    setup(OperationState.Status.UNKNOWN);
    assertThatThrownBy(() -> service.complete(entity,item,context,operation,draft,OperationState.Result.CREATED)).isInstanceOf(ApiFailure.class);
    verify(invoices,never()).link(any(),any(),any(),any(),anyInt(),any(),any());
  }
}
