package com.airconcepts.dashboard;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;

/** Builds fake JWTs for tests. They are never signed, and tests never contact Supabase. */
public final class TestJwts {

  public static final String TOKEN = "test-token";

  private TestJwts() {
  }

  public static Jwt forSubject(String subject) {
    return forSubject(subject, Map.of());
  }

  public static Jwt forSubject(String subject, Map<String, Object> claims) {
    Instant now = Instant.now();
    Jwt.Builder builder = Jwt.withTokenValue(TOKEN)
        .header("alg", "ES256")
        .subject(subject)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(3600));
    claims.forEach(builder::claim);
    return builder.build();
  }
}
