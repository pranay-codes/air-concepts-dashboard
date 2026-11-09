package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/** Current active staff identity; inactive and unregistered callers receive HTTP 403. */
public record MeResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String subject,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {
}
