package com.airconcepts.dashboard.vwork;

import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.xero.TokenCipher;
import java.util.*;
import org.junit.jupiter.api.Test;

class VworkSecretCipherTest {
  private final String key = Base64.getEncoder().encodeToString(new byte[32]);
  private final String otherKey = Base64.getEncoder().encodeToString(new byte[]{1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0});

  @Test void gl26_separateKeyAndBoundContextProtectTheCredential() {
    var cipher = new VworkSecretCipher(key, otherKey);
    var first = cipher.encrypt(new VworkCredential("synthetic-key"));
    var second = cipher.encrypt(new VworkCredential("synthetic-key"));
    assertThat(first).isNotEqualTo(second).doesNotContain("synthetic-key");
    assertThat(cipher.decrypt(first).value()).isEqualTo("synthetic-key");
    assertThatThrownBy(() -> new TokenCipher(key).decrypt(UUID.randomUUID(), "access", first)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> new VworkSecretCipher(otherKey, key).decrypt(first)).isInstanceOf(RuntimeException.class)
        .hasMessageNotContaining("synthetic-key");
    assertThat(cipher.toString()).doesNotContain(key, otherKey, "synthetic-key");
  }

  @Test void gl26_missingOrSharedEncryptionKeysFailClosed() {
    for (var invalid : List.of("", "not-base64", Base64.getEncoder().encodeToString(new byte[16]), otherKey))
      assertThatThrownBy(() -> new VworkSecretCipher(invalid, otherKey).requireConfigured())
          .isInstanceOf(RuntimeException.class).hasMessageNotContaining(invalid.isEmpty() ? "synthetic-key" : invalid);
  }
}
