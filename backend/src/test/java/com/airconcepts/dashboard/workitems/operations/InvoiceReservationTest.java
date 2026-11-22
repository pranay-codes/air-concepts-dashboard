package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.sync.QuoteMirrorRepository;
import com.airconcepts.dashboard.workitems.sync.QuoteMirrorService;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoiceReservationTest {
  private final UUID entity=UUID.randomUUID(),item=UUID.randomUUID(),tenant=UUID.randomUUID(),grant=UUID.randomUUID(),quote=UUID.randomUUID(),operation=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.FINANCIAL_ADMIN);
  private final InvoiceDraftContext contexts=mock(InvoiceDraftContext.class);
  private final QuoteMirrorRepository mappings=mock(QuoteMirrorRepository.class);
  private final OperationReservationService reservations=mock(OperationReservationService.class);
  private final InvoiceReservation service=new InvoiceReservation(contexts,mappings,reservations);
  private final InvoiceDraftContext.Context context=new InvoiceDraftContext.Context(quote,"Q-1","J-1",4,tenant,grant,"Organisation");
  private void setup() {
    var settings=new StageMachine.Settings(null,false,null,false);
    when(mappings.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(false,30,2,settings)));
    when(mappings.lockMapping(new QuoteMirrorService.Mapping(entity,tenant,grant))).thenReturn(true);
    when(contexts.load(user,entity,item)).thenReturn(context);
    when(reservations.reserve(user,entity,item,4,ExternalOperationRepository.Kind.XERO_CREATE_INVOICE,"J-1","a".repeat(64)))
        .thenReturn(new OperationReservationService.Reservation(operation,1));
  }
  @Test void gl17_reservesOnlyAfterMappingAndItemAreRechecked() {
    setup();
    assertThat(service.reserve(user,entity,item,context,"a".repeat(64)).id()).isEqualTo(operation);
    var order=inOrder(mappings,contexts,reservations);
    order.verify(mappings).lockConfig(entity);
    order.verify(mappings).lockMapping(new QuoteMirrorService.Mapping(entity,tenant,grant));
    order.verify(contexts).load(user,entity,item);
    order.verify(reservations).reserve(user,entity,item,4,ExternalOperationRepository.Kind.XERO_CREATE_INVOICE,"J-1","a".repeat(64));
  }
  @Test void gl06_changedMappingOrItemNeverReserves() {
    setup();
    when(mappings.lockMapping(any())).thenReturn(false);
    assertThatThrownBy(() -> service.reserve(user,entity,item,context,"a".repeat(64))).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(reservations);
  }
}
