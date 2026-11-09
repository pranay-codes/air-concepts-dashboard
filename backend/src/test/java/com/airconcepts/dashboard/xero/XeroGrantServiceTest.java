package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class XeroGrantServiceTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);
  private final UUID id = UUID.randomUUID();
  private final TokenCipher cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
  private final XeroConnectionRepository repository = mock(XeroConnectionRepository.class);
  private final XeroOAuthClient provider = mock(XeroOAuthClient.class);
  private final XeroGrantService service = new XeroGrantService(repository, provider, cipher, clock);

  private StoredGrant expired(GrantState state, Instant pending, Instant retry) {
    return new StoredGrant(id, cipher.encrypt(id, "access", "previous-synthetic-access"),
        cipher.encrypt(id, "refresh", "previous-synthetic-refresh"), clock.instant().minusSeconds(1), state, pending, retry, null);
  }

  @Test
  void frUc15_10_sharedGrantRotatesOnceAndPersistsBothEncryptedValuesBeforeReuse() {
    var stored = new AtomicReference<>(expired(GrantState.ACTIVE, null, null));
    when(repository.lockGrant(id)).thenAnswer(i -> stored.get());
    doAnswer(i -> { stored.set(i.getArgument(0)); return null; }).when(repository).saveRotated(any());
    when(provider.refresh("previous-synthetic-refresh"))
        .thenReturn(new XeroTokens("next-synthetic-access", "next-synthetic-refresh", clock.instant().plusSeconds(1800)));
    assertThat(service.access(id).accessToken()).isEqualTo("next-synthetic-access");
    assertThat(service.access(id).accessToken()).isEqualTo("next-synthetic-access");
    verify(provider, times(1)).refresh(anyString());
    assertThat(cipher.decrypt(id, "refresh", stored.get().refreshCiphertext())).isEqualTo("next-synthetic-refresh");
    assertThat(stored.get().toString()).doesNotContain("synthetic");
  }

  @Test
  void frUc15_10_revokedConsentMarksEveryMappingOfTheGrantForReconnection() {
    when(repository.lockGrant(id)).thenReturn(expired(GrantState.ACTIVE, null, null));
    var failure = new XeroProviderFailure("XERO_RECONNECT_REQUIRED", "Reconnect Xero", 400, true, false, null);
    when(provider.refresh(anyString())).thenThrow(failure);
    assertThatThrownBy(() -> service.access(id)).isSameAs(failure);
    verify(repository).recordGrantFailure(id, GrantState.NEEDS_RECONNECTION, failure.code(), null, null);
    verify(repository, never()).saveRotated(any());
  }

  @Test
  void frUc15_10_uncertainRefreshPreservesPriorTokenAndRecordsPendingInsteadOfSuccess() {
    when(repository.lockGrant(id)).thenReturn(expired(GrantState.ACTIVE, null, null));
    var failure = new XeroProviderFailure("XERO_UNAVAILABLE", "Not confirmed", 0, false, true, null);
    when(provider.refresh(anyString())).thenThrow(failure);
    assertThatThrownBy(() -> service.access(id)).isSameAs(failure);
    verify(repository).recordGrantFailure(id, GrantState.REFRESH_PENDING, failure.code(), clock.instant(), null);
    verify(repository, never()).saveRotated(any());
  }

  @Test
  void frUc15_10_pendingRefreshBeyondGracePeriodRequiresConsentWithoutBlindRetry() {
    when(repository.lockGrant(id)).thenReturn(expired(GrantState.REFRESH_PENDING, clock.instant().minusSeconds(1801), null));
    assertThatThrownBy(() -> service.access(id)).isInstanceOf(XeroProviderFailure.class);
    verifyNoInteractions(provider);
    verify(repository).recordGrantFailure(id, GrantState.NEEDS_RECONNECTION, "XERO_RECONNECT_REQUIRED", null, null);
  }

  @Test
  void frUc15_14_retryAfterIsHonouredBeforeAnyNewProviderCall() {
    when(repository.lockGrant(id)).thenReturn(expired(GrantState.ACTIVE, null, clock.instant().plusSeconds(120)));
    assertThatThrownBy(() -> service.access(id)).isInstanceOf(XeroProviderFailure.class)
        .satisfies(e -> assertThat(((XeroProviderFailure)e).retryAfterSeconds()).isGreaterThanOrEqualTo(120));
    verifyNoInteractions(provider);
  }
}
