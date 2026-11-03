package com.airconcepts.dashboard.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

/** Default until role storage exists: every user has no role, so role-checked actions are denied. */
@Component
class NoRoleLookup implements RoleLookup {

  @Override
  public Optional<Role> findRole(String subject) {
    return Optional.empty();
  }
}
