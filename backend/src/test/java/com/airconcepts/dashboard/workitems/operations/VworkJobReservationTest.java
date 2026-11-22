package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository;
import java.util.*;
import org.junit.jupiter.api.Test;

class VworkJobReservationTest {
  private final UUID entity=UUID.randomUUID(), item=UUID.randomUUID(), actor=UUID.randomUUID(), operation=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(actor.toString(), Role.DIRECTOR);
  private final VworkMirrorRepository mirror=mock(VworkMirrorRepository.class);
  private final OperationReservationService reservations=mock(OperationReservationService.class);
  private final VworkJobReservation service=new VworkJobReservation(mirror, reservations);
  private final StageMachine.State state=new StageMachine.State(WorkItemStage.TO_SCHEDULE, actor, null, null, false, false, Set.of());
  private final StageMachine.Settings settings=new StageMachine.Settings(null, false, actor, true);

  @Test void gl13_reservesOnlyAfterLockingEntityConnectionAndTemplate() {
    when(mirror.lock(entity,item,"revision")).thenReturn(Optional.of(new VworkMirrorRepository.Item(
        3,"J-1",null,null,null,null,null,state,settings,2)));
    when(mirror.templateBelongsTo(entity,"template-a")).thenReturn(true);
    var reservation=new OperationReservationService.Reservation(operation,1);
    when(reservations.reserve(user,entity,item,3,ExternalOperationRepository.Kind.VWORK_CREATE_JOB,"J-1","a".repeat(64))).thenReturn(reservation);
    assertThat(service.reserve(user,entity,item,3,"revision","template-a","J-1","a".repeat(64))).isEqualTo(reservation);
    var order=inOrder(mirror,reservations);
    order.verify(mirror).lock(entity,item,"revision");
    order.verify(mirror).templateBelongsTo(entity,"template-a");
    order.verify(reservations).reserve(user,entity,item,3,ExternalOperationRepository.Kind.VWORK_CREATE_JOB,"J-1","a".repeat(64));
  }
  @Test void gl06_otherEntityTemplateNeverReservesAnOperation() {
    when(mirror.lock(entity,item,"revision")).thenReturn(Optional.of(new VworkMirrorRepository.Item(
        3,"J-1",null,null,null,null,null,state,settings,2)));
    assertThatThrownBy(() -> service.reserve(user,entity,item,3,"revision","other-template","J-1","a".repeat(64)))
        .isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    verifyNoInteractions(reservations);
  }
}
