package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.vwork.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.*;
import org.junit.jupiter.api.Test;

class VworkOperationReconcilerTest {
  private final UUID entity=UUID.randomUUID(), item=UUID.randomUUID(), operation=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
  private final VworkOperationLookupContext contexts=mock(VworkOperationLookupContext.class);
  private final VworkOperationLookupCommit commit=mock(VworkOperationLookupCommit.class);
  private final VworkConnectionService connections=mock(VworkConnectionService.class);
  private final MockVworkClient client=new MockVworkClient();
  private final VworkCredential credential=new VworkCredential("synthetic");
  private final VworkConnectionService.Access access=new VworkConnectionService.Access("revision",credential,client,true);
  private final VworkOperationReconciler service=new VworkOperationReconciler(contexts,commit,connections);

  private void setup() {
    when(contexts.load(user,entity,item,operation)).thenReturn(new VworkOperationLookupContext.Lookup("J-1"));
    when(connections.systemAccess()).thenReturn(access);
  }
  @Test void gl22_foundJobIsLinkedWithoutAnotherWrite() {
    setup();
    var job=client.create(credential,new VworkClient.NewJob("J-1","template-a","Synthetic",List.of(new VworkClient.ScopeLine("Work","1"))));
    assertThat(service.lookup(user,entity,item,operation)).isEqualTo(OperationState.Result.LINKED_EXISTING);
    verify(commit).link(user,entity,item,operation,"revision",job);
    assertThat(client.jobs(credential,1).totalEntries()).isEqualTo(1);
  }
  @Test void gl22_completeEmptyMockSearchMarksNotFound() {
    setup();
    assertThat(service.lookup(user,entity,item,operation)).isEqualTo(OperationState.Result.NOT_FOUND);
    verify(commit).notFound(user,entity,item,operation,"revision");
  }
  @Test void gl22_unverifiedSearchNeverClaimsNotFound() {
    setup();
    when(connections.systemAccess()).thenReturn(new VworkConnectionService.Access("revision",credential,client,false));
    assertThatThrownBy(() -> service.lookup(user,entity,item,operation)).isInstanceOfSatisfying(ApiFailure.class,
        error -> assertThat(error.code()).isEqualTo("VWORK_SEARCH_UNVERIFIED"));
    verifyNoInteractions(commit);
  }
  @Test void gl06_deniedActorNeverReadsProvider() {
    when(contexts.load(user,entity,item,operation)).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
    assertThatThrownBy(() -> service.lookup(user,entity,item,operation)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(connections,commit);
  }
}
