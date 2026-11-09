package com.airconcepts.dashboard.xero;
import java.util.UUID;
/** Authorised server-only accounting request context. */
public record TenantAccess(UUID entityId, UUID tenantId, UUID grantId, String accessToken) {
  @Override public String toString() { return "TenantAccess[redacted]"; }
}
