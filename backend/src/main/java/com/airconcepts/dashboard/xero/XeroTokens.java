package com.airconcepts.dashboard.xero;

import java.time.Instant;
import java.util.Set;

/** Internal transport value; never serialize it as an application response. */
public record XeroTokens(String accessToken, String refreshToken, Instant expiresAt, Set<String> grantedScopes) {
  public XeroTokens(String accessToken, String refreshToken, Instant expiresAt) { this(accessToken, refreshToken, expiresAt, null); }
  public XeroTokens { if (grantedScopes != null) grantedScopes = Set.copyOf(grantedScopes); }
  @Override public String toString() { return "XeroTokens[redacted]"; }
}
