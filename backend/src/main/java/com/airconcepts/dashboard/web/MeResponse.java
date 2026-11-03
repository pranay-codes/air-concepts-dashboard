package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.security.Role;

/** The caller's identity. The role is null when no role is assigned. */
public record MeResponse(String subject, Role role) {
}
