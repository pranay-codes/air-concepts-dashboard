package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.entities.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class XeroConnectionServiceTest {
  private final UUID directorId = UUID.randomUUID(), entity = UUID.randomUUID(), attemptId = UUID.randomUUID(), grant = UUID.randomUUID();
  private final CurrentUser director = new CurrentUser(directorId.toString(), Role.DIRECTOR);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);
  private final TokenCipher cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
  private final EntityService entities = mock(EntityService.class);
  private final XeroConnectionRepository repository = mock(XeroConnectionRepository.class);
  private final XeroOAuthClient provider = mock(XeroOAuthClient.class);
  private final XeroGrantService grants = mock(XeroGrantService.class);
  private final XeroSettings settings = new XeroSettings("client", "synthetic-secret",
      "https://api.example.invalid/api/v1/xero/callback", "https://app.example.invalid/settings/entities");
  private final XeroConnectionService service = new XeroConnectionService(repository, provider, grants, entities, cipher, settings, clock);

  private OAuthAttempt attempt(OAuthAttempt.State state) {
    return new OAuthAttempt(attemptId, directorId, entity, state == OAuthAttempt.State.READY ? grant : null,
        clock.instant().plusSeconds(600), state, null, null);
  }

  @Test
  void frUc15_01_nonDirectorsCannotStartInspectOrConfirmAuthorisation() {
    for (Role role : List.of(Role.FINANCIAL_ADMIN, Role.STANDARD_USER)) {
      var user = new CurrentUser(UUID.randomUUID().toString(), role);
      assertThatThrownBy(() -> service.start(user, entity)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> service.attempt(user, attemptId)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> service.confirm(user, attemptId, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }
    verifyNoInteractions(repository, provider, grants, entities);
  }

  @Test
  void frUc15_10_startBindsRandomHashedStateToDirectorAndEntityWithTenMinuteExpiry() {
    var start = service.start(director, entity);
    assertThat(start.authorizationUrl()).contains("state=");
    assertThat(start.expiresAt()).isEqualTo(clock.instant().plusSeconds(600));
    verify(entities).get(director, entity);
    verify(repository).createAttempt(eq(start.attemptId()), eq(directorId), eq(entity), matches("[a-f0-9]{64}"), eq(start.expiresAt()));
  }

  @Test
  void frUc15_10_callbackClaimsStateOnceAndEncryptsBothTokensBeforeReady() {
    when(repository.claimAttempt(anyString(), eq(clock.instant()))).thenReturn(Optional.of(attempt(OAuthAttempt.State.PROCESSING)), Optional.empty());
    when(provider.exchange("synthetic-code")).thenReturn(new XeroTokens("synthetic-access", "synthetic-refresh", clock.instant().plusSeconds(1800)));
    String state = "a".repeat(43);
    assertThat(service.callback(state, "synthetic-code", null)).isEqualTo(attemptId);
    assertThatThrownBy(() -> service.callback(state, "synthetic-code", null)).isInstanceOf(ApiFailure.class);
    var captured = org.mockito.ArgumentCaptor.forClass(StoredGrant.class);
    verify(repository).saveGrant(eq(attempt(OAuthAttempt.State.PROCESSING)), captured.capture(), eq(clock.instant()));
    assertThat(cipher.decrypt(captured.getValue().id(), "refresh", captured.getValue().refreshCiphertext())).isEqualTo("synthetic-refresh");
    verify(provider, times(1)).exchange(anyString());
  }

  @Test
  void frUc15_10_deniedConsentIsTerminalWithoutExchangingOrExposingProviderError() {
    when(repository.claimAttempt(anyString(), any())).thenReturn(Optional.of(attempt(OAuthAttempt.State.PROCESSING)));
    assertThat(service.callback("a".repeat(43), null, "access_denied")).isEqualTo(attemptId);
    verify(repository).failAttempt(attemptId, "XERO_CONSENT_DENIED", null);
    verifyNoInteractions(provider, grants);
  }

  @Test
  void frUc15_14_unknownAuthorisationErrorIsSanitisedWithoutCallingItUserDenial() {
    when(repository.claimAttempt(anyString(), any())).thenReturn(Optional.of(attempt(OAuthAttempt.State.PROCESSING)));
    service.callback("a".repeat(43), null, "untrusted-provider-message");
    verify(repository).failAttempt(attemptId, "XERO_AUTHORIZATION_FAILED", null);
    verifyNoInteractions(provider, grants);
  }

  @Test
  void frUc15_03_confirmationAcceptsOnlyAFreshlyDiscoveredConnectionId() {
    when(repository.ownedAttempt(attemptId, directorId)).thenReturn(Optional.of(attempt(OAuthAttempt.State.READY)));
    when(grants.access(grant)).thenReturn(new XeroAccess(grant, "synthetic-access"));
    var tenant = new XeroTenant(UUID.randomUUID(), UUID.randomUUID(), "Additional business");
    when(provider.connections("synthetic-access")).thenReturn(List.of(tenant));
    assertThatThrownBy(() -> service.confirm(director, attemptId, UUID.randomUUID())).isInstanceOf(ApiFailure.class);
    verify(repository, never()).completeMapping(any(), any(), anyList(), any());
    service.confirm(director, attemptId, tenant.connectionId());
    verify(repository).completeMapping(attempt(OAuthAttempt.State.READY), tenant, List.of(tenant), clock.instant());
  }

  @Test
  void frUc15_01_anotherDirectorsAttemptNeverReachesTheProvider() {
    when(repository.ownedAttempt(attemptId, directorId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.attempt(director, attemptId)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(provider, grants);
  }

  @Test
  void frUc15_14_successfulDiscoveryClearsOnlyFailuresOlderThanThatRead() {
    when(repository.ownedAttempt(attemptId, directorId)).thenReturn(Optional.of(attempt(OAuthAttempt.State.READY)));
    when(grants.access(grant)).thenReturn(new XeroAccess(grant, "synthetic-access"));
    when(provider.connections("synthetic-access")).thenReturn(List.of());
    service.attempt(director, attemptId);
    verify(grants).recordReadSuccess(grant, clock.instant());
  }
}
