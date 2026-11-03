package com.airconcepts.dashboard.security;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;

/** The authenticated caller. The role comes from the server-side lookup, so it may be null. */
public record CurrentUser(String subject, Role role) {

  /** Throws AccessDeniedException (HTTP 403) unless the user holds one of the allowed roles. */
  public void requireRole(Role... allowed) {
    if (role == null || !List.of(allowed).contains(role)) {
      throw new AccessDeniedException("Your role is not allowed to perform this action.");
    }
  }
}
