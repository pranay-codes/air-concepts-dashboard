package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.vwork.VworkTemplatesRepository;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class VworkJobDraftContextTest {
  private final UUID entity=UUID.randomUUID(), item=UUID.randomUUID(), actor=UUID.randomUUID();
  private final WorkItemRepository items=mock(WorkItemRepository.class);
  private final ExternalOperationRepository operations=mock(ExternalOperationRepository.class);
  private final VworkTemplatesRepository templates=mock(VworkTemplatesRepository.class);
  private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  private final VworkJobDraftContext service=new VworkJobDraftContext(items,operations,templates,jdbc);

  @Test void gl06_nonManagerCannotReadDraftOrQuoteIdentity() {
    var user=new CurrentUser(actor.toString(),Role.STANDARD_USER);
    when(items.lockActor(actor)).thenReturn(Optional.of(Role.STANDARD_USER));
    when(items.lockItem(entity,item)).thenReturn(Optional.of(new WorkItemRepository.Item(item,entity,
        WorkItemStage.TO_SCHEDULE,actor,null,null,0)));
    when(operations.context(entity,item)).thenReturn(new ExternalOperationRepository.Context(null,null,UUID.randomUUID()));
    assertThatThrownBy(() -> service.load(user,entity,item)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(jdbc);
    verify(templates,never()).read(any());
  }
  @Test void gl06_inactiveStaffCannotReadAnyItem() {
    var user=new CurrentUser(actor.toString(),Role.DIRECTOR);
    when(items.lockActor(actor)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.load(user,entity,item)).isInstanceOf(AccessDeniedException.class);
    verify(items,never()).lockItem(any(),any());
    verifyNoInteractions(operations,templates,jdbc);
  }
}
