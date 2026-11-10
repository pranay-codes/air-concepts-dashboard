package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class XeroConnectionService {
  private final XeroConnectionRepository repository;
  private final XeroOAuthClient provider;
  private final XeroGrantService grants;
  private final EntityService entities;
  private final TokenCipher cipher;
  private final XeroSettings settings;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public XeroConnectionService(XeroConnectionRepository repository, XeroOAuthClient provider, XeroGrantService grants,
      EntityService entities, TokenCipher cipher, XeroSettings settings, Clock clock) {
    this.repository = repository; this.provider = provider; this.grants = grants; this.entities = entities;
    this.cipher = cipher; this.settings = settings; this.clock = clock;
  }

  public ConnectionViews.Start start(CurrentUser user, UUID entity) {
    user.requireRole(Role.DIRECTOR);
    entities.get(user, entity);
    settings.requireConfigured(); cipher.requireConfigured();
    byte[] bytes = new byte[32]; random.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    UUID id = UUID.randomUUID(); Instant expires = clock.instant().plusSeconds(600);
    repository.createAttempt(id, UUID.fromString(user.subject()), entity, hash(state), expires);
    return new ConnectionViews.Start(id, settings.authorizationUrl(state), expires);
  }

  /** Public callback is authorised only by a live single-use state bound to an active Director. */
  public UUID callback(String state, String code, String error) {
    if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) throw invalidState();
    var attempt = repository.claimAttempt(hash(state), clock.instant()).orElseThrow(XeroConnectionService::invalidState);
    if (error != null && !error.isBlank()) {
      String failure = "access_denied".equals(error) ? "XERO_CONSENT_DENIED"
          : Set.of("invalid_client", "unauthorized_client", "invalid_scope").contains(error)
              ? "XERO_CONFIGURATION_ERROR" : "XERO_AUTHORIZATION_FAILED";
      repository.failAttempt(attempt.id(), failure, null); return attempt.id();
    }
    if (code == null || code.isBlank() || code.length() > 4096) {
      repository.failAttempt(attempt.id(), "XERO_CALLBACK_INVALID", null); return attempt.id();
    }
    try {
      var tokens = provider.exchange(code);
      UUID id = UUID.randomUUID();
      repository.saveGrant(attempt, new StoredGrant(id, cipher.encrypt(id, "access", tokens.accessToken()),
          cipher.encrypt(id, "refresh", tokens.refreshToken()), tokens.expiresAt(), GrantState.ACTIVE, null, null, null), clock.instant());
    } catch (ApiFailure failure) {
      repository.failAttempt(attempt.id(), failure.code(), failure instanceof ProviderFailure p ? p.upstreamStatus() : null);
    }
    return attempt.id();
  }

  public ConnectionViews.Attempt attempt(CurrentUser user, UUID id) {
    var attempt = owned(user, id);
    if (attempt.state() != OAuthAttempt.State.COMPLETED && attempt.state() != OAuthAttempt.State.FAILED
        && !attempt.expiresAt().isAfter(clock.instant())) {
      return new ConnectionViews.Attempt(id, attempt.entityId(), OAuthAttempt.State.FAILED, "XERO_AUTH_EXPIRED", null, List.of());
    }
    var choices = attempt.state() == OAuthAttempt.State.READY ? discover(attempt.grantId()).stream()
        .map(t -> new ConnectionViews.Choice(t.connectionId(), t.name())).toList() : List.<ConnectionViews.Choice>of();
    return new ConnectionViews.Attempt(id, attempt.entityId(), attempt.state(), attempt.errorCode(), attempt.upstreamStatus(), choices);
  }

  public ConnectionViews.Connection confirm(CurrentUser user, UUID id, UUID connectionId) {
    var attempt = owned(user, id);
    if (attempt.state() != OAuthAttempt.State.READY || !attempt.expiresAt().isAfter(clock.instant())) {
      throw new ApiFailure(HttpStatus.CONFLICT, "XERO_AUTH_EXPIRED", "This authorisation attempt changed or expired. Connect Xero again.");
    }
    var authorised = discover(attempt.grantId());
    var selected = authorised.stream().filter(t -> t.connectionId().equals(connectionId)).findFirst()
        .orElseThrow(() -> new ApiFailure(HttpStatus.BAD_REQUEST, "XERO_TENANT_NOT_AUTHORISED", "Choose a currently authorised Xero organisation."));
    repository.completeMapping(attempt, selected, authorised, clock.instant());
    return status(user, attempt.entityId());
  }

  public ConnectionViews.Connection status(CurrentUser user, UUID entity) {
    entities.get(user, entity);
    return repository.connection(entity).map(c -> new ConnectionViews.Connection(entity,
        c.grantState() == GrantState.NEEDS_RECONNECTION ? ConnectionViews.Status.NEEDS_RECONNECTION : ConnectionViews.Status.CONNECTED,
        c.tenantName(), ConnectionViews.maskTenant(c.tenantId()), c.connectedBy(), c.verifiedAt(), c.lastCheckedAt(), c.errorCode()))
        .orElseGet(() -> new ConnectionViews.Connection(entity, ConnectionViews.Status.NOT_CONNECTED,
            null, null, null, null, null, null));
  }

  public TenantAccess authorisedAccess(CurrentUser user, UUID entity) {
    entities.get(user, entity);
    return connectionAccess(entity);
  }

  /** Scheduled internal forecast work only. No web/controller code may call this method. */
  public TenantAccess systemAccess(UUID entity) {
    if (!repository.activeEntity(entity)) throw new ApiFailure(HttpStatus.NOT_FOUND, "ENTITY_UNAVAILABLE", "Entity is unavailable.");
    return connectionAccess(entity);
  }

  private TenantAccess connectionAccess(UUID entity) {
    var connection = repository.connection(entity).orElseThrow(() -> new ApiFailure(HttpStatus.CONFLICT,
        "XERO_NOT_CONNECTED", "Ask a Director to connect this entity to Xero in Settings."));
    var access = grants.access(connection.grantId());
    return new TenantAccess(entity, connection.tenantId(), connection.grantId(), access.accessToken());
  }

  private List<XeroTenant> discover(UUID grant) {
    var access = grants.access(grant);
    Instant started = clock.instant();
    try {
      var result = provider.connections(access.accessToken());
      grants.recordReadSuccess(grant, started);
      return result;
    }
    catch (XeroProviderFailure failure) { grants.recordReadFailure(grant, failure); throw failure; }
  }

  private OAuthAttempt owned(CurrentUser user, UUID id) {
    user.requireRole(Role.DIRECTOR);
    return repository.ownedAttempt(id, UUID.fromString(user.subject())).orElseThrow(() ->
        new ApiFailure(HttpStatus.NOT_FOUND, "XERO_ATTEMPT_UNAVAILABLE", "This authorisation attempt is unavailable."));
  }
  private static String hash(String state) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.US_ASCII))); }
    catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("Secure state hashing is unavailable."); }
  }
  private static ApiFailure invalidState() {
    return new ApiFailure(HttpStatus.BAD_REQUEST, "XERO_STATE_INVALID", "This authorisation callback is invalid, expired or already used. Connect Xero again.");
  }
}
