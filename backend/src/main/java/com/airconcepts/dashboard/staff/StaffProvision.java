package com.airconcepts.dashboard.staff;

import com.airconcepts.dashboard.security.Role;
import java.util.UUID;

/** Durable idempotency identity for an Auth create operation, not an active staff account. */
public record StaffProvision(UUID id, String email, String displayName, Role role) {}
