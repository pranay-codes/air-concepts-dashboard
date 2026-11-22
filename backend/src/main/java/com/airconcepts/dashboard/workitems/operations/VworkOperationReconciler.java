package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.vwork.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Read-only reconciliation; a negative is accepted only for the deterministic mock. */
@Service
public class VworkOperationReconciler {
  private final VworkOperationLookupContext contexts;
  private final VworkOperationLookupCommit commit;
  private final VworkConnectionService connections;
  public VworkOperationReconciler(VworkOperationLookupContext contexts, VworkOperationLookupCommit commit,
      VworkConnectionService connections) { this.contexts=contexts; this.commit=commit; this.connections=connections; }
  public OperationState.Result lookup(CurrentUser user, UUID entity, UUID item, UUID operation) {
    var context=contexts.load(user,entity,item,operation);
    var access=connections.systemAccess();
    if (!access.searchVerified() || !(access.provider() instanceof MockVworkClient))
      throw new ApiFailure(HttpStatus.CONFLICT,"VWORK_SEARCH_UNVERIFIED","vWork search needs verification before resolving this operation.");
    java.util.List<VworkClient.Job> matches;
    try { matches=access.provider().findByReference(access.credential(),context.reference()); }
    catch (VworkFailure failure) { throw new ApiFailure(HttpStatus.BAD_GATEWAY,"VWORK_SEARCH_UNAVAILABLE","vWork could not be checked. Try again later."); }
    var current=connections.systemAccess();
    if (!access.revision().equals(current.revision()) || access.provider()!=current.provider())
      throw new ApiFailure(HttpStatus.CONFLICT,"VWORK_CONNECTION_CHANGED","The vWork connection changed. Retry lookup.");
    if (matches==null || matches.size()>1)
      throw new ApiFailure(HttpStatus.CONFLICT,"VWORK_MULTIPLE_MATCHES","The vWork result needs review before linking.");
    if (matches.isEmpty()) {
      commit.notFound(user,entity,item,operation,access.revision());
      return OperationState.Result.NOT_FOUND;
    }
    commit.link(user,entity,item,operation,access.revision(),matches.getFirst());
    return OperationState.Result.LINKED_EXISTING;
  }
}
