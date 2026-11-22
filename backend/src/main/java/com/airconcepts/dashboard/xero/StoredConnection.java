package com.airconcepts.dashboard.xero;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
public record StoredConnection(UUID entityId, UUID grantId, UUID tenantId, String tenantName,
    Instant verifiedAt, String connectedBy, Instant lastCheckedAt, GrantState grantState, String errorCode, Set<String> grantedScopes) {
  public StoredConnection(UUID entityId, UUID grantId, UUID tenantId, String tenantName,
      Instant verifiedAt, String connectedBy, Instant lastCheckedAt, GrantState grantState, String errorCode) {
    this(entityId, grantId, tenantId, tenantName, verifiedAt, connectedBy, lastCheckedAt, grantState, errorCode, null);
  }
  public StoredConnection { if (grantedScopes != null) grantedScopes = Set.copyOf(grantedScopes); }
}
