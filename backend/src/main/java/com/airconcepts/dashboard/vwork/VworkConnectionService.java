package com.airconcepts.dashboard.vwork;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

/** Verification is deliberately outside the transactional repository write. */
public class VworkConnectionService {
  public record View(String status, UUID configuredBy, Instant configuredAt, Instant verifiedAt, String errorCode) {}
  /** Internal only; never serialize this record or expose the provider or credential. */
  public record Access(String revision, VworkCredential credential, VworkClient provider, boolean searchVerified) {
    @Override public String toString() { return "VworkAccess[redacted]"; }
  }
  private final VworkConnectionRepository repository;
  private final Supplier<VworkClient> client;
  private final VworkSecretCipher cipher;
  private final Clock clock;
  public VworkConnectionService(VworkConnectionRepository repository, Supplier<VworkClient> client,
      VworkSecretCipher cipher, Clock clock) {
    this.repository = repository; this.client = client; this.cipher = cipher; this.clock = clock;
  }

  public View get(CurrentUser user) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    return repository.read().map(row -> view(row, user.role() == Role.DIRECTOR))
        .orElse(new View("DISCONNECTED", null, null, null, null));
  }

  /** Internal sync only. A later mirror transaction locks and rechecks this revision. */
  public Access systemAccess() {
    var row = repository.read().filter(value -> "ACTIVE".equals(value.status()) && value.ciphertext() != null)
        .orElseThrow(() -> new ApiFailure(HttpStatus.CONFLICT, "VWORK_NOT_CONNECTED", "Ask a Director to check the vWork connection."));
    var provider = client.get();
    if (provider == null) throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "VWORK_NOT_CONFIGURED", "The vWork integration is not configured.");
    return new Access(row.revision(), cipher.decrypt(row.ciphertext()), provider, provider instanceof MockVworkClient);
  }

  public void connect(CurrentUser user, String value) {
    user.requireRole(Role.DIRECTOR);
    UUID actor = actor(user);
    VworkCredential credential;
    try { credential = new VworkCredential(value); }
    catch (IllegalArgumentException invalid) { throw new ApiFailure(HttpStatus.BAD_REQUEST, "VWORK_KEY_INVALID", "Enter a valid vWork API key."); }
    cipher.requireConfigured();
    VworkClient provider = client.get();
    if (provider == null) throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "VWORK_NOT_CONFIGURED", "The vWork integration is not configured.");
    String revision = repository.read().map(VworkConnectionRepository.Stored::revision).orElse(null);
    Instant now = clock.instant();
    String status = "ACTIVE", error = null;
    Instant verified = now, retry = null;
    try { provider.jobs(credential, 1); }
    catch (VworkFailure failure) {
      int code = failure.upstreamStatus();
      if (code == 401 || code == 403) throw new ApiFailure(HttpStatus.BAD_REQUEST, "VWORK_KEY_REJECTED", "vWork rejected the API key.");
      if (code != 0 && code != 429 && (code < 500 || code > 599))
        throw new ApiFailure(HttpStatus.BAD_GATEWAY, "VWORK_VERIFICATION_FAILED", "The vWork connection could not be verified.");
      status = "UNVERIFIED"; verified = null;
      error = code == 429 ? "VWORK_RATE_LIMITED" : "VWORK_UNAVAILABLE";
      if (code == 429) retry = now.plusSeconds(Math.max(1, Math.min(3600, failure.retryAfterSeconds() == null ? 60 : failure.retryAfterSeconds())));
    }
    repository.change(actor, revision, new VworkConnectionRepository.Change(cipher.encrypt(credential), status, error, verified, retry));
  }

  public void disconnect(CurrentUser user) {
    user.requireRole(Role.DIRECTOR);
    UUID actor = actor(user);
    String revision = repository.read().map(VworkConnectionRepository.Stored::revision).orElse(null);
    repository.change(actor, revision, new VworkConnectionRepository.Change(null, "DISCONNECTED", null, null, null));
  }

  private static View view(VworkConnectionRepository.Stored row, boolean director) {
    return new View(row.status(), director ? row.configuredBy() : null, director ? row.configuredAt() : null,
        director ? row.verifiedAt() : null, director ? row.errorCode() : null);
  }
  private static UUID actor(CurrentUser user) {
    try { return UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Director access is required."); }
  }
}
