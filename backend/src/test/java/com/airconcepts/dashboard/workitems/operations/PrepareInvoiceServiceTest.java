package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class PrepareInvoiceServiceTest {
  private final UUID entity=UUID.randomUUID(),item=UUID.randomUUID(),tenant=UUID.randomUUID(),grant=UUID.randomUUID(),quote=UUID.randomUUID(),operation=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
  private final InvoiceDraftService drafts=mock(InvoiceDraftService.class);
  private final InvoiceReservation reservations=mock(InvoiceReservation.class);
  private final InvoiceOperationCompletion completion=mock(InvoiceOperationCompletion.class);
  private final OperationOutcomeService outcomes=mock(OperationOutcomeService.class);
  private final XeroConnectionService connections=mock(XeroConnectionService.class);
  private final InvoiceGateway gateway=new MockInvoiceGateway();
  private final TenantAccess access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read","accounting.invoices"));
  private final InvoiceDraftContext.Context context=new InvoiceDraftContext.Context(quote,"Q-1","J-1",4,tenant,grant,"Organisation");
  private final DraftInvoice draft=new DraftInvoice(UUID.randomUUID(),"Customer","Q-1","J-1 / Q-1",LocalDate.of(2026,11,21),"NZD","Exclusive",null,new BigDecimal("115"),
      List.of(new DraftInvoice.Line("Work",BigDecimal.ONE,new BigDecimal("100"),null,"200","OUTPUT2",null,List.of())));
  private final PrepareInvoiceService service=new PrepareInvoiceService(drafts,reservations,completion,outcomes,connections,gateway);
  private void setup() {
    when(drafts.prepare(user,entity,item)).thenReturn(new InvoiceDraftService.Prepared(context,access,draft));
    when(connections.authorisedAccess(user,entity)).thenReturn(access);
    when(reservations.reserve(user,entity,item,context,draft.fingerprint())).thenReturn(new OperationReservationService.Reservation(operation,1));
  }
  @Test void gl17_explicitSubmissionReservesCreatesAndConfirmsOneMockInvoice() {
    setup();
    var result=service.submit(user,entity,item,4,draft.fingerprint());
    assertThat(result.operationId()).isEqualTo(operation);
    assertThat(result.outcome()).isEqualTo(OperationState.Result.CREATED);
    var order=inOrder(reservations,completion);
    order.verify(reservations).reserve(user,entity,item,context,draft.fingerprint());
    order.verify(completion).complete(eq(entity),eq(item),eq(context),eq(operation),any(),eq(OperationState.Result.CREATED));
  }
  @Test void gl17_changedReviewNeverReservesOrWrites() {
    setup();
    assertThatThrownBy(() -> service.submit(user,entity,item,4,"a".repeat(64))).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(reservations,completion,outcomes);
  }
  @Test void gl19_existingTestInvoiceIsLinkedWithoutCreatingAnother() {
    setup();
    var existing=gateway.create(access,draft,UUID.randomUUID());
    var result=service.submit(user,entity,item,4,draft.fingerprint());
    assertThat(result.outcome()).isEqualTo(OperationState.Result.LINKED_EXISTING);
    verify(completion).complete(entity,item,context,operation,existing,OperationState.Result.LINKED_EXISTING);
    assertThat(gateway.find(access,"J-1","Q-1").matches()).containsExactly(existing);
  }
}
