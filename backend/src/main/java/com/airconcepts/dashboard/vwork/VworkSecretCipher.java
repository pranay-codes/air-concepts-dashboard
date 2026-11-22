package com.airconcepts.dashboard.vwork;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TokenCipher;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.http.HttpStatus;

/** Separate vWork key; neither configuration nor credential is exposed by this object. */
public final class VworkSecretCipher {
  private final String key;
  private final String xeroKey;
  private final TokenCipher cipher;
  public VworkSecretCipher(String key, String xeroKey) { this.key = key; this.xeroKey = xeroKey; this.cipher = new TokenCipher(key); }
  public void requireConfigured() {
    try {
      byte[] bytes = Base64.getDecoder().decode(key == null ? "" : key);
      if (bytes.length != 32) throw invalid();
      byte[] xero;
      try { xero = Base64.getDecoder().decode(xeroKey == null ? "" : xeroKey); }
      catch (IllegalArgumentException ignored) { xero = new byte[0]; }
      if (xero.length == 32 && MessageDigest.isEqual(bytes, xero)) throw invalid();
    } catch (IllegalArgumentException failure) { throw invalid(); }
  }
  public String encrypt(VworkCredential credential) {
    requireConfigured();
    try { return cipher.encryptVwork(credential.value()); }
    catch (RuntimeException failure) { throw unreadable(); }
  }
  public VworkCredential decrypt(String envelope) {
    requireConfigured();
    try { return new VworkCredential(cipher.decryptVwork(envelope)); }
    catch (RuntimeException failure) { throw unreadable(); }
  }
  private static ApiFailure invalid() { return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "VWORK_KEY_NOT_CONFIGURED", "vWork credential encryption is not configured correctly."); }
  private static ApiFailure unreadable() { return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "VWORK_CREDENTIAL_UNREADABLE", "The vWork credential could not be read."); }
  @Override public String toString() { return "VworkSecretCipher[redacted]"; }
}
