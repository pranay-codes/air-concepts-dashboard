package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ApiFailure;
import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One row lock per shared grant, held until rotation and its encrypted values are committed. */
@Service
public class XeroGrantService {
  private final XeroConnectionRepository repository;
  private final XeroOAuthClient provider;
  private final TokenCipher cipher;
  private final Clock clock;
  public XeroGrantService(XeroConnectionRepository repository, XeroOAuthClient provider, TokenCipher cipher, Clock clock) {
    this.repository = repository; this.provider = provider; this.cipher = cipher; this.clock = clock;
  }

  @Transactional(noRollbackFor = XeroProviderFailure.class)
  public XeroAccess access(UUID id) {
    var grant = repository.lockGrant(id);
    Instant now = clock.instant();
    if (grant.state() == GrantState.NEEDS_RECONNECTION) throw new ApiFailure(HttpStatus.CONFLICT,
        "XERO_RECONNECT_REQUIRED", "Ask a Director to reconnect this Xero organisation.");
    if (grant.retryAt() != null && grant.retryAt().isAfter(now)) {
      int wait = Math.toIntExact(Math.min(Integer.MAX_VALUE, Duration.between(now, grant.retryAt()).getSeconds() + 1));
      throw new XeroProviderFailure("XERO_RATE_LIMITED", "Wait before retrying Xero.", 429, false, false, wait);
    }
    if (grant.state() == GrantState.ACTIVE && grant.expiresAt().isAfter(now.plusSeconds(60))) {
      return new XeroAccess(id, cipher.decrypt(id, "access", grant.accessCiphertext()), grant.grantedScopes());
    }
    if (grant.pendingSince() != null && !grant.pendingSince().plusSeconds(1800).isAfter(now)) {
      repository.recordGrantFailure(id, GrantState.NEEDS_RECONNECTION, "XERO_RECONNECT_REQUIRED", null, null);
      throw new XeroProviderFailure("XERO_RECONNECT_REQUIRED", "The refresh recovery period expired. Reconnect Xero.", 401, true, false, null);
    }
    try {
      var rotated = provider.refresh(cipher.decrypt(id, "refresh", grant.refreshCiphertext()));
      repository.saveRotated(new StoredGrant(id, cipher.encrypt(id, "access", rotated.accessToken()),
          cipher.encrypt(id, "refresh", rotated.refreshToken()), rotated.expiresAt(), GrantState.ACTIVE, null, null, null,
          rotated.grantedScopes()));
      return new XeroAccess(id, rotated.accessToken(), rotated.grantedScopes());
    } catch (XeroProviderFailure failure) {
      GrantState state = failure.requiresReconnect() ? GrantState.NEEDS_RECONNECTION
          : failure.uncertain() ? GrantState.REFRESH_PENDING : grant.state();
      Instant pending = state == GrantState.REFRESH_PENDING ? (grant.pendingSince() == null ? now : grant.pendingSince()) : null;
      Instant retry = failure.retryAfterSeconds() == null ? null : now.plusSeconds(failure.retryAfterSeconds());
      repository.recordGrantFailure(id, state, failure.code(), pending, retry);
      throw failure;
    }
  }

  @Transactional
  public void recordReadFailure(UUID id, XeroProviderFailure failure) {
    var grant = repository.lockGrant(id);
    GrantState state = failure.requiresReconnect() ? GrantState.NEEDS_RECONNECTION : grant.state();
    Instant retry = failure.retryAfterSeconds() == null ? null : clock.instant().plusSeconds(failure.retryAfterSeconds());
    repository.recordGrantFailure(id, state, failure.code(), state == GrantState.REFRESH_PENDING ? grant.pendingSince() : null, retry);
  }

  public void recordReadSuccess(UUID id, Instant requestStarted) {
    repository.clearReadFailure(id, requestStarted, clock.instant());
  }
}
