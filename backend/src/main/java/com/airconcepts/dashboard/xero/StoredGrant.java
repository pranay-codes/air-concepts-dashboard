package com.airconcepts.dashboard.xero;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
public record StoredGrant(UUID id, String accessCiphertext, String refreshCiphertext, Instant expiresAt,
    GrantState state, Instant pendingSince, Instant retryAt, String errorCode, Set<String> grantedScopes) {
  public StoredGrant(UUID id, String accessCiphertext, String refreshCiphertext, Instant expiresAt,
      GrantState state, Instant pendingSince, Instant retryAt, String errorCode) {
    this(id, accessCiphertext, refreshCiphertext, expiresAt, state, pendingSince, retryAt, errorCode, null);
  }
  public StoredGrant { if (grantedScopes != null) grantedScopes = Set.copyOf(grantedScopes); }
  @Override public String toString() { return "StoredGrant[redacted]"; }
}
