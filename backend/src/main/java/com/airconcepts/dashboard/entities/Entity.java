package com.airconcepts.dashboard.entities;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

/** Stable dashboard identity; Xero tenant IDs are deliberately absent from public entity data. */
public record Entity(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {}
