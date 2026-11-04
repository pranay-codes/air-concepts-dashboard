package com.airconcepts.dashboard.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/** Requirement R2: issuer, audience and expiry are checked on every Supabase JWT, not only the signature. */
class JwtValidatorTest {

  private static final String ISSUER = "https://placeholder.supabase.co/auth/v1";
  private static final String AUDIENCE = "authenticated";

  private final OAuth2TokenValidator<Jwt> validator = JwtDecoderConfig.validator(ISSUER, AUDIENCE);

  @Test
  void acceptsTokenWithExpectedIssuerAudienceAndUnexpiredTime() {
    Jwt jwt = token(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(600));

    assertThat(validator.validate(jwt).hasErrors()).isFalse();
  }

  @Test
  void rejectsTokenFromAnotherIssuer() {
    Jwt jwt = token("https://live-project.supabase.co/auth/v1", List.of(AUDIENCE), Instant.now().plusSeconds(600));

    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }

  @Test
  void rejectsTokenIssuedForAnotherAudience() {
    Jwt jwt = token(ISSUER, List.of("anon"), Instant.now().plusSeconds(600));

    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }

  @Test
  void rejectsTokenWithoutAudience() {
    Jwt jwt = token(ISSUER, null, Instant.now().plusSeconds(600));

    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }

  @Test
  void rejectsExpiredToken() {
    Jwt jwt = token(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(600));

    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }

  private static Jwt token(String issuer, List<String> audience, Instant expiresAt) {
    Jwt.Builder builder = Jwt.withTokenValue("test-token")
        .header("alg", "ES256")
        .subject("user-123")
        .issuer(issuer)
        .issuedAt(Instant.now().minusSeconds(3600))
        .expiresAt(expiresAt);
    if (audience != null) {
      builder.audience(audience);
    }
    return builder.build();
  }
}
