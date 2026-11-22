package com.airconcepts.dashboard.vwork;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.settings.GreenLineSettingsRepository;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VworkTemplatesService {
  private final VworkTemplatesRepository templates;
  private final GreenLineSettingsRepository settings;
  public VworkTemplatesService(VworkTemplatesRepository templates, GreenLineSettingsRepository settings) {
    this.templates = templates; this.settings = settings;
  }

  public List<VworkTemplatesRepository.Template> get(CurrentUser user, UUID entity) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    if (entity == null || !settings.entityExists(entity)) throw unavailable();
    return templates.read(entity);
  }

  @Transactional
  public List<VworkTemplatesRepository.Template> save(CurrentUser user, UUID entity, List<VworkTemplatesRepository.Template> desired) {
    user.requireRole(Role.DIRECTOR);
    validate(desired);
    UUID actor;
    try { actor = UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Director access is required."); }
    if (settings.lockActor(actor).orElse(null) != Role.DIRECTOR) throw new AccessDeniedException("Director access is required.");
    templates.lockAssignments();
    if (entity == null || !settings.lockEntity(entity)) throw unavailable();
    templates.replace(entity, actor, desired);
    return templates.read(entity);
  }

  private static void validate(List<VworkTemplatesRepository.Template> desired) {
    if (desired == null || desired.isEmpty() || desired.size() > 20) throw invalid();
    var refs = new HashSet<String>();
    int defaults = 0;
    for (var item : desired) {
      if (item == null || item.templateRef() == null || item.templateRef().isBlank()
          || item.templateRef().length() > 120 || !item.templateRef().equals(item.templateRef().strip())
          || item.templateRef().chars().anyMatch(Character::isISOControl) || !refs.add(item.templateRef())) throw invalid();
      if (item.isDefault()) defaults++;
    }
    if (defaults != 1) throw invalid();
  }
  private static ApiFailure invalid() { return new ApiFailure(HttpStatus.BAD_REQUEST, "VWORK_TEMPLATES_INVALID", "Enter 1 to 20 unique template references and choose one default."); }
  private static ApiFailure unavailable() { return new ApiFailure(HttpStatus.NOT_FOUND, "ENTITY_UNAVAILABLE", "This entity is unavailable."); }
}
