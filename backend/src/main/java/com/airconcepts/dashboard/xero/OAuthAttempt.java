package com.airconcepts.dashboard.xero;
import java.time.Instant;
import java.util.UUID;
public record OAuthAttempt(UUID id, UUID directorId, UUID entityId, UUID grantId, Instant expiresAt,
    State state, String errorCode, Integer upstreamStatus) {
  public enum State { AWAITING_CALLBACK, PROCESSING, READY, FAILED, COMPLETED }
}
