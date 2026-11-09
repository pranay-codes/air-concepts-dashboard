package com.airconcepts.dashboard.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Current database role; deactivation takes effect even for an already-issued JWT. */
@Component
public class JdbcRoleLookup implements RoleLookup {
  private final JdbcTemplate jdbc;

  public JdbcRoleLookup(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Role> findRole(String subject) {
    if (subject == null) return Optional.empty();
    final UUID id;
    try {
      id = UUID.fromString(subject);
    } catch (IllegalArgumentException invalidSubject) {
      return Optional.empty();
    }
    // No cache: neither a JWT role claim nor a previous request can authorise a revoked user.
    return jdbc.query("select role from public.staff_users where id = ? and active = true",
        (row, index) -> Role.valueOf(row.getString("role")), id).stream().findFirst();
  }
}
