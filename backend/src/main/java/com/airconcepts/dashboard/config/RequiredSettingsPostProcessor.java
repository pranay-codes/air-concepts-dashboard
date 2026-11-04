package com.airconcepts.dashboard.config;

import java.net.URI;
import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Fails startup with one message that names every missing setting. Runs last, after config files load,
 * so a value set in application-*.yml counts too. Then refuses any Supabase or database setting that does
 * not point at the Test project named by SUPABASE_TEST_PROJECT_REF.
 */
public class RequiredSettingsPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final List<String> REQUIRED = List.of(
      "DATABASE_URL",
      "DATABASE_USER",
      "DATABASE_PASSWORD",
      "SUPABASE_JWT_ISSUER_URI",
      "SUPABASE_JWKS_URI",
      "SUPABASE_TEST_PROJECT_REF");

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    List<String> missing = REQUIRED.stream()
        .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
        .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing required settings: " + String.join(", ", missing)
          + ". Set them as environment variables. See README.md, Run locally.");
    }
    requireTestProject(environment);
  }

  private static void requireTestProject(ConfigurableEnvironment environment) {
    String projectRef = environment.getProperty("SUPABASE_TEST_PROJECT_REF");
    String expectedHost = projectRef + ".supabase.co";

    String issuerHost = hostOf(environment.getProperty("SUPABASE_JWT_ISSUER_URI"));
    String jwksHost = hostOf(environment.getProperty("SUPABASE_JWKS_URI"));
    if (!expectedHost.equals(issuerHost) || !expectedHost.equals(jwksHost)) {
      throw new IllegalStateException("Refusing to start: Supabase Auth settings do not point at the Test project"
          + " named by SUPABASE_TEST_PROJECT_REF.");
    }

    String databaseUrl = environment.getProperty("DATABASE_URL");
    if (!databaseUrl.contains(projectRef)) {
      throw new IllegalStateException("Refusing to start: DATABASE_URL does not point at the Test project"
          + " named by SUPABASE_TEST_PROJECT_REF.");
    }
    if (!databaseUrl.contains("sslmode=require")) {
      throw new IllegalStateException("Refusing to start: DATABASE_URL must require TLS (sslmode=require).");
    }
  }

  private static String hostOf(String uri) {
    try {
      return URI.create(uri).getHost();
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
