package com.airconcepts.dashboard.config;

import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Fails startup with one message that names every missing setting. Runs last, after config files load,
 * so a value set in application-*.yml counts too.
 */
public class RequiredSettingsPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final List<String> REQUIRED = List.of(
      "DATABASE_URL",
      "DATABASE_USER",
      "DATABASE_PASSWORD",
      "SUPABASE_JWT_ISSUER_URI",
      "SUPABASE_JWKS_URI");

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    List<String> missing = REQUIRED.stream()
        .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
        .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing required settings: " + String.join(", ", missing)
          + ". Set them as environment variables. See README.md, Run locally.");
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
