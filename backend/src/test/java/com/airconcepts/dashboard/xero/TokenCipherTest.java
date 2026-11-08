package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TokenCipherTest {
  private final String key = Base64.getEncoder().encodeToString(new byte[32]);
  private final UUID grant = UUID.randomUUID();

  @Test
  void frUc15_10_authenticatedEncryptionRoundTripsWithUniqueNonceAndNoPlaintextEnvelope() {
    var cipher = new TokenCipher(key);
    var first = cipher.encrypt(grant, "access", "synthetic-access-value");
    var second = cipher.encrypt(grant, "access", "synthetic-access-value");
    assertThat(first).isNotEqualTo(second).doesNotContain("synthetic-access-value");
    assertThat(cipher.decrypt(grant, "access", first)).isEqualTo("synthetic-access-value");
    assertThat(cipher.toString()).doesNotContain(key);
  }

  @Test
  void frUc15_10_ciphertextCannotBeMovedBetweenGrantsOrTokenPurposesOrTampered() {
    var cipher = new TokenCipher(key);
    var encrypted = cipher.encrypt(grant, "refresh", "synthetic-refresh-value");
    assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(), "refresh", encrypted)).hasMessageNotContaining("synthetic-refresh-value");
    assertThatThrownBy(() -> cipher.decrypt(grant, "access", encrypted)).isInstanceOf(RuntimeException.class);
    byte[] bytes = Base64.getUrlDecoder().decode(encrypted);
    bytes[bytes.length - 1] ^= 1;
    assertThatThrownBy(() -> cipher.decrypt(grant, "refresh", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void frUc15_10_missingInvalidOrWrongLengthKeyFailsClosedWithoutEchoingIt() {
    for (String invalid : List.of("", "not-a-key", Base64.getEncoder().encodeToString(new byte[16]))) {
      assertThatThrownBy(() -> new TokenCipher(invalid).encrypt(grant, "access", "synthetic-access-value"))
          .isInstanceOf(RuntimeException.class).hasMessageNotContaining("synthetic-access-value");
    }
    assertThatThrownBy(() -> new TokenCipher(key).decrypt(grant, "access", "invalid"))
        .isInstanceOf(RuntimeException.class);
  }
}
