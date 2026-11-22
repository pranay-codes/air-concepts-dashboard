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

class CreateJobServiceTest {
  private final UUID entity=UUID.randomUUID(), item=UUID.randomUUID(), actor=UUID.randomUUID(), operation=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(actor.toString(),Role.DIRECTOR);
  private final VworkJobReservation reservation=mock(VworkJobReservation.class);
  private final VworkOperationCompletion completion=mock(VworkOperationCompletion.class);
  private final OperationOutcomeService outcomes=mock(OperationOutcomeService.class);
  private final VworkConnectionService connections=mock(VworkConnectionService.class);
  private final MockVworkClient client=spy(new MockVworkClient());
  private final VworkCredential credential=new VworkCredential("synthetic");
  private final VworkConnectionService.Access access=new VworkConnectionService.Access("revision",credential,client,true);
  private final VworkClient.NewJob request=new VworkClient.NewJob("J-1","template-a","Synthetic work",List.of(new VworkClient.ScopeLine("Install", "1")),
      "Synthetic customer","Alex","555123","Example Street","");
  private final CreateJobService service=new CreateJobService(reservation,completion,outcomes,connections);

  private void setup() {
    when(connections.systemAccess()).thenReturn(access);
    when(reservation.reserve(eq(user),eq(entity),eq(item),eq(4),eq("revision"),eq("template-a"),eq("J-1"),anyString()))
        .thenReturn(new OperationReservationService.Reservation(operation,1));
  }
  @Test void gl13_reservesThenCreatesAndConfirmsOneMockJob() {
    setup();
    var result=service.submit(user,entity,item,4,request);
    assertThat(result.operationId()).isEqualTo(operation);
    assertThat(result.outcome()).isEqualTo(OperationState.Result.CREATED);
    assertThat(client.jobs(credential,1).totalEntries()).isEqualTo(1);
    var order=inOrder(reservation,client,completion);
    order.verify(reservation).reserve(eq(user),eq(entity),eq(item),eq(4),eq("revision"),eq("template-a"),eq("J-1"),anyString());
    order.verify(client).findByReference(credential,"J-1");
    order.verify(client).create(credential,request);
    order.verify(completion).complete(eq(entity),eq(item),eq(operation),eq("revision"),any(),eq(OperationState.Result.CREATED));
  }
  @Test void gl14_existingJobIsLinkedWithoutAnotherCreate() {
    client.create(credential,request);
    clearInvocations(client);
    setup();
    assertThat(service.submit(user,entity,item,4,request).outcome()).isEqualTo(OperationState.Result.LINKED_EXISTING);
    verify(client,never()).create(any(),any());
    verify(completion).complete(eq(entity),eq(item),eq(operation),eq("revision"),any(),eq(OperationState.Result.LINKED_EXISTING));
  }
  @Test void gl22_explicitRetryReservesThenChecksForDuplicatesBeforeCreating() {
    var previous=UUID.randomUUID();
    when(connections.systemAccess()).thenReturn(access);
    when(reservation.retry(eq(user),eq(entity),eq(item),eq(4),eq("revision"),eq("template-a"),eq("J-1"),anyString(),eq(previous)))
        .thenReturn(new OperationReservationService.Reservation(operation,2));
    assertThat(service.retry(user,entity,item,4,request,previous).outcome()).isEqualTo(OperationState.Result.CREATED);
    var order=inOrder(reservation,client);
    order.verify(reservation).retry(eq(user),eq(entity),eq(item),eq(4),eq("revision"),eq("template-a"),eq("J-1"),anyString(),eq(previous));
    order.verify(client).findByReference(credential,"J-1");
    order.verify(client).create(credential,request);
  }
  @Test void gl22_unconfirmedWriteIsUnknownAndNeverAutomaticallyRetried() {
    setup();
    doThrow(new VworkFailure("VWORK_PROVIDER_FAILED","private detail",0,null)).when(client).create(any(),any());
    assertThatThrownBy(() -> service.submit(user,entity,item,4,request)).isInstanceOf(ApiFailure.class)
        .hasMessageNotContaining("private detail");
    verify(outcomes).markUnknown(entity,item,operation);
    verify(client,times(1)).create(any(),any());
    verifyNoInteractions(completion);
  }
  @Test void gl14_searchFailureStopsBeforeCreateAndRecordsNotApplied() {
    setup();
    doThrow(new VworkFailure("VWORK_PROVIDER_FAILED","private detail",503,null)).when(client).findByReference(any(),any());
    assertThatThrownBy(() -> service.submit(user,entity,item,4,request)).isInstanceOf(ApiFailure.class);
    verify(outcomes).markFailed(entity,item,operation,OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE,null);
    verify(client,never()).create(any(),any());
  }
  @Test void gl26_invalidOrLiveRequestsCannotCreate() {
    assertThatThrownBy(() -> service.submit(user,entity,item,4,new VworkClient.NewJob("", "template-a", "Work", List.of())))
        .isInstanceOf(ApiFailure.class);
    verifyNoInteractions(reservation,completion,outcomes,connections);
  }
  @Test void gl26_liveCreationIsUnavailableUntilTemplateBehaviourIsVerified() {
    var live=mock(VworkClient.class);
    when(connections.systemAccess()).thenReturn(new VworkConnectionService.Access("revision",credential,live,false));
    assertThatThrownBy(() -> service.submit(user,entity,item,4,request)).isInstanceOfSatisfying(ApiFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("VWORK_CAPABILITY_UNVERIFIED"));
    verifyNoInteractions(reservation,completion,outcomes,live);
  }
  @Test void gl13_mockCreationRunsThroughRealReservationAndConfirmationServices() {
    var mirror=mock(com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository.class);
    var items=mock(com.airconcepts.dashboard.workitems.WorkItemRepository.class);
    var ops=mock(ExternalOperationRepository.class);
    var state=new com.airconcepts.dashboard.workitems.StageMachine.State(
        com.airconcepts.dashboard.workitems.WorkItemStage.TO_SCHEDULE,actor,null,null,false,false,Set.of());
    var settings=new com.airconcepts.dashboard.workitems.StageMachine.Settings(null,false,actor,true);
    when(mirror.lock(entity,item,"revision")).thenReturn(
        Optional.of(new com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository.Item(4,"J-1",null,null,null,null,null,state,settings,2)),
        Optional.of(new com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository.Item(5,"J-1",null,null,null,null,null,state,settings,2)));
    when(mirror.templateBelongsTo(entity,"template-a")).thenReturn(true);
    when(items.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(items.lockItem(entity,item)).thenReturn(Optional.of(new com.airconcepts.dashboard.workitems.WorkItemRepository.Item(
        item,entity,com.airconcepts.dashboard.workitems.WorkItemStage.TO_SCHEDULE,actor,null,null,4)));
    when(ops.context(entity,item)).thenReturn(new ExternalOperationRepository.Context(null,null,actor));
    when(ops.latest(entity,item,ExternalOperationRepository.Kind.VWORK_CREATE_JOB)).thenReturn(Optional.empty());
    when(ops.bumpVersion(entity,item,4)).thenReturn(1);
    when(ops.lockOperation(eq(entity),eq(item),any())).thenReturn(Optional.of(new ExternalOperationRepository.Locked(
        ExternalOperationRepository.Kind.VWORK_CREATE_JOB,OperationState.Status.PENDING,"J-1")));
    when(ops.linkVwork(eq(entity),eq(item),eq(5),eq("J-1"),any())).thenReturn(1);
    when(ops.complete(eq(entity),eq(item),any(),eq(OperationState.Result.CREATED),anyString())).thenReturn(1);
    when(connections.systemAccess()).thenReturn(access);
    var realReservation=new VworkJobReservation(mirror,new OperationReservationService(items,ops));
    var realCompletion=new VworkOperationCompletion(mirror,ops);
    var realService=new CreateJobService(realReservation,realCompletion,new OperationOutcomeService(items,ops),connections);
    var result=realService.submit(user,entity,item,4,request);
    assertThat(result.outcome()).isEqualTo(OperationState.Result.CREATED);
    verify(ops).insert(eq(result.operationId()),eq(entity),eq(item),eq(ExternalOperationRepository.Kind.VWORK_CREATE_JOB),eq(1),eq("J-1"),anyString(),eq(actor));
    verify(ops).complete(entity,item,result.operationId(),OperationState.Result.CREATED,"mock-000001");
  }
}
