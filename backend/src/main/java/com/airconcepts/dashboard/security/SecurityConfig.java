package com.airconcepts.dashboard.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.airconcepts.dashboard.config.CorsProperties;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, RoleLookup roles) throws Exception {
    return http
        // Bearer tokens only, no cookies, so CSRF does not apply.
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            // QA-001 AC-4: exact path only. requestMatchers("/api/hello") does not match /api/hello/extra.
            .requestMatchers(HttpMethod.GET, "/api/hello").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/xero/callback").permitAll()
            .requestMatchers("/api/v1/forecast/**", "/api/v1/entities/*/forecast", "/api/v1/entities/*/forecast/**")
            .access((authentication, context) -> {
              var principal = authentication.get().getPrincipal();
              boolean allowed = principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt
                  && roles.findRole(jwt.getSubject()).filter(role -> role == Role.DIRECTOR || role == Role.FINANCIAL_ADMIN).isPresent();
              return new org.springframework.security.authorization.AuthorizationDecision(allowed);
            })
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setExposedHeaders(List.of("Content-Disposition", "Retry-After", "X-Forecast-Reconciled", "X-Forecast-Generated-At", "X-Forecast-Warnings"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
