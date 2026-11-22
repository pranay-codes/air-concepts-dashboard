package com.airconcepts.dashboard.vwork;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VworkConnectionServiceTest {
  private final VworkConnectionRepository repository = mock(VworkConnectionRepository.class);
  private final VworkClient client = mock(VworkClient.class);
  private final Instant now = Instant.parse("2026-11-19T00:00:00Z");
  private final String key = Base64.getEncoder().encodeToString(new byte[32]);
  private final VworkSecretCipher cipher = new VworkSecretCipher(key, "");
  private final VworkConnectionService service = new VworkConnectionService(repository, () -> client, cipher,
      Clock.fixed(now, ZoneOffset.UTC));
  private final CurrentUser director = new CurrentUser(UUID.randomUUID().toString(), Role.DIRECTOR);

  @Test void gl26_successVerifiesBeforeSavingOnlyEncryptedMaterial() {
    when(repository.read()).thenReturn(Optional.empty());
    service.connect(director, "synthetic-secret");
    var order = inOrder(repository, client);
    order.verify(repository).read();
    order.verify(client).jobs(any(), eq(1));
    var value = org.mockito.ArgumentCaptor.forClass(VworkConnectionRepository.Change.class);
    order.verify(repository).change(eq(UUID.fromString(director.subject())), isNull(), value.capture());
    assertThat(value.getValue().status()).isEqualTo("ACTIVE");
    assertThat(value.getValue().verifiedAt()).isEqualTo(now);
    assertThat(value.getValue().ciphertext()).doesNotContain("synthetic-secret");
    assertThat(cipher.decrypt(value.getValue().ciphertext()).value()).isEqualTo("synthetic-secret");
    assertThat(value.getValue().toString()).doesNotContain("synthetic-secret", value.getValue().ciphertext());
  }

  @Test void gl26_rejectedKeyNeverReplacesStoredCredential() {
    when(repository.read()).thenReturn(Optional.empty());
    when(client.jobs(any(), eq(1))).thenThrow(new VworkFailure("VWORK_KEY_REJECTED", "safe", 401, null));
    assertThatThrownBy(() -> service.connect(director, "synthetic-secret"))
        .isInstanceOfSatisfying(ApiFailure.class, e -> assertThat(e.code()).isEqualTo("VWORK_KEY_REJECTED"));
    verify(repository, never()).change(any(), any(), any());
  }

  @Test void gl26_providerUnavailableSavesUnverifiedWithSafeRetry() {
    when(repository.read()).thenReturn(Optional.empty());
    when(client.jobs(any(), eq(1))).thenThrow(new VworkFailure("VWORK_RATE_LIMITED", "unsafe provider text", 429, 999999));
    service.connect(director, "synthetic-secret");
    var value = org.mockito.ArgumentCaptor.forClass(VworkConnectionRepository.Change.class);
    verify(repository).change(any(), isNull(), value.capture());
    assertThat(value.getValue().status()).isEqualTo("UNVERIFIED");
    assertThat(value.getValue().errorCode()).isEqualTo("VWORK_RATE_LIMITED");
    assertThat(value.getValue().retryAt()).isEqualTo(now.plusSeconds(3600));
  }

  @Test void gl26_forbiddenAndUnexpectedFailuresNeverStoreKey() {
    assertThatThrownBy(() -> service.connect(new CurrentUser(director.subject(), Role.STANDARD_USER), "secret"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    when(repository.read()).thenReturn(Optional.empty());
    when(client.jobs(any(), eq(1))).thenThrow(new VworkFailure("UNKNOWN", "unsafe", 400, null));
    assertThatThrownBy(() -> service.connect(director, "synthetic-secret"))
        .isInstanceOf(ApiFailure.class).hasMessageNotContaining("unsafe").hasMessageNotContaining("synthetic-secret");
    verify(repository, never()).change(any(), any(), any());
  }

  @Test void gl26_staffSeeOnlyStatusAndDisconnectRemovesCiphertext() {
    var stored = new VworkConnectionRepository.Stored("42", "encrypted-secret", "ACTIVE", "SAFE_CODE",
        UUID.randomUUID(), now, now, null);
    when(repository.read()).thenReturn(Optional.of(stored));
    var staffView = service.get(new CurrentUser(director.subject(), Role.FINANCIAL_ADMIN));
    assertThat(staffView.status()).isEqualTo("ACTIVE");
    assertThat(staffView.configuredBy()).isNull();
    assertThat(staffView.configuredAt()).isNull();
    assertThat(staffView.verifiedAt()).isNull();
    assertThat(staffView.errorCode()).isNull();
    service.disconnect(director);
    var change = org.mockito.ArgumentCaptor.forClass(VworkConnectionRepository.Change.class);
    verify(repository).change(eq(UUID.fromString(director.subject())), eq("42"), change.capture());
    assertThat(change.getValue().status()).isEqualTo("DISCONNECTED");
    assertThat(change.getValue().ciphertext()).isNull();
    assertThat(stored.toString()).doesNotContain("encrypted-secret");
  }

  @Test void gl26_concurrentChangeFailsWithoutLeakingCredential() {
    when(repository.read()).thenReturn(Optional.of(new VworkConnectionRepository.Stored("10", "old", "ACTIVE",
        null, UUID.randomUUID(), now, now, null)));
    doThrow(new ApiFailure(org.springframework.http.HttpStatus.CONFLICT, "VWORK_CONNECTION_CHANGED", "Refresh."))
        .when(repository).change(any(), eq("10"), any());
    assertThatThrownBy(() -> service.connect(director, "synthetic-secret"))
        .isInstanceOfSatisfying(ApiFailure.class, e -> assertThat(e.code()).isEqualTo("VWORK_CONNECTION_CHANGED"))
        .hasMessageNotContaining("synthetic-secret");
  }

  @Test void gl26_internalSyncAccessRequiresActiveConnectionAndDoesNotExposeCredential() {
    when(repository.read()).thenReturn(Optional.empty());
    assertThatThrownBy(service::systemAccess).isInstanceOfSatisfying(ApiFailure.class,
        error -> assertThat(error.code()).isEqualTo("VWORK_NOT_CONNECTED"));
    var encrypted = cipher.encrypt(new VworkCredential("synthetic-secret"));
    when(repository.read()).thenReturn(Optional.of(new VworkConnectionRepository.Stored("42", encrypted, "ACTIVE", null,
        UUID.randomUUID(), now, now, null)));
    var access = service.systemAccess();
    assertThat(access.revision()).isEqualTo("42");
    assertThat(access.searchVerified()).isFalse();
    assertThat(access.toString()).doesNotContain("synthetic-secret", encrypted);
  }
}
