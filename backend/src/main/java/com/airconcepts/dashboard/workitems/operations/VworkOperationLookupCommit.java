package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.workitems.sync.VworkMirrorRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rechecks actor, mapping, connection and operation while committing the lookup result. */
@Service
public class VworkOperationLookupCommit {
  private final VworkMirrorRepository mirror;
  private final VworkOperationLookupContext contexts;
  private final VworkOperationCompletion completion;
  private final ExternalOperationRepository operations;
  public VworkOperationLookupCommit(VworkMirrorRepository mirror, VworkOperationLookupContext contexts,
      VworkOperationCompletion completion, ExternalOperationRepository operations) {
    this.mirror=mirror; this.contexts=contexts; this.completion=completion; this.operations=operations;
  }
  @Transactional
  public void link(CurrentUser user, UUID entity, UUID item, UUID operation, String revision, VworkClient.Job job) {
    mirror.lock(entity,item,revision).orElseThrow(VworkOperationLookupCommit::changed);
    contexts.load(user,entity,item,operation);
    completion.complete(entity,item,operation,revision,job,OperationState.Result.LINKED_EXISTING);
  }
  @Transactional
  public void notFound(CurrentUser user, UUID entity, UUID item, UUID operation, String revision) {
    var current=mirror.lock(entity,item,revision).orElseThrow(VworkOperationLookupCommit::changed);
    contexts.load(user,entity,item,operation);
    if (current.state().stage()!=WorkItemStage.TO_SCHEDULE || current.jobId()!=null) throw changed();
    if (operations.markNotFound(entity,item,operation)!=1) throw changed();
    if (operations.markLookupFailedAttention(entity,item,current.version())!=1) throw changed();
    operations.auditOutcome(entity,item,operation,OperationState.Status.FAILED,"NOT_FOUND");
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"OPERATION_STATE_CHANGED","This job or operation changed. Refresh and try again."); }
}
