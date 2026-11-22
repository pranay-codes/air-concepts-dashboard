package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal, provider-free bookkeeping. Never exposes a route or retries an external write. */
@Service
public class OperationOutcomeService {
  public enum FailureCode { PROVIDER_REJECTED, PROVIDER_RATE_LIMITED, LOOKUP_UNAVAILABLE }
  private final WorkItemRepository items;
  private final ExternalOperationRepository operations;
  public OperationOutcomeService(WorkItemRepository items, ExternalOperationRepository operations) {
    this.items = items; this.operations = operations;
  }

  @Transactional
  public void markUnknown(UUID entity, UUID item, UUID operation) {
    record(entity, item, operation, OperationState.Status.UNKNOWN, "EXTERNAL_OUTCOME_UNKNOWN", null);
  }

  /** Call only when the adapter has proven no write was applied, not for timeouts or ambiguous responses. */
  @Transactional
  public void markFailed(UUID entity, UUID item, UUID operation, FailureCode code, Integer upstreamStatus) {
    if (code == null || (upstreamStatus != null && (upstreamStatus < 0 || upstreamStatus > 599))
        || (code == FailureCode.PROVIDER_REJECTED && (upstreamStatus == null || upstreamStatus < 400 || upstreamStatus >= 500 || upstreamStatus == 429))
        || (code == FailureCode.PROVIDER_RATE_LIMITED && (upstreamStatus == null || upstreamStatus != 429)))
      throw new IllegalArgumentException("Invalid confirmed-failure classification.");
    record(entity, item, operation, OperationState.Status.FAILED, code.name(), upstreamStatus);
  }

  private void record(UUID entity, UUID itemId, UUID operation, OperationState.Status next, String code, Integer upstream) {
    var item = items.lockItemForOutcome(entity, itemId).orElseThrow(() ->
        new ApiFailure(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "This work item is unavailable."));
    var previous = operations.lockOperation(entity, itemId, operation).orElseThrow(() ->
        new ApiFailure(HttpStatus.NOT_FOUND, "OPERATION_NOT_FOUND", "This operation is unavailable."));
    if (previous.status() != OperationState.Status.PENDING) throw changed();
    if (operations.recordOutcome(entity, itemId, operation, next, code, upstream) != 1) throw changed();
    String attention = next == OperationState.Status.UNKNOWN ? "OPERATION_UNKNOWN" : "OPERATION_FAILED";
    if (operations.addAttention(entity, itemId, attention, item.version()) != 1) throw changed();
    operations.auditOutcome(entity, itemId, operation, next, code);
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT, "OPERATION_STATE_CHANGED", "This operation changed. Refresh and try again."); }
}
