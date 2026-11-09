package com.airconcepts.dashboard.staff;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

/** Minimal assignment choice; contains no email or permission details. */
public record StaffOption(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {}
