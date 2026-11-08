package com.airconcepts.dashboard.xero;

import java.time.Instant;

/** Internal transport value; never serialize it as an application response. */
public record XeroTokens(String accessToken, String refreshToken, Instant expiresAt) {
  @Override public String toString() { return "XeroTokens[redacted]"; }
}
