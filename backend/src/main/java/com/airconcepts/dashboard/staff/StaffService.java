package com.airconcepts.dashboard.staff;

import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Orchestrates local transactions and Auth calls without rolling back access revocation. */
@Service
public class StaffService {
  private final StaffRepository users;
  private final SupabaseAdminClient auth;

  public StaffService(StaffRepository users, SupabaseAdminClient auth) {
    this.users = users;
    this.auth = auth;
  }

  public List<StaffMember> list(CurrentUser caller) {
    caller.requireRole(Role.DIRECTOR);
    return users.findAll();
  }

  public List<StaffOption> choices(CurrentUser caller) {
    caller.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN);
    return users.activeChoices();
  }

  public StaffMember get(CurrentUser caller, UUID id) {
    caller.requireRole(Role.DIRECTOR);
    return users.findById(id).orElseThrow(() ->
        new ApiFailure(HttpStatus.NOT_FOUND, "USER_UNAVAILABLE", "This dashboard user is unavailable."));
  }

  public StaffMember create(CurrentUser caller, String email, String displayName, Role role) {
    caller.requireRole(Role.DIRECTOR);
    String normalizedEmail = normalizeEmail(email);
    String name = validateName(displayName);
    requireRoleValue(role);
    StaffProvision provision = users.reserve(normalizedEmail, name, role);
    auth.ensureUser(provision.id(), provision.email());
    return users.complete(provision);
  }

  public StaffMember update(CurrentUser caller, UUID id, String displayName, Role role, boolean active) {
    caller.requireRole(Role.DIRECTOR);
    String name = validateName(displayName);
    requireRoleValue(role);
    // Fail before any external request if the UUID is not a dashboard user.
    if (users.findById(id).isEmpty()) {
      throw new ApiFailure(HttpStatus.NOT_FOUND, "USER_UNAVAILABLE", "This dashboard user is unavailable.");
    }
    if (active) auth.setActive(id, true);
    users.update(UUID.fromString(caller.subject()), id, name, role, active);
    if (!active) auth.setActive(id, false);
    return reconcileAuthState(id, active);
  }

  private StaffMember reconcileAuthState(UUID id, boolean appliedActive) {
    var current = currentAccount(id);
    if (current.active() == appliedActive) return current;
    // An older ban/unban may finish after a newer opposite change. Restore the latest
    // committed intent, bound attempts under contention, and report the conflict to the caller.
    for (int attempt = 0; attempt < 3; attempt++) {
      boolean desiredActive = current.active();
      auth.setActive(id, desiredActive);
      current = currentAccount(id);
      if (current.active() == desiredActive) break;
    }
    throw new ApiFailure(HttpStatus.CONFLICT, "USER_CHANGED",
        "This account changed during the request. Refresh Settings and check its current state before retrying.");
  }

  private StaffMember currentAccount(UUID id) {
    return users.findById(id).orElseThrow(() ->
        new ApiFailure(HttpStatus.NOT_FOUND, "USER_UNAVAILABLE", "This dashboard user is unavailable."));
  }

  public static String normalizeEmail(String email) {
    if (email == null) throw invalid("Enter a valid email address.");
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    if (normalized.length() > 254 || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || normalized.chars().anyMatch(Character::isISOControl)) {
      throw invalid("Enter a valid email address.");
    }
    return normalized;
  }

  private static String validateName(String name) {
    if (name == null || name.isBlank() || name.strip().length() > 160
        || name.chars().anyMatch(Character::isISOControl)) {
      throw invalid("Enter a name of 1 to 160 characters.");
    }
    return name.strip();
  }

  private static void requireRoleValue(Role role) {
    if (role == null) throw invalid("Choose a dashboard role.");
  }

  private static ApiFailure invalid(String detail) {
    return new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_USER", detail);
  }
}
