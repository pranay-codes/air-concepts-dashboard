package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirms an already-observed external job; this method never calls the provider. */
@Service
public class VworkOperationCompletion {
  private final VworkMirrorRepository mirror;
  private final ExternalOperationRepository operations;
  public VworkOperationCompletion(VworkMirrorRepository mirror, ExternalOperationRepository operations) {
    this.mirror = mirror; this.operations = operations;
  }
  @Transactional
  public void complete(UUID entity, UUID item, UUID operation, String connectionRevision, VworkClient.Job job,
      OperationState.Result result) {
    if (job == null || job.id() == null || job.id().isBlank() || job.id().length() > 120 || job.status() == null
        || (result != OperationState.Result.CREATED && result != OperationState.Result.LINKED_EXISTING)) throw invalid();
    var current = mirror.lock(entity, item, connectionRevision).orElseThrow(VworkOperationCompletion::changed);
    var pending = operations.lockOperation(entity, item, operation).orElseThrow(VworkOperationCompletion::changed);
    if (pending.kind() != ExternalOperationRepository.Kind.VWORK_CREATE_JOB
        || (pending.status() != OperationState.Status.PENDING && pending.status() != OperationState.Status.UNKNOWN)
        || (result == OperationState.Result.CREATED && pending.status() != OperationState.Status.PENDING)
        || current.state().stage() != WorkItemStage.TO_SCHEDULE || current.jobId() != null) throw changed();
    if (pending.lookupReference() == null || !Objects.equals(pending.lookupReference(), job.reference())
        || !mirror.templateBelongsTo(entity, job.templateId()) || mirror.jobLinkedElsewhere(item, job.id())) throw invalid();
    try {
      if (operations.linkVwork(entity, item, current.version(), pending.lookupReference(), job) != 1) throw changed();
      if (operations.complete(entity, item, operation, result, job.id()) != 1) throw changed();
      operations.clearResolvedUnknown(entity, item);
      operations.clearResolvedFailed(entity, item);
    } catch (DuplicateKeyException duplicate) { throw invalid(); }
    operations.auditCompleted(entity, item, operation, result);
  }
  private static ApiFailure invalid() { return new ApiFailure(HttpStatus.CONFLICT, "VWORK_LINK_UNVERIFIED", "The vWork job could not be safely linked."); }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT, "OPERATION_STATE_CHANGED", "This operation or job changed. Refresh and review it."); }
}
