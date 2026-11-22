package com.airconcepts.dashboard.xero;
import java.util.UUID;
import java.util.Set;
/** Internal-only lease; never returned by a controller. */
public record XeroAccess(UUID grantId, String accessToken, Set<String> grantedScopes) {
  public XeroAccess(UUID grantId, String accessToken) { this(grantId, accessToken, null); }
  public XeroAccess { if (grantedScopes != null) grantedScopes = Set.copyOf(grantedScopes); }
  @Override public String toString() { return "XeroAccess[redacted]"; }
}
