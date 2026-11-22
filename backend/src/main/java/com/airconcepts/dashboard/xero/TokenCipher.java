package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ApiFailure;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;

/** Versioned authenticated encryption, bound to the grant and token purpose. */
public final class TokenCipher {
  private static final int NONCE_BYTES = 12;
  private final String encodedKey;
  private final SecureRandom random = new SecureRandom();

  public TokenCipher(String encodedKey) { this.encodedKey = encodedKey; }

  public String encrypt(UUID grant, String purpose, String plaintext) {
    return encryptWithContext(context(grant, purpose), plaintext);
  }

  /** The shared vWork account has a separate key and fixed, distinct associated data. */
  public String encryptVwork(String plaintext) {
    return encryptWithContext("vwork:account".getBytes(StandardCharsets.UTF_8), plaintext);
  }

  private String encryptWithContext(byte[] associatedData, String plaintext) {
    if (plaintext == null || plaintext.isBlank() || plaintext.length() > 32768) throw unreadable();
    try {
      var key = key();
      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      cipher.updateAAD(associatedData);
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] envelope = ByteBuffer.allocate(1 + nonce.length + encrypted.length)
          .put((byte) 1).put(nonce).put(encrypted).array();
      return Base64.getUrlEncoder().withoutPadding().encodeToString(envelope);
    } catch (GeneralSecurityException failure) { throw unreadable(); }
  }

  public String decrypt(UUID grant, String purpose, String envelope) {
    return decryptWithContext(context(grant, purpose), envelope);
  }

  public String decryptVwork(String envelope) {
    return decryptWithContext("vwork:account".getBytes(StandardCharsets.UTF_8), envelope);
  }

  private String decryptWithContext(byte[] associatedData, String envelope) {
    try {
      var key = key();
      if (envelope == null || envelope.length() > 65536) throw unreadable();
      byte[] bytes = Base64.getUrlDecoder().decode(envelope);
      if (bytes.length < 1 + NONCE_BYTES + 16 || bytes[0] != 1) throw unreadable();
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(bytes, 1, 13)));
      cipher.updateAAD(associatedData);
      return new String(cipher.doFinal(bytes, 13, bytes.length - 13), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException failure) { throw unreadable(); }
  }

  public void requireConfigured() { key(); }

  private SecretKeySpec key() {
    try {
      byte[] bytes = Base64.getDecoder().decode(encodedKey == null ? "" : encodedKey);
      if (bytes.length != 32) throw new IllegalArgumentException();
      return new SecretKeySpec(bytes, "AES");
    } catch (IllegalArgumentException invalid) {
      throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "XERO_KEY_NOT_CONFIGURED",
          "Xero credential encryption is not configured. Ask the setup owner to check the server key.");
    }
  }

  private byte[] context(UUID grant, String purpose) {
    if (grant == null || purpose == null || !Set.of("access", "refresh").contains(purpose)) throw unreadable();
    return (grant + ":" + purpose).getBytes(StandardCharsets.UTF_8);
  }

  private static ApiFailure unreadable() {
    return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "XERO_TOKEN_UNREADABLE",
        "The server could not read Xero credentials. Ask the setup owner to check the connection.");
  }

  @Override public String toString() { return "TokenCipher[redacted]"; }
}
