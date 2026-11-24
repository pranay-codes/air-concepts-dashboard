package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WorkItemServiceTest {
  private final WorkItemRepository repository = mock(WorkItemRepository.class);
  private final WorkItemService service = new WorkItemService(repository);
  private final UUID actor = UUID.randomUUID(), entity = UUID.randomUUID(), item = UUID.randomUUID();
  private final CurrentUser user = new CurrentUser(actor.toString(), Role.STANDARD_USER);

  private WorkItemRepository.Item open(UUID owner, int version) {
    return new WorkItemRepository.Item(item, entity, WorkItemStage.QUOTED, owner, null, null, version);
  }

  @BeforeEach void setUp() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.STANDARD_USER));
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(open(actor, 2)));
    when(repository.save(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(1);
    when(repository.closeLost(any(), any(), anyInt(), any())).thenReturn(1);
  }

  @Test void gl04_ownerEditsAndWritesSeparateAuditEvents() {
    var date = LocalDate.of(2026, 11, 23);
    var result = service.edit(user, entity, item, 2, date, "Call customer");
    assertThat(result.version()).isEqualTo(3);
    verify(repository).save(entity, item, WorkItemStage.QUOTED, date, "Call customer", actor, 2);
    verify(repository).auditEdit(entity, item, actor, "FOLLOW_UP_CHANGED", null, date.toString());
    verify(repository).auditEdit(entity, item, actor, "NOTES_CHANGED", null, "Call customer");
  }

  @Test void gl09_currentDatabaseRoleAndOwnershipGovernAccess() {
    when(repository.lockActor(actor)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.edit(user, entity, item, 2, null, "note"))
        .isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).lockItem(any(), any());
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.STANDARD_USER));
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(open(UUID.randomUUID(), 2)));
    assertThatThrownBy(() -> service.edit(user, entity, item, 2, null, "note"))
        .isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).save(any(), any(), any(), any(), any(), any(), anyInt());
  }

  @Test void gl06_unknownOrCrossEntityItemIsNotFound() {
    when(repository.lockItem(entity, item)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.edit(user, entity, item, 2, null, "note"))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("WORK_ITEM_NOT_FOUND");
    verify(repository, never()).save(any(), any(), any(), any(), any(), any(), anyInt());
  }

  @Test void gl04_staleVersionOrFailedCompareAndSetNeverAudits() {
    assertThatThrownBy(() -> service.edit(user, entity, item, 1, null, "note"))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("WORK_ITEM_CHANGED");
    when(repository.save(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(0);
    assertThatThrownBy(() -> service.edit(user, entity, item, 2, null, "note"))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("WORK_ITEM_CHANGED");
    verify(repository, never()).auditEdit(any(), any(), any(), any(), any(), any());
  }

  @Test void gl05_financeCanCloseOnlyQuotedItemsWithReason() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    var result = service.closeLost(user, entity, item, 2, "Customer declined");
    assertThat(result.stage()).isEqualTo(WorkItemStage.CLOSED);
    verify(repository).closeLost(entity, item, 2, "Customer declined");
    verify(repository).auditClose(entity, item, actor, "Customer declined");
    assertThatThrownBy(() -> service.closeLost(user, entity, item, 2, "  "))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("REASON_REQUIRED");
  }

  @Test void gl04_unchangedEditDoesNotIncrementVersionOrAudit() {
    var result = service.edit(user, entity, item, 2, null, null);
    assertThat(result.version()).isEqualTo(2);
    verify(repository, never()).save(any(), any(), any(), any(), any(), any(), anyInt());
    verify(repository, never()).auditEdit(any(), any(), any(), any(), any(), any());
  }

  @Test void gl20_financeCanCloseWithoutInvoiceAndAuditTheReason() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(
        item, entity, WorkItemStage.JOB_COMPLETED, actor, null, null, 2)));
    when(repository.closeNoInvoice(entity, item, 2, "No charge")).thenReturn(1);
    var result = service.closeNoInvoice(user, entity, item, 2, "No charge");
    assertThat(result.stage()).isEqualTo(WorkItemStage.CLOSED);
    assertThat(result.version()).isEqualTo(3);
    verify(repository).auditNoInvoice(entity, item, actor, "No charge");
  }
  @Test void owner_financeAssignsActiveDirectorOrFinancialAdminAndAudits() {
    var previous = UUID.randomUUID();
    var newOwner = UUID.randomUUID();
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(open(previous, 2)));
    when(repository.isAssignableOwner(newOwner)).thenReturn(true);
    var result = service.editItem(user, entity, item, 2, false, null, false, false, null, true, newOwner, false);
    assertThat(result.ownerId()).isEqualTo(newOwner);
    assertThat(result.version()).isEqualTo(3);
    verify(repository).save(entity, item, WorkItemStage.QUOTED, null, null, newOwner, 2);
    verify(repository).auditEdit(entity, item, actor, "OWNER_CHANGED", previous.toString(), newOwner.toString());
  }

  @Test void owner_clearsToUnassignedForFinance() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    var result = service.editItem(user, entity, item, 2, false, null, false, false, null, false, null, true);
    assertThat(result.ownerId()).isNull();
    verify(repository).save(entity, item, WorkItemStage.QUOTED, null, null, null, 2);
    verify(repository).auditEdit(entity, item, actor, "OWNER_CHANGED", actor.toString(), null);
  }

  @Test void owner_standardOwnerCannotReassignEvenOwnItem() {
    assertThatThrownBy(() -> service.editItem(user, entity, item, 2, false, null, false, false, null, true,
        UUID.randomUUID(), false)).isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).save(any(), any(), any(), any(), any(), any(), anyInt());
  }

  @Test void owner_rejectsCandidateThatIsNotActiveDirectorOrFinancialAdmin() {
    var candidate = UUID.randomUUID();
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    when(repository.isAssignableOwner(candidate)).thenReturn(false);
    assertThatThrownBy(() -> service.editItem(user, entity, item, 2, false, null, false, false, null, true, candidate, false))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("OWNER_UNAVAILABLE");
    verify(repository, never()).save(any(), any(), any(), any(), any(), any(), anyInt());
  }

  @Test void owner_setAndClearTogetherIsRejected() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    assertThatThrownBy(() -> service.editItem(user, entity, item, 2, false, null, false, false, null, true,
        UUID.randomUUID(), true)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure) e).code()).isEqualTo("INVALID_WORK_ITEM_UPDATE");
  }

  @Test void gl21_financeCanConfirmAuthorisedInvoiceWasSent() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    when(repository.lockItem(entity,item)).thenReturn(Optional.of(new WorkItemRepository.Item(
        item,entity,WorkItemStage.INVOICE_CREATED,actor,null,null,2)));
    when(repository.markSent(entity,item,2)).thenReturn(1);
    assertThat(service.markSent(user,entity,item,2).stage()).isEqualTo(WorkItemStage.INVOICED);
    verify(repository).auditMarkSent(entity,item,actor);
  }
  @Test void gl21_markSentRejectsUnauthorisedRoleOrUnapprovedInvoice() {
    assertThatThrownBy(() -> service.markSent(user,entity,item,2)).isInstanceOf(AccessDeniedException.class);
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(repository.lockItem(entity,item)).thenReturn(Optional.of(new WorkItemRepository.Item(
        item,entity,WorkItemStage.JOB_COMPLETED,actor,null,null,2)));
    when(repository.markSent(entity,item,2)).thenReturn(0);
    assertThatThrownBy(() -> service.markSent(user,entity,item,2)).isInstanceOf(ApiFailure.class);
    verify(repository,never()).auditMarkSent(any(),any(),any());
  }

  @Test void gl20_noInvoiceRequiresFinanceCorrectStageReasonAndVersion() {
    assertThatThrownBy(() -> service.closeNoInvoice(user, entity, item, 2, "No charge"))
        .isInstanceOf(AccessDeniedException.class);
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    assertThatThrownBy(() -> service.closeNoInvoice(user, entity, item, 2, " "))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("REASON_REQUIRED");
    assertThatThrownBy(() -> service.closeNoInvoice(user, entity, item, 2, "No charge"))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("STAGE_ACTION_NOT_ALLOWED");
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(
        item, entity, WorkItemStage.JOB_COMPLETED, actor, null, null, 2)));
    assertThatThrownBy(() -> service.closeNoInvoice(user, entity, item, 1, "No charge"))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("WORK_ITEM_CHANGED");
    verify(repository, never()).closeNoInvoice(any(), any(), anyInt(), any());
    verify(repository, never()).auditNoInvoice(any(), any(), any(), any());
  }

  @Test void gl20_linkedInvoiceCannotBeClosedWithoutInvoice() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(
        item, entity, WorkItemStage.JOB_COMPLETED, actor, null, null, 2)));
    when(repository.closeNoInvoice(entity, item, 2, "No charge")).thenReturn(0);
    assertThatThrownBy(() -> service.closeNoInvoice(user, entity, item, 2, "No charge"))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("STAGE_ACTION_NOT_ALLOWED");
    verify(repository, never()).auditNoInvoice(any(), any(), any(), any());
  }

  @Test void gl22_unresolvedExternalOperationBlocksManualClosureWithoutAudit() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(
        item, entity, WorkItemStage.JOB_COMPLETED, actor, null, null, 2)));
    when(repository.hasBlockingOperation(entity, item)).thenReturn(true);
    assertThatThrownBy(() -> service.closeNoInvoice(user, entity, item, 2, "No charge"))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("OPERATION_IN_PROGRESS"));
    verify(repository, never()).closeNoInvoice(any(), any(), anyInt(), any());
    verify(repository, never()).auditNoInvoice(any(), any(), any(), any());
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(open(actor, 2)));
    assertThatThrownBy(() -> service.closeLost(user, entity, item, 2, "Customer declined"))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("OPERATION_IN_PROGRESS"));
    verify(repository, never()).closeLost(any(), any(), anyInt(), any());
    verify(repository, never()).auditClose(any(), any(), any(), any());
  }

  @Test void gl04_patchPreservesOmittedFieldsAndAuditsOnlyChanges() {
    var previousDate = LocalDate.of(2026, 11, 25);
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(
        item, entity, WorkItemStage.QUOTED, actor, previousDate, "Existing note", 2)));
    var result = service.editPartial(user, entity, item, 2, false, null, false, true, "New note");
    assertThat(result.followUpDate()).isEqualTo(previousDate);
    verify(repository).save(entity, item, WorkItemStage.QUOTED, previousDate, "New note", actor, 2);
    verify(repository, never()).auditEdit(eq(entity), eq(item), eq(actor), eq("FOLLOW_UP_CHANGED"), any(), any());
    verify(repository).auditEdit(entity, item, actor, "NOTES_CHANGED", "Existing note", "New note");
  }

  @Test void gl04_patchClearFollowUpPreservesNotesAndRejectsContradictoryInput() {
    var previousDate = LocalDate.of(2026, 11, 25);
    when(repository.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(
        item, entity, WorkItemStage.QUOTED, actor, previousDate, "Keep this", 2)));
    var result = service.editPartial(user, entity, item, 2, false, null, true, false, null);
    assertThat(result.followUpDate()).isNull();
    assertThat(result.notes()).isEqualTo("Keep this");
    verify(repository).auditEdit(entity, item, actor, "FOLLOW_UP_CHANGED", previousDate.toString(), null);
    assertThatThrownBy(() -> service.editPartial(user, entity, item, 2, true, previousDate, true, false, null))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure) e).code()).isEqualTo("INVALID_WORK_ITEM_UPDATE");
  }
}
