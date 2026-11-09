package com.airconcepts.dashboard.xero;
import java.time.Instant;
import java.util.UUID;
public record StoredConnection(UUID entityId, UUID grantId, UUID tenantId, String tenantName,
    Instant verifiedAt, GrantState grantState, String errorCode) {}
