package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OperationReservationServiceTest {
  private final WorkItemRepository items = mock(WorkItemRepository.class);
  private final ExternalOperationRepository operations = mock(ExternalOperationRepository.class);
  private final OperationReservationService service = new OperationReservationService(items, operations);
  private final UUID entity = UUID.randomUUID(), item = UUID.randomUUID(), actor = UUID.randomUUID();
  private final CurrentUser director = new CurrentUser(actor.toString(), Role.DIRECTOR);
  private final String hash = "a".repeat(64);

  @BeforeEach void setup() {
    when(items.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(items.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(item, entity, WorkItemStage.TO_SCHEDULE, actor, null, null, 7)));
    when(operations.context(entity, item)).thenReturn(new ExternalOperationRepository.Context(null, null, actor));
    when(operations.latest(entity, item, ExternalOperationRepository.Kind.VWORK_CREATE_JOB)).thenReturn(Optional.empty());
    when(operations.bumpVersion(entity, item, 7)).thenReturn(1);
  }

  @Test void gl22_reservePersistsPendingBeforeAnyProviderCall() {
    var result = service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", hash);
    assertThat(result.id()).isNotNull();
    assertThat(result.attempt()).isEqualTo(1);
    var ordered = inOrder(items, operations);
    ordered.verify(items).lockActor(actor);
    ordered.verify(items).lockItem(entity, item);
    ordered.verify(operations).context(entity, item);
    ordered.verify(operations).latest(entity, item, ExternalOperationRepository.Kind.VWORK_CREATE_JOB);
    ordered.verify(operations).insert(result.id(), entity, item, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, 1, "J-1", hash, actor);
    ordered.verify(operations).bumpVersion(entity, item, 7);
    ordered.verify(operations).auditRequested(entity, item, actor, result.id(), ExternalOperationRepository.Kind.VWORK_CREATE_JOB);
  }

  @Test void gl22_existingAttemptMustUseReconciliationOrExplicitRetry() {
    for (var status : OperationState.Status.values()) {
      when(operations.latest(entity, item, ExternalOperationRepository.Kind.VWORK_CREATE_JOB))
          .thenReturn(Optional.of(new ExternalOperationRepository.Existing(status, 1)));
      assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", hash))
          .isInstanceOfSatisfying(ApiFailure.class, e -> assertThat(e.code()).isEqualTo("OPERATION_ALREADY_EXISTS"));
    }
    verify(operations, never()).insert(any(), any(), any(), any(), anyInt(), any(), any(), any());
  }

  @Test void gl22_explicitRetryReservesNextAttemptOnlyForLatestFailedOperation() {
    var previous=UUID.randomUUID();
    when(operations.latest(entity,item,ExternalOperationRepository.Kind.VWORK_CREATE_JOB))
        .thenReturn(Optional.of(new ExternalOperationRepository.Existing(previous,OperationState.Status.FAILED,1)));
    var result=service.retry(director,entity,item,7,ExternalOperationRepository.Kind.VWORK_CREATE_JOB,previous,"J-1",hash);
    assertThat(result.attempt()).isEqualTo(2);
    verify(operations).insert(result.id(),entity,item,ExternalOperationRepository.Kind.VWORK_CREATE_JOB,2,"J-1",hash,actor);
  }

  @Test void gl22_unknownOrStaleAttemptCannotBeRetried() {
    var previous=UUID.randomUUID();
    when(operations.latest(entity,item,ExternalOperationRepository.Kind.VWORK_CREATE_JOB))
        .thenReturn(Optional.of(new ExternalOperationRepository.Existing(previous,OperationState.Status.UNKNOWN,1)),
            Optional.of(new ExternalOperationRepository.Existing(previous,OperationState.Status.FAILED,1)));
    assertThatThrownBy(() -> service.retry(director,entity,item,7,ExternalOperationRepository.Kind.VWORK_CREATE_JOB,previous,"J-1",hash))
        .isInstanceOf(ApiFailure.class);
    assertThatThrownBy(() -> service.retry(director,entity,item,7,ExternalOperationRepository.Kind.VWORK_CREATE_JOB,UUID.randomUUID(),"J-1",hash))
        .isInstanceOf(ApiFailure.class);
    verify(operations,never()).insert(any(),any(),any(),any(),anyInt(),any(),any(),any());
  }

  @Test void gl06_staleRoleWrongEntityAndVersionAreRejected() {
    when(items.lockActor(actor)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", hash))
        .isInstanceOf(AccessDeniedException.class);
    when(items.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(items.lockItem(entity, item)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", hash))
        .isInstanceOfSatisfying(ApiFailure.class, e -> assertThat(e.code()).isEqualTo("WORK_ITEM_NOT_FOUND"));
    when(items.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(item, entity, WorkItemStage.TO_SCHEDULE, actor, null, null, 8)));
    assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", hash))
        .isInstanceOfSatisfying(ApiFailure.class, e -> assertThat(e.code()).isEqualTo("WORK_ITEM_CHANGED"));
    verify(operations, never()).insert(any(), any(), any(), any(), anyInt(), any(), any(), any());
  }

  @Test void gl09_managerAndFinanceHaveDifferentPermissions() {
    when(items.lockActor(actor)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    when(operations.context(entity, item)).thenReturn(new ExternalOperationRepository.Context(null, null, UUID.randomUUID()));
    assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", hash))
        .isInstanceOf(AccessDeniedException.class);
    when(items.lockItem(entity, item)).thenReturn(Optional.of(new WorkItemRepository.Item(item, entity, WorkItemStage.TO_INVOICE, actor, null, null, 7)));
    when(operations.latest(entity, item, ExternalOperationRepository.Kind.XERO_CREATE_INVOICE)).thenReturn(Optional.empty());
    assertThat(service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.XERO_CREATE_INVOICE, "J-1", hash).attempt()).isEqualTo(1);
  }

  @Test void gl22_invalidInputsAndAlreadyLinkedItemNeverReserve() {
    assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "", hash)).isInstanceOf(ApiFailure.class);
    assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", "bad" )).isInstanceOf(ApiFailure.class);
    when(operations.context(entity, item)).thenReturn(new ExternalOperationRepository.Context("job", null, actor));
    assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", hash)).isInstanceOf(ApiFailure.class);
    verify(operations, never()).insert(any(), any(), any(), any(), anyInt(), any(), any(), any());
  }

  @Test void gl22_duplicateReservationRaceReturnsConflictWithoutAudit() {
    doThrow(new org.springframework.dao.DuplicateKeyException("synthetic"))
        .when(operations).insert(any(), any(), any(), any(), anyInt(), any(), any(), any());
    assertThatThrownBy(() -> service.reserve(director, entity, item, 7, ExternalOperationRepository.Kind.VWORK_CREATE_JOB, "J-1", hash))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("OPERATION_IN_PROGRESS"));
    verify(operations, never()).bumpVersion(any(), any(), anyInt());
    verify(operations, never()).auditRequested(any(), any(), any(), any(), any());
  }
}
