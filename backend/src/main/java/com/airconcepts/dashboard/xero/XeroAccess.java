package com.airconcepts.dashboard.xero;
import java.util.UUID;
/** Internal-only lease; never returned by a controller. */
public record XeroAccess(UUID grantId, String accessToken) {
  @Override public String toString() { return "XeroAccess[redacted]"; }
}
