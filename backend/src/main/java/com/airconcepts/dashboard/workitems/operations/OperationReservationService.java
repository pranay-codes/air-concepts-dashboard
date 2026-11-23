package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserves an initial or explicitly requested retry attempt without calling a provider. */
@Service
public class OperationReservationService {
  public record Reservation(UUID id, int attempt) {}
  private final WorkItemRepository items;
  private final ExternalOperationRepository operations;
  public OperationReservationService(WorkItemRepository items, ExternalOperationRepository operations) {
    this.items = items; this.operations = operations;
  }

  @Transactional
  public Reservation reserve(CurrentUser user, UUID entity, UUID itemId, int version,
      ExternalOperationRepository.Kind kind, String lookup, String fingerprint) {
    return reserveAttempt(user,entity,itemId,version,kind,null,lookup,fingerprint);
  }

  @Transactional
  public Reservation retry(CurrentUser user, UUID entity, UUID itemId, int version,
      ExternalOperationRepository.Kind kind, UUID previous, String lookup, String fingerprint) {
    boolean retryable = kind == ExternalOperationRepository.Kind.VWORK_CREATE_JOB
        || kind == ExternalOperationRepository.Kind.XERO_ACCEPT_QUOTE;
    if (previous == null || !retryable)
      throw failure(HttpStatus.BAD_REQUEST,"OPERATION_INVALID","Choose an operation to retry.");
    return reserveAttempt(user,entity,itemId,version,kind,previous,lookup,fingerprint);
  }

  private Reservation reserveAttempt(CurrentUser user, UUID entity, UUID itemId, int version,
      ExternalOperationRepository.Kind kind, UUID previous, String lookup, String fingerprint) {
    if (entity == null || itemId == null || kind == null || lookup == null || lookup.isBlank() || lookup.length() > 255
        || !lookup.equals(lookup.strip()) || lookup.chars().anyMatch(Character::isISOControl)
        || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}"))
      throw failure(HttpStatus.BAD_REQUEST, "OPERATION_INVALID", "The operation details are invalid.");
    UUID actor;
    try { actor = UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Active user access is required."); }
    Role role = items.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Active user access is required."));
    var item = items.lockItem(entity, itemId).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "This work item is unavailable."));
    if (item.version() != version) throw failure(HttpStatus.CONFLICT, "WORK_ITEM_CHANGED", "This work item changed. Refresh and try again.");
    var context = operations.context(entity, itemId);
    if (kind == ExternalOperationRepository.Kind.VWORK_CREATE_JOB) {
      if (role != Role.DIRECTOR && !actor.equals(context.serviceManagerId())) throw new AccessDeniedException("Service Manager access is required.");
      if (item.stage() != WorkItemStage.TO_SCHEDULE || context.jobId() != null) throw stage();
    } else if (kind == ExternalOperationRepository.Kind.XERO_CREATE_INVOICE) {
      if (role != Role.DIRECTOR && role != Role.FINANCIAL_ADMIN) throw new AccessDeniedException("Finance access is required.");
      if (item.stage() != WorkItemStage.TO_INVOICE || context.invoiceId() != null) throw stage();
    } else if (kind == ExternalOperationRepository.Kind.XERO_ACCEPT_QUOTE) {
      if (role != Role.DIRECTOR && role != Role.FINANCIAL_ADMIN) throw new AccessDeniedException("Finance access is required.");
      if (item.stage() != WorkItemStage.QUOTED || !"SENT".equals(operations.quoteStatus(entity, itemId).orElse(null))) throw stage();
    } else {
      // Report attachment has a different policy and is not wired until its own workflow is implemented.
      throw stage();
    }
    var latest = operations.latest(entity, itemId, kind);
    int attempt = 1;
    if (previous == null) {
      if (latest.isPresent()) throw failure(HttpStatus.CONFLICT, "OPERATION_ALREADY_EXISTS", "Review the existing operation before trying again.");
    } else {
      if (latest.isEmpty() || !previous.equals(latest.get().id()) || latest.get().status() != OperationState.Status.FAILED
          || latest.get().attempt() >= 20)
        throw failure(HttpStatus.CONFLICT, "OPERATION_STATE_CHANGED", "This operation cannot be retried. Refresh and review it.");
      attempt = OperationState.retryAttempt(latest.get().status(), latest.get().attempt());
    }
    var id = UUID.randomUUID();
    try { operations.insert(id, entity, itemId, kind, attempt, lookup, fingerprint, actor); }
    catch (DuplicateKeyException conflict) { throw failure(HttpStatus.CONFLICT, "OPERATION_IN_PROGRESS", "An operation is already in progress."); }
    if (operations.bumpVersion(entity, itemId, version) != 1)
      throw failure(HttpStatus.CONFLICT, "WORK_ITEM_CHANGED", "This work item changed. Refresh and try again.");
    operations.auditRequested(entity, itemId, actor, id, kind);
    return new Reservation(id, attempt);
  }
  private static ApiFailure failure(HttpStatus status, String code, String text) { return new ApiFailure(status, code, text); }
  private static ApiFailure stage() { return failure(HttpStatus.CONFLICT, "STAGE_ACTION_NOT_ALLOWED", "This action is not available at this stage."); }
}
