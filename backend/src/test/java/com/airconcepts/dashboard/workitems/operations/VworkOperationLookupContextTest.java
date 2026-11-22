package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class VworkOperationLookupContextTest {
  private final UUID entity=UUID.randomUUID(), item=UUID.randomUUID(), actor=UUID.randomUUID(), operation=UUID.randomUUID();
  private final WorkItemRepository items=mock(WorkItemRepository.class);
  private final ExternalOperationRepository operations=mock(ExternalOperationRepository.class);
  private final VworkOperationLookupContext service=new VworkOperationLookupContext(items,operations);
  private final CurrentUser user=new CurrentUser(actor.toString(),Role.STANDARD_USER);

  private void setup(UUID manager) {
    when(items.lockActor(actor)).thenReturn(Optional.of(Role.STANDARD_USER));
    when(items.lockItem(entity,item)).thenReturn(Optional.of(new WorkItemRepository.Item(item,entity,
        WorkItemStage.TO_SCHEDULE,actor,null,null,1)));
    when(operations.context(entity,item)).thenReturn(new ExternalOperationRepository.Context(null,null,manager));
  }
  @Test void gl22_designatedManagerCanLookUpUnknownOperation() {
    setup(actor);
    when(operations.lockOperation(entity,item,operation)).thenReturn(Optional.of(new ExternalOperationRepository.Locked(
        ExternalOperationRepository.Kind.VWORK_CREATE_JOB,OperationState.Status.UNKNOWN,"J-1")));
    assertThat(service.load(user,entity,item,operation).reference()).isEqualTo("J-1");
  }
  @Test void gl06_otherStaffCannotInspectOperation() {
    setup(UUID.randomUUID());
    assertThatThrownBy(() -> service.load(user,entity,item,operation)).isInstanceOf(AccessDeniedException.class);
    verify(operations,never()).lockOperation(entity,item,operation);
  }
  @Test void gl06_inactiveActorCannotInspectItem() {
    when(items.lockActor(actor)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.load(user,entity,item,operation)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(operations);
  }
}
