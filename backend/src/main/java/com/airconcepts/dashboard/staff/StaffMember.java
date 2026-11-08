package com.airconcepts.dashboard.staff;

import com.airconcepts.dashboard.security.Role;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

/** Director-only user management view. Historical inactive staff are preserved. */
public record StaffMember(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}
