package com.airconcepts.dashboard.xero;
import java.util.UUID;
import java.util.Set;
/** Authorised server-only accounting request context. */
public record TenantAccess(UUID entityId, UUID tenantId, UUID grantId, String accessToken, Set<String> grantedScopes) {
  public TenantAccess(UUID entityId, UUID tenantId, UUID grantId, String accessToken) { this(entityId, tenantId, grantId, accessToken, null); }
  public TenantAccess { if (grantedScopes != null) grantedScopes = Set.copyOf(grantedScopes); }
  @Override public String toString() { return "TenantAccess[redacted]"; }
}
