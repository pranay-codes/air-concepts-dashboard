package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemService {
  private final WorkItemRepository repository;
  public WorkItemService(WorkItemRepository repository) { this.repository = repository; }

  @Transactional
  public WorkItemRepository.Item edit(CurrentUser user, UUID entity, UUID id, int version,
      LocalDate followUp, String notes) {
    return editPartial(user, entity, id, version, true, followUp, false, true, notes);
  }

  /** Presence is explicit so a partial request never erases an omitted field. */
  @Transactional
  public WorkItemRepository.Item editPartial(CurrentUser user, UUID entity, UUID id, int version,
      boolean followUpProvided, LocalDate followUp, boolean clearFollowUp, boolean notesProvided, String notes) {
    return editItem(user, entity, id, version, followUpProvided, followUp, clearFollowUp, notesProvided, notes,
        false, null, false);
  }

  /**
   * Owner changes are finance-only and must name an active Director or Financial Admin. Owners may still
   * edit the follow-up date and notes on their own items.
   */
  @Transactional
  public WorkItemRepository.Item editItem(CurrentUser user, UUID entity, UUID id, int version,
      boolean followUpProvided, LocalDate followUp, boolean clearFollowUp, boolean notesProvided, String notes,
      boolean ownerProvided, UUID ownerId, boolean clearOwner) {
    if (clearFollowUp && followUpProvided) {
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_WORK_ITEM_UPDATE", "Choose a follow-up date or clear it, not both.");
    }
    if (ownerProvided && clearOwner) {
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_WORK_ITEM_UPDATE", "Choose an owner or clear it, not both.");
    }
    if (notesProvided && notes != null && notes.length() > 4000) {
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_NOTES", "Notes must be at most 4,000 characters.");
    }
    UUID actor = actorId(user);
    Role role = repository.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Active user access is required."));
    WorkItemRepository.Item item = item(entity, id);
    boolean finance = role == Role.DIRECTOR || role == Role.FINANCIAL_ADMIN;
    if (!finance && !actor.equals(item.ownerId())) throw new AccessDeniedException("Only the owner or finance can edit this item.");
    boolean ownerRequested = ownerProvided || clearOwner;
    if (ownerRequested && !finance) throw new AccessDeniedException("Only a Director or Financial Admin can change the owner.");
    if (item.stage() == WorkItemStage.CLOSED) throw stageConflict();
    requireVersion(item, version);
    if (ownerProvided && !repository.isAssignableOwner(ownerId)) {
      throw new ApiFailure(HttpStatus.CONFLICT, "OWNER_UNAVAILABLE", "Choose an active Director or Financial Admin.");
    }
    UUID owner = clearOwner ? null : ownerProvided ? ownerId : item.ownerId();
    if (!followUpProvided && !clearFollowUp) followUp = item.followUpDate();
    if (clearFollowUp) followUp = null;
    if (!notesProvided) notes = item.notes();
    boolean dateChanged = !Objects.equals(followUp, item.followUpDate());
    boolean notesChanged = !Objects.equals(notes, item.notes());
    boolean ownerChanged = !Objects.equals(owner, item.ownerId());
    if (!dateChanged && !notesChanged && !ownerChanged) return item;
    requireSaved(repository.save(entity, id, item.stage(), followUp, notes, owner, version));
    if (dateChanged) repository.auditEdit(entity, id, actor, "FOLLOW_UP_CHANGED", date(item.followUpDate()), date(followUp));
    if (notesChanged) repository.auditEdit(entity, id, actor, "NOTES_CHANGED", item.notes(), notes);
    if (ownerChanged) repository.auditEdit(entity, id, actor, "OWNER_CHANGED",
        item.ownerId() == null ? null : item.ownerId().toString(), owner == null ? null : owner.toString());
    return new WorkItemRepository.Item(id, entity, item.stage(), owner, followUp, notes, version + 1);
  }

  @Transactional
  public WorkItemRepository.Item closeLost(CurrentUser user, UUID entity, UUID id, int version, String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500) {
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Enter a reason of 1 to 500 characters.");
    }
    UUID actor = actorId(user);
    Role role = repository.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Active user access is required."));
    if (role != Role.DIRECTOR && role != Role.FINANCIAL_ADMIN) throw new AccessDeniedException("Finance access is required.");
    WorkItemRepository.Item item = item(entity, id);
    if (item.stage() != WorkItemStage.QUOTED) throw stageConflict();
    requireVersion(item, version);
    if (repository.hasBlockingOperation(entity, id)) throw operationInProgress();
    requireSaved(repository.closeLost(entity, id, version, reason));
    repository.auditClose(entity, id, actor, reason);
    return new WorkItemRepository.Item(id, entity, WorkItemStage.CLOSED, item.ownerId(), item.followUpDate(), item.notes(), version + 1);
  }

  @Transactional
  public WorkItemRepository.Item closeNoInvoice(CurrentUser user, UUID entity, UUID id, int version, String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500) {
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Enter a reason of 1 to 500 characters.");
    }
    UUID actor = actorId(user);
    Role role = repository.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Active user access is required."));
    if (role != Role.DIRECTOR && role != Role.FINANCIAL_ADMIN) throw new AccessDeniedException("Finance access is required.");
    WorkItemRepository.Item item = item(entity, id);
    if (item.stage() != WorkItemStage.TO_INVOICE) throw stageConflict();
    requireVersion(item, version);
    if (repository.hasBlockingOperation(entity, id)) throw operationInProgress();
    if (repository.closeNoInvoice(entity, id, version, reason) != 1) throw stageConflict();
    repository.auditNoInvoice(entity, id, actor, reason);
    return new WorkItemRepository.Item(id, entity, WorkItemStage.CLOSED, item.ownerId(), item.followUpDate(), item.notes(), version + 1);
  }

  @Transactional
  public WorkItemRepository.Item markSent(CurrentUser user, UUID entity, UUID id, int version) {
    UUID actor=actorId(user);
    Role role=repository.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Active user access is required."));
    if (role!=Role.DIRECTOR && role!=Role.FINANCIAL_ADMIN) throw new AccessDeniedException("Finance access is required.");
    var item=item(entity,id);
    if (item.stage()!=WorkItemStage.INVOICE_DRAFTED) throw stageConflict();
    requireVersion(item,version);
    if (repository.markSent(entity,id,version)!=1) throw stageConflict();
    repository.auditMarkSent(entity,id,actor);
    return new WorkItemRepository.Item(id,entity,WorkItemStage.INVOICED,item.ownerId(),item.followUpDate(),item.notes(),version+1);
  }

  private WorkItemRepository.Item item(UUID entity, UUID id) {
    return repository.lockItem(entity, id).orElseThrow(() ->
        new ApiFailure(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "This work item is unavailable."));
  }

  private static UUID actorId(CurrentUser user) {
    try { return UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Active user access is required."); }
  }

  private static String date(LocalDate value) { return value == null ? null : value.toString(); }
  private static void requireVersion(WorkItemRepository.Item item, int version) {
    if (item.version() != version) throw changed();
  }
  private static void requireSaved(int count) { if (count != 1) throw changed(); }
  private static ApiFailure changed() {
    return new ApiFailure(HttpStatus.CONFLICT, "WORK_ITEM_CHANGED", "This work item changed. Refresh and try again.");
  }
  private static ApiFailure stageConflict() {
    return new ApiFailure(HttpStatus.CONFLICT, "STAGE_ACTION_NOT_ALLOWED", "This action is not available at this stage.");
  }
  private static ApiFailure operationInProgress() {
    return new ApiFailure(HttpStatus.CONFLICT, "OPERATION_IN_PROGRESS", "Resolve the existing operation before closing this work item.");
  }
}
