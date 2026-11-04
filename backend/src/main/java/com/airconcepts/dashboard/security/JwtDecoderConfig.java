package com.airconcepts.dashboard.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies Supabase JWTs against the project's JWKS. Built from explicit settings so startup makes no
 * network call: keys are fetched on the first request, and the issuer and audience are always checked.
 */
@Configuration
class JwtDecoderConfig {

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${dashboard.supabase.jwks-uri}") String jwksUri,
      @Value("${dashboard.supabase.jwt-issuer-uri}") String issuer,
      @Value("${dashboard.supabase.jwt-audience}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
        .jwsAlgorithm(SignatureAlgorithm.ES256)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build();
    decoder.setJwtValidator(validator(issuer, audience));
    return decoder;
  }

  /** Expiry and issuer from Spring's defaults, plus the audience check that Spring does not add. */
  static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
    OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
        JwtClaimNames.AUD,
        audiences -> audiences != null && audiences.contains(audience));
    return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
  }
}
