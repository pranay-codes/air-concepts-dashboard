package com.airconcepts.dashboard.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies Supabase JWTs against the project's JWKS. Built from explicit settings so startup makes no
 * network call: keys are fetched on the first request, and the issuer is always checked.
 */
@Configuration
class JwtDecoderConfig {

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${dashboard.supabase.jwks-uri}") String jwksUri,
      @Value("${dashboard.supabase.jwt-issuer-uri}") String issuer) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
        .jwsAlgorithm(SignatureAlgorithm.ES256)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }
}
