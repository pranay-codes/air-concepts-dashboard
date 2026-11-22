package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks the current actor and the entity-scoped unknown operation before provider reads. */
@Service
public class VworkOperationLookupContext {
  public record Lookup(String reference) {}
  private final WorkItemRepository items;
  private final ExternalOperationRepository operations;
  public VworkOperationLookupContext(WorkItemRepository items, ExternalOperationRepository operations) {
    this.items=items; this.operations=operations;
  }
  @Transactional
  public Lookup load(CurrentUser user, UUID entity, UUID item, UUID operation) {
    user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN,Role.STANDARD_USER);
    UUID actor;
    try { actor=UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Active user access is required."); }
    Role role=items.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Active user access is required."));
    items.lockItem(entity,item).orElseThrow(VworkOperationLookupContext::unavailable);
    var relation=operations.context(entity,item);
    if (role != Role.DIRECTOR && !actor.equals(relation.serviceManagerId())) throw new AccessDeniedException("Service Manager access is required.");
    var stored=operations.lockOperation(entity,item,operation).orElseThrow(VworkOperationLookupContext::unavailable);
    if (stored.kind()!=ExternalOperationRepository.Kind.VWORK_CREATE_JOB || stored.status()!=OperationState.Status.UNKNOWN
        || stored.lookupReference()==null || stored.lookupReference().isBlank())
      throw new ApiFailure(HttpStatus.CONFLICT,"OPERATION_STATE_CHANGED","This operation is not awaiting lookup.");
    return new Lookup(stored.lookupReference());
  }
  private static ApiFailure unavailable() { return new ApiFailure(HttpStatus.NOT_FOUND,"OPERATION_NOT_FOUND","This operation is unavailable."); }
}
