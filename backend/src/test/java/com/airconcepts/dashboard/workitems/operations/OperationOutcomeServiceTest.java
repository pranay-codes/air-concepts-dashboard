package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationOutcomeServiceTest {
  private final WorkItemRepository items = mock(WorkItemRepository.class);
  private final ExternalOperationRepository operations = mock(ExternalOperationRepository.class);
  private final OperationOutcomeService service = new OperationOutcomeService(items, operations);
  private final UUID entity = UUID.randomUUID(), item = UUID.randomUUID(), operation = UUID.randomUUID();

  @BeforeEach void setup() {
    when(items.lockItemForOutcome(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(item, entity,
        WorkItemStage.TO_SCHEDULE, null, null, null, 4)));
    when(operations.lockOperation(entity, item, operation)).thenReturn(Optional.of(
        new ExternalOperationRepository.Locked(ExternalOperationRepository.Kind.VWORK_CREATE_JOB, OperationState.Status.PENDING)));
    when(operations.recordOutcome(eq(entity), eq(item), eq(operation), any(), any(), any())).thenReturn(1);
    when(operations.addAttention(entity, item, "OPERATION_UNKNOWN", 4)).thenReturn(1);
    when(operations.addAttention(entity, item, "OPERATION_FAILED", 4)).thenReturn(1);
  }

  @Test void gl22_uncertainWriteMarksUnknownAndAddsAttentionAtomically() {
    service.markUnknown(entity, item, operation);
    var order = inOrder(items, operations);
    order.verify(items).lockItemForOutcome(entity, item);
    order.verify(operations).lockOperation(entity, item, operation);
    order.verify(operations).recordOutcome(entity, item, operation, OperationState.Status.UNKNOWN, "EXTERNAL_OUTCOME_UNKNOWN", null);
    order.verify(operations).addAttention(entity, item, "OPERATION_UNKNOWN", 4);
    order.verify(operations).auditOutcome(entity, item, operation, OperationState.Status.UNKNOWN, "EXTERNAL_OUTCOME_UNKNOWN");
  }

  @Test void gl22_definiteFailureUsesOnlyAllowlistedCodes() {
    service.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.PROVIDER_REJECTED, 400);
    verify(operations).recordOutcome(entity, item, operation, OperationState.Status.FAILED, "PROVIDER_REJECTED", 400);
    verify(operations).addAttention(entity, item, "OPERATION_FAILED", 4);
    verify(operations).auditOutcome(entity, item, operation, OperationState.Status.FAILED, "PROVIDER_REJECTED");
  }

  @Test void gl22_unknownOrTerminalOperationCannotBeMarkedFailedOrReplayed() {
    when(operations.lockOperation(entity, item, operation)).thenReturn(Optional.of(
        new ExternalOperationRepository.Locked(ExternalOperationRepository.Kind.VWORK_CREATE_JOB, OperationState.Status.UNKNOWN)));
    assertThatThrownBy(() -> service.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.PROVIDER_REJECTED, 400))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("OPERATION_STATE_CHANGED"));
    verify(operations, never()).recordOutcome(any(), any(), any(), any(), any(), any());
  }

  @Test void gl06_missingItemOrCrossEntityOperationCannotMutate() {
    when(items.lockItemForOutcome(entity, item)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.markUnknown(entity, item, operation)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(operations);
    when(items.lockItemForOutcome(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(item, entity, WorkItemStage.TO_SCHEDULE, null, null, null, 4)));
    when(operations.lockOperation(entity, item, operation)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.markUnknown(entity, item, operation)).isInstanceOf(ApiFailure.class);
    verify(operations, never()).recordOutcome(any(), any(), any(), any(), any(), any());
  }

  @Test void gl22_staleVersionOrInvalidStatusDoesNotCreateAudit() {
    when(operations.addAttention(entity, item, "OPERATION_UNKNOWN", 4)).thenReturn(0);
    assertThatThrownBy(() -> service.markUnknown(entity, item, operation)).isInstanceOf(ApiFailure.class);
    verify(operations, never()).auditOutcome(any(), any(), any(), any(), any());
    assertThatThrownBy(() -> service.markFailed(entity, item, operation, OperationOutcomeService.FailureCode.PROVIDER_REJECTED, 500))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
