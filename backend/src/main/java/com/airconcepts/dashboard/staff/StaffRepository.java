package com.airconcepts.dashboard.staff;

import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StaffRepository {
  private static final RowMapper<StaffMember> ROW = (row, index) -> new StaffMember(
      row.getObject("id", UUID.class), row.getString("email"), row.getString("display_name"),
      Role.valueOf(row.getString("role")), row.getBoolean("active"));
  private final JdbcTemplate jdbc;

  public StaffRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public List<StaffMember> findAll() {
    return jdbc.query("select * from public.staff_users order by lower(display_name), id", ROW);
  }

  public Optional<StaffMember> findById(UUID id) {
    return jdbc.query("select * from public.staff_users where id = ?", ROW, id).stream().findFirst();
  }

  public Optional<StaffMember> findByEmail(String email) {
    return jdbc.query("select * from public.staff_users where lower(email) = ?", ROW, email).stream().findFirst();
  }

  public List<StaffOption> activeChoices() {
    return jdbc.query("select id, display_name from public.staff_users where active = true order by lower(display_name), id",
        (row, index) -> new StaffOption(row.getObject("id", UUID.class), row.getString("display_name")));
  }

  @Transactional
  public StaffProvision reserve(String email, String displayName, Role role) {
    var existing = findByEmail(email);
    if (existing.isPresent()) {
      var user = existing.get();
      if (!user.active() || !user.displayName().equals(displayName) || user.role() != role) {
        throw conflict("This email already belongs to a dashboard user. Change that user through Settings.");
      }
      return new StaffProvision(user.id(), email, displayName, role);
    }
    jdbc.update("insert into public.staff_provisions(email, display_name, role) values (?, ?, ?) on conflict(email) do nothing",
        email, displayName, role.name());
    var provision = jdbc.queryForObject("select * from public.staff_provisions where email = ?",
        (row, index) -> new StaffProvision(row.getObject("id", UUID.class), row.getString("email"),
            row.getString("display_name"), Role.valueOf(row.getString("role"))), email);
    if (provision == null || !provision.displayName().equals(displayName) || provision.role() != role) {
      throw conflict("A pending user request for this email has different details. Retry the original request.");
    }
    return provision;
  }

  @Transactional
  public StaffMember complete(StaffProvision provision) {
    // Serializes completion with deactivation, so a stale retry cannot reactivate someone.
    lockAdministration();
    jdbc.update("insert into public.staff_users(id, email, display_name, role) values (?, ?, ?, ?) on conflict(id) do nothing",
        provision.id(), provision.email(), provision.displayName(), provision.role().name());
    var user = findById(provision.id()).orElseThrow(() -> conflict("User provisioning is incomplete. Retry."));
    if (!user.active() || !user.email().equalsIgnoreCase(provision.email())
        || !user.displayName().equals(provision.displayName()) || user.role() != provision.role()) {
      throw conflict("The user changed while the request was in progress. Refresh Settings.");
    }
    return user;
  }

  @Transactional
  public StaffMember update(UUID actor, UUID id, String displayName, Role role, boolean active) {
    lockAdministration();
    var currentActor = findById(actor);
    if (currentActor.isEmpty() || !currentActor.get().active() || currentActor.get().role() != Role.DIRECTOR) {
      throw new org.springframework.security.access.AccessDeniedException("Director access is required.");
    }
    var previous = findById(id).orElseThrow(() -> new ApiFailure(HttpStatus.NOT_FOUND,
        "USER_UNAVAILABLE", "This dashboard user is unavailable."));
    if (previous.active() && previous.role() == Role.DIRECTOR && (!active || role != Role.DIRECTOR)) {
      Integer directors = jdbc.queryForObject("select count(*) from public.staff_users where active = true and role = 'DIRECTOR'", Integer.class);
      if (directors == null || directors <= 1) {
        throw conflict("Keep at least one active Director. Add another Director before changing this account.");
      }
    }
    jdbc.update("update public.staff_users set display_name = ?, role = ?, active = ?, updated_at = now() where id = ?",
        displayName, role.name(), active, id);
    return new StaffMember(id, previous.email(), displayName, role, active);
  }

  private void lockAdministration() {
    jdbc.execute("select pg_advisory_xact_lock(150016)");
  }

  private static ApiFailure conflict(String detail) {
    return new ApiFailure(HttpStatus.CONFLICT, "USER_CONFLICT", detail);
  }
}
