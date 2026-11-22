package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository;
import java.util.*;
import org.junit.jupiter.api.Test;

class VworkOperationCompletionTest {
  private final UUID entity = UUID.randomUUID(), item = UUID.randomUUID(), operation = UUID.randomUUID();
  private final VworkMirrorRepository mirror = mock(VworkMirrorRepository.class);
  private final ExternalOperationRepository operations = mock(ExternalOperationRepository.class);
  private final VworkOperationCompletion service = new VworkOperationCompletion(mirror, operations);
  private final VworkClient.Job job = new VworkClient.Job("mock-1", "J-1", "template-a", null,
      VworkClient.Status.UNASSIGNED, null, null, null);
  private final StageMachine.State state = new StageMachine.State(WorkItemStage.TO_SCHEDULE, null, null, null, false, false, Set.of());
  private final StageMachine.Settings settings = new StageMachine.Settings(null, false, null, false);

  private void setup(OperationState.Status status) {
    when(mirror.lock(entity, item, "revision")).thenReturn(Optional.of(new VworkMirrorRepository.Item(
        4, "J-1", null, null, null, null, null, state, settings, 2)));
    when(mirror.templateBelongsTo(entity, "template-a")).thenReturn(true);
    when(operations.lockOperation(entity, item, operation)).thenReturn(Optional.of(
        new ExternalOperationRepository.Locked(ExternalOperationRepository.Kind.VWORK_CREATE_JOB, status, "J-1")));
    when(operations.linkVwork(entity, item, 4, "J-1", job)).thenReturn(1);
    when(operations.complete(eq(entity), eq(item), eq(operation), any(), eq(job.id()))).thenReturn(1);
  }

  @Test void gl13_confirmedCreationLinksJobAndOperationTogether() {
    setup(OperationState.Status.PENDING);
    service.complete(entity, item, operation, "revision", job, OperationState.Result.CREATED);
    var ordered = inOrder(mirror, operations);
    ordered.verify(mirror).lock(entity, item, "revision");
    ordered.verify(operations).lockOperation(entity, item, operation);
    ordered.verify(mirror).templateBelongsTo(entity, "template-a");
    ordered.verify(mirror).jobLinkedElsewhere(item, job.id());
    ordered.verify(operations).linkVwork(entity, item, 4, "J-1", job);
    ordered.verify(operations).complete(entity, item, operation, OperationState.Result.CREATED, job.id());
    ordered.verify(operations).auditCompleted(entity, item, operation, OperationState.Result.CREATED);
  }

  @Test void gl22_unknownCanOnlyBeLinkedAfterAConfirmedLookup() {
    setup(OperationState.Status.UNKNOWN);
    assertThatThrownBy(() -> service.complete(entity, item, operation, "revision", job, OperationState.Result.CREATED))
        .isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    service.complete(entity, item, operation, "revision", job, OperationState.Result.LINKED_EXISTING);
    verify(operations).complete(entity, item, operation, OperationState.Result.LINKED_EXISTING, job.id());
    verify(operations).clearResolvedUnknown(entity, item);
  }

  @Test void gl06_otherEntityTemplateOrDifferentReferenceCannotLink() {
    setup(OperationState.Status.PENDING);
    when(mirror.templateBelongsTo(entity, "template-a")).thenReturn(false);
    assertThatThrownBy(() -> service.complete(entity, item, operation, "revision", job, OperationState.Result.CREATED))
        .isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    verify(operations, never()).linkVwork(any(), any(), anyInt(), any(), any());
  }
}
