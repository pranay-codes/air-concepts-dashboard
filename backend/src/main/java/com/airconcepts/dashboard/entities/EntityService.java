package com.airconcepts.dashboard.entities;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EntityService {
  private final EntityRepository entities;

  public EntityService(EntityRepository entities) {
    this.entities = entities;
  }

  public List<Entity> list(CurrentUser user) {
    requireActive(user);
    return entities.findAll();
  }

  public Entity get(CurrentUser user, UUID id) {
    requireActive(user);
    return entities.findById(id).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity is unavailable"));
  }

  public Entity create(CurrentUser user, String displayName) {
    user.requireRole(Role.DIRECTOR);
    if (displayName == null || displayName.isBlank() || displayName.strip().length() > 160
        || displayName.chars().anyMatch(Character::isISOControl)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter an entity name of 1 to 160 characters");
    }
    return entities.create(displayName.strip());
  }

  private static void requireActive(CurrentUser user) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
  }
}
