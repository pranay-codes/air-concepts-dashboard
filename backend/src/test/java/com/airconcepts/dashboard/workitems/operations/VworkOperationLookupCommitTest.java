package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository;
import java.util.*;
import org.junit.jupiter.api.Test;

class VworkOperationLookupCommitTest {
  private final UUID entity=UUID.randomUUID(), item=UUID.randomUUID(), operation=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
  private final VworkMirrorRepository mirror=mock(VworkMirrorRepository.class);
  private final VworkOperationLookupContext contexts=mock(VworkOperationLookupContext.class);
  private final VworkOperationCompletion completion=mock(VworkOperationCompletion.class);
  private final ExternalOperationRepository operations=mock(ExternalOperationRepository.class);
  private final VworkOperationLookupCommit service=new VworkOperationLookupCommit(mirror,contexts,completion,operations);

  private void item(WorkItemStage stage, String job) {
    var state=new StageMachine.State(stage,null,null,null,job!=null,false,Set.of("OPERATION_UNKNOWN"));
    var settings=new StageMachine.Settings(null,false,null,false);
    when(mirror.lock(entity,item,"revision")).thenReturn(Optional.of(new VworkMirrorRepository.Item(
        4,"J-1",job,null,null,null,null,state,settings,2)));
  }
  @Test void gl22_notFoundRecordsOutcomeAndAttention() {
    item(WorkItemStage.TO_SCHEDULE,null);
    when(operations.markNotFound(entity,item,operation)).thenReturn(1);
    when(operations.markLookupFailedAttention(entity,item,4)).thenReturn(1);
    service.notFound(user,entity,item,operation,"revision");
    var order=inOrder(contexts,operations);
    order.verify(contexts).load(user,entity,item,operation);
    order.verify(operations).markNotFound(entity,item,operation);
    order.verify(operations).markLookupFailedAttention(entity,item,4);
    order.verify(operations).auditOutcome(entity,item,operation,OperationState.Status.FAILED,"NOT_FOUND");
  }
  @Test void gl22_changedOrLinkedItemCannotBecomeNotFound() {
    item(WorkItemStage.SCHEDULED,"job-1");
    assertThatThrownBy(() -> service.notFound(user,entity,item,operation,"revision")).isInstanceOf(ApiFailure.class);
    verify(operations,never()).markNotFound(any(),any(),any());
  }
  @Test void gl06_actorIsRecheckedBeforeRecordingOutcome() {
    item(WorkItemStage.TO_SCHEDULE,null);
    when(contexts.load(user,entity,item,operation)).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
    assertThatThrownBy(() -> service.notFound(user,entity,item,operation,"revision"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(operations);
  }
}
