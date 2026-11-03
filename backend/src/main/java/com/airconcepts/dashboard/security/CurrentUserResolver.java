package com.airconcepts.dashboard.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Builds the {@link CurrentUser} for a verified JWT. Only the subject comes from the token. */
@Component
public class CurrentUserResolver {

  private final RoleLookup roleLookup;

  public CurrentUserResolver(RoleLookup roleLookup) {
    this.roleLookup = roleLookup;
  }

  public CurrentUser from(Jwt jwt) {
    String subject = jwt.getSubject();
    Role role = roleLookup.findRole(subject).orElse(null);
    return new CurrentUser(subject, role);
  }
}
