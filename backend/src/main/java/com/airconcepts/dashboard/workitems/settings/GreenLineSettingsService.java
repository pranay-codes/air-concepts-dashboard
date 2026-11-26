package com.airconcepts.dashboard.workitems.settings;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GreenLineSettingsService {
  private final GreenLineSettingsRepository repository;
  public GreenLineSettingsService(GreenLineSettingsRepository repository) { this.repository = repository; }

  public GreenLineSettingsRepository.Settings get(CurrentUser user, UUID entity) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    if (!repository.entityExists(entity)) {
      throw new ApiFailure(HttpStatus.NOT_FOUND, "ENTITY_UNAVAILABLE", "This entity is unavailable.");
    }
    return repository.get(entity).orElseGet(() -> GreenLineSettingsRepository.Settings.defaults(entity));
  }

  @Transactional
  public GreenLineSettingsRepository.Settings save(CurrentUser user, GreenLineSettingsRepository.Settings settings) {
    user.requireRole(Role.DIRECTOR);
    if (settings == null || settings.entityId() == null || settings.lateThresholdWorkingDays() < 1
        || settings.lateThresholdWorkingDays() > 30 || settings.backfillDays() < 0 || settings.backfillDays() > 365) {
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_WORKFLOW_SETTINGS", "Check the threshold and backfill window.");
    }
    UUID actor = actorId(user);
    Role role = repository.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Director access is required."));
    if (role != Role.DIRECTOR) throw new AccessDeniedException("Director access is required.");
    if (!repository.lockEntity(settings.entityId())) {
      throw new ApiFailure(HttpStatus.NOT_FOUND, "ENTITY_UNAVAILABLE", "This entity is unavailable.");
    }
    if (settings.defaultOwnerId() != null) {
      Role ownerRole = repository.findStaffRole(settings.defaultOwnerId()).orElse(null);
      if (ownerRole != Role.DIRECTOR && ownerRole != Role.FINANCIAL_ADMIN) {
        throw new ApiFailure(HttpStatus.CONFLICT, "DEFAULT_OWNER_UNAVAILABLE", "Choose an active Director or Financial Admin.");
      }
    }
    if (settings.serviceManagerId() != null && repository.findStaffRole(settings.serviceManagerId()).isEmpty()) {
      throw new ApiFailure(HttpStatus.CONFLICT, "SERVICE_MANAGER_UNAVAILABLE", "Choose an active staff member.");
    }
    var before = repository.get(settings.entityId()).orElse(GreenLineSettingsRepository.Settings.defaults(settings.entityId()));
    settings = new GreenLineSettingsRepository.Settings(settings.entityId(), settings.enabled(), settings.defaultOwnerId(),
        settings.serviceManagerId(), settings.lateThresholdWorkingDays(), settings.backfillDays(),
        settings.automaticSyncEnabled() == null ? before.automaticSyncEnabled() : settings.automaticSyncEnabled(),
        settings.automaticSyncIntervalMinutes() == null ? before.automaticSyncIntervalMinutes() : settings.automaticSyncIntervalMinutes());
    if (settings.automaticSyncIntervalMinutes() < 1 || settings.automaticSyncIntervalMinutes() > 1440)
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_WORKFLOW_SETTINGS", "Enter an interval from 1 to 1440 minutes.");
    if (!before.equals(settings)) {
      repository.save(settings, actor);
      repository.auditSettings(settings.entityId(), actor, before, settings);
      if (before.lateThresholdWorkingDays() != settings.lateThresholdWorkingDays()) {
        repository.recalculateLateDates(settings.entityId(), settings.lateThresholdWorkingDays());
      }
    }
    repository.fillUnowned(settings.entityId(), actor, settings.defaultOwnerId(), settings.serviceManagerId());
    return settings;
  }

  private static UUID actorId(CurrentUser user) {
    try { return UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Director access is required."); }
  }
}
