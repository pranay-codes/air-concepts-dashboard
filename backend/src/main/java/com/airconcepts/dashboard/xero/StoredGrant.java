package com.airconcepts.dashboard.xero;
import java.time.Instant;
import java.util.UUID;
public record StoredGrant(UUID id, String accessCiphertext, String refreshCiphertext, Instant expiresAt,
    GrantState state, Instant pendingSince, Instant retryAt, String errorCode) {
  @Override public String toString() { return "StoredGrant[redacted]"; }
}
