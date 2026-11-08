package com.airconcepts.dashboard.staff;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class StaffServiceTest {
  private final StaffRepository repository = mock(StaffRepository.class);
  private final SupabaseAdminClient auth = mock(SupabaseAdminClient.class);
  private final StaffService service = new StaffService(repository, auth);
  private final UUID directorId = UUID.randomUUID();
  private final CurrentUser director = new CurrentUser(directorId.toString(), Role.DIRECTOR);
  private final UUID target = UUID.randomUUID();

  @Test
  void frUc15_16_onlyDirectorMayListCreateOrChangeUsers() {
    for (Role role : List.of(Role.FINANCIAL_ADMIN, Role.STANDARD_USER)) {
      var caller = new CurrentUser(UUID.randomUUID().toString(), role);
      assertThatThrownBy(() -> service.list(caller)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> service.create(caller, "user@example.invalid", "Staff", Role.STANDARD_USER))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> service.update(caller, target, "Staff", Role.DIRECTOR, false))
          .isInstanceOf(AccessDeniedException.class);
    }
    verifyNoInteractions(repository, auth);
  }

  @Test
  void frUc15_16_provisionsWithPersistedIdentityBeforeGrantingDashboardAccess() {
    var draft = new StaffProvision(target, "user@example.invalid", "Staff", Role.STANDARD_USER);
    var expected = member(true);
    when(repository.reserve("user@example.invalid", "Staff", Role.STANDARD_USER)).thenReturn(draft);
    when(repository.complete(draft)).thenReturn(expected);
    assertThat(service.create(director, " User@Example.invalid ", " Staff ", Role.STANDARD_USER))
        .isEqualTo(expected);
    var order = inOrder(repository, auth);
    order.verify(repository).reserve("user@example.invalid", "Staff", Role.STANDARD_USER);
    order.verify(auth).ensureUser(target, "user@example.invalid");
    order.verify(repository).complete(draft);
  }

  @Test
  void frUc15_16_providerTimeoutDoesNotGrantAccessAndRetryUsesSameProvision() {
    var draft = new StaffProvision(target, "user@example.invalid", "Staff", Role.STANDARD_USER);
    when(repository.reserve(draft.email(), draft.displayName(), draft.role())).thenReturn(draft);
    doThrow(new IllegalStateException("provider unavailable")).doNothing()
        .when(auth).ensureUser(target, draft.email());
    assertThatThrownBy(() -> service.create(director, draft.email(), draft.displayName(), draft.role()))
        .isInstanceOf(IllegalStateException.class);
    verify(repository, never()).complete(any());
    service.create(director, draft.email(), draft.displayName(), draft.role());
    verify(auth, times(2)).ensureUser(target, draft.email());
    verify(repository).complete(draft);
  }

  @Test
  void frUc15_18_deactivationCommitsAccessRevocationBeforeProviderBanEvenOnFailure() {
    when(repository.findById(target)).thenReturn(Optional.of(member(true)));
    when(repository.update(directorId, target, "Staff", Role.STANDARD_USER, false)).thenReturn(member(false));
    doThrow(new IllegalStateException("provider unavailable")).when(auth).setActive(target, false);
    assertThatThrownBy(() -> service.update(director, target, "Staff", Role.STANDARD_USER, false))
        .isInstanceOf(IllegalStateException.class);
    var order = inOrder(repository, auth);
    order.verify(repository).update(directorId, target, "Staff", Role.STANDARD_USER, false);
    order.verify(auth).setActive(target, false);
  }

  @Test
  void frUc15_18_reactivationNeverGrantsAccessWhenProviderUnbanFails() {
    when(repository.findById(target)).thenReturn(Optional.of(member(false)));
    doThrow(new IllegalStateException("provider unavailable")).when(auth).setActive(target, true);
    assertThatThrownBy(() -> service.update(director, target, "Staff", Role.STANDARD_USER, true))
        .isInstanceOf(IllegalStateException.class);
    verify(repository, never()).update(any(), any(), anyString(), any(), anyBoolean());
  }

  @Test
  void frUc15_18_competingReactivationIsReconciledAfterALateBanRatherThanReportedAsSuccess() {
    when(repository.findById(target)).thenReturn(Optional.of(member(true)));
    when(repository.update(directorId, target, "Staff", Role.STANDARD_USER, false)).thenReturn(member(false));
    assertThatThrownBy(() -> service.update(director, target, "Staff", Role.STANDARD_USER, false))
        .isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class).hasMessageContaining("changed");
    var order = inOrder(auth);
    order.verify(auth).setActive(target, false);
    order.verify(auth).setActive(target, true);
  }

  @Test
  void frUc15_19_assignmentChoicesAreActiveStaffOnlyAndRequireAssignmentRole() {
    when(repository.activeChoices()).thenReturn(List.of(new StaffOption(target, "Staff")));
    assertThat(service.choices(new CurrentUser(directorId.toString(), Role.FINANCIAL_ADMIN))).hasSize(1);
    assertThatThrownBy(() -> service.choices(new CurrentUser(directorId.toString(), Role.STANDARD_USER)))
        .isInstanceOf(AccessDeniedException.class);
  }

  private StaffMember member(boolean active) {
    return new StaffMember(target, "user@example.invalid", "Staff", Role.STANDARD_USER, active);
  }
}
