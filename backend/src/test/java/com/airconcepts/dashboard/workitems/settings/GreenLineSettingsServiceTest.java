package com.airconcepts.dashboard.workitems.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class GreenLineSettingsServiceTest {
  private final GreenLineSettingsRepository repository = mock(GreenLineSettingsRepository.class);
  private final GreenLineSettingsService service = new GreenLineSettingsService(repository);
  private final UUID entity = UUID.randomUUID(), actor = UUID.randomUUID();
  private final UUID manager = UUID.randomUUID(), owner = UUID.randomUUID();
  private final CurrentUser director = new CurrentUser(actor.toString(), Role.DIRECTOR);
  private GreenLineSettingsRepository.Settings desired;

  @BeforeEach void setUp() {
    desired = new GreenLineSettingsRepository.Settings(entity, true, owner, manager, 2, 90);
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.DIRECTOR));
    when(repository.lockEntity(entity)).thenReturn(true);
    when(repository.findStaffRole(owner)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    when(repository.findStaffRole(manager)).thenReturn(Optional.of(Role.STANDARD_USER));
    when(repository.get(entity)).thenReturn(Optional.empty());
  }

  @Test void gl08_directorSavesAndFillsOnlyUnownedItems() {
    assertThat(service.save(director, desired)).isEqualTo(desired);
    verify(repository).save(desired, actor);
    verify(repository).auditSettings(entity, actor, GreenLineSettingsRepository.Settings.defaults(entity), desired);
    verify(repository).fillUnowned(entity, actor, owner, manager);
  }

  @Test void gl09_directorMustStillBeActiveAndDirectorInsideTransaction() {
    when(repository.lockActor(actor)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    assertThatThrownBy(() -> service.save(director, desired)).isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).lockEntity(any());
    verify(repository, never()).save(any(), any());
  }

  @Test void gl08_defaultOwnerMustBeActiveFinanceAndManagerMustBeActive() {
    when(repository.findStaffRole(owner)).thenReturn(Optional.of(Role.STANDARD_USER));
    assertThatThrownBy(() -> service.save(director, desired)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure) e).code()).isEqualTo("DEFAULT_OWNER_UNAVAILABLE");
    when(repository.findStaffRole(owner)).thenReturn(Optional.of(Role.DIRECTOR));
    when(repository.findStaffRole(manager)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.save(director, desired)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure) e).code()).isEqualTo("SERVICE_MANAGER_UNAVAILABLE");
    verify(repository, never()).save(any(), any());
  }

  @Test void gl11_and_gl23_invalidThresholdOrBackfillCannotBeSaved() {
    var threshold = new GreenLineSettingsRepository.Settings(entity, true, owner, manager, 0, 90);
    var backfill = new GreenLineSettingsRepository.Settings(entity, true, owner, manager, 2, 366);
    assertThatThrownBy(() -> service.save(director, threshold)).isInstanceOf(ApiFailure.class);
    assertThatThrownBy(() -> service.save(director, backfill)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(repository);
  }

  @Test void gl06_archivedOrUnknownEntityCannotBeConfigured() {
    when(repository.lockEntity(entity)).thenReturn(false);
    assertThatThrownBy(() -> service.save(director, desired)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure) e).code()).isEqualTo("ENTITY_UNAVAILABLE");
    verify(repository, never()).save(any(), any());
  }

  @Test void gl07_unchangedSettingsDoNotCreateAuditEvents() {
    when(repository.get(entity)).thenReturn(Optional.of(desired));
    assertThat(service.save(director, desired)).isEqualTo(desired);
    verify(repository, never()).save(any(), any());
    verify(repository, never()).auditSettings(any(), any(), any(), any());
  }

  @Test void gl11_thresholdChangeRecalculatesExistingInvoiceDeadlines() {
    when(repository.get(entity)).thenReturn(Optional.of(GreenLineSettingsRepository.Settings.defaults(entity)));
    var changed = new GreenLineSettingsRepository.Settings(entity, true, owner, manager, 4, 90);
    service.save(director, changed);
    verify(repository).recalculateLateDates(entity, 4);
  }

  @Test void gl11_unchangedThresholdDoesNotRecalculateDeadlines() {
    service.save(director, desired);
    verify(repository, never()).recalculateLateDates(any(), anyInt());
  }

  @Test void gl06_readDefaultsForActiveEntityAndRejectsMissingEntity() {
    when(repository.entityExists(entity)).thenReturn(true, false);
    assertThat(service.get(new CurrentUser(actor.toString(), Role.STANDARD_USER), entity))
        .isEqualTo(GreenLineSettingsRepository.Settings.defaults(entity));
    assertThatThrownBy(() -> service.get(director, entity)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure) e).code()).isEqualTo("ENTITY_UNAVAILABLE");
  }
}
