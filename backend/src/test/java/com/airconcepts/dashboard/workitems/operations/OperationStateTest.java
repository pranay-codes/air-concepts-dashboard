package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OperationStateTest {
  @Test void gl22_unknownCannotBeRetriedAndUncertainLookupCannotConcludeAbsence() {
    assertThatThrownBy(() -> OperationState.retryAttempt(OperationState.Status.UNKNOWN, 1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> OperationState.notFound(OperationState.Status.UNKNOWN, false)).isInstanceOf(IllegalStateException.class);
    assertThat(OperationState.notFound(OperationState.Status.UNKNOWN, true)).isEqualTo(OperationState.Result.NOT_FOUND);
  }

  @Test void gl22_onlyDefiniteFailureAllowsNewAttemptWithinLimit() {
    assertThat(OperationState.retryAttempt(OperationState.Status.FAILED, 1)).isEqualTo(2);
    for (var status : new OperationState.Status[]{OperationState.Status.PENDING, OperationState.Status.UNKNOWN, OperationState.Status.SUCCEEDED})
      assertThatThrownBy(() -> OperationState.retryAttempt(status, 1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> OperationState.retryAttempt(OperationState.Status.FAILED, 20)).isInstanceOf(IllegalStateException.class);
  }

  @Test void gl22_writeOutcomesAndReconciliationNeverTurnUncertaintyIntoSuccess() {
    assertThat(OperationState.unknown(OperationState.Status.PENDING)).isEqualTo(OperationState.Status.UNKNOWN);
    assertThat(OperationState.failed(OperationState.Status.PENDING)).isEqualTo(OperationState.Status.FAILED);
    assertThat(OperationState.linked(OperationState.Status.UNKNOWN, "job-1")).isEqualTo(OperationState.Result.LINKED_EXISTING);
    assertThat(OperationState.created(OperationState.Status.PENDING, "job-1")).isEqualTo(OperationState.Result.CREATED);
    assertThatThrownBy(() -> OperationState.created(OperationState.Status.UNKNOWN, "job-1")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> OperationState.linked(OperationState.Status.UNKNOWN, " ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> OperationState.failed(OperationState.Status.UNKNOWN)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> OperationState.unknown(OperationState.Status.SUCCEEDED)).isInstanceOf(IllegalStateException.class);
  }
}
