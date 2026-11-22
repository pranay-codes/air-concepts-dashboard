package com.airconcepts.dashboard.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Fails startup with one message that names every missing setting. Runs last, after config files load,
 * so a value set in application-*.yml counts too. Then refuses any Supabase or database setting that does
 * not point at the one Supabase project named by SUPABASE_PROJECT_REF.
 *
 * <p>The guard is environment-neutral on purpose: the same backend image runs on Test and Live, so the
 * project that is allowed comes from configuration, never from the code. SUPABASE_TEST_PROJECT_REF is the
 * legacy name and is still read when SUPABASE_PROJECT_REF is absent, so a Test host rolled back to an older
 * release keeps starting.
 */
public class RequiredSettingsPostProcessor implements EnvironmentPostProcessor, Ordered {

  static final String PROJECT_REF = "SUPABASE_PROJECT_REF";
  static final String LEGACY_PROJECT_REF = "SUPABASE_TEST_PROJECT_REF";

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
        .collect(Collectors.toCollection(ArrayList::new));
    String projectRef = projectRef(environment);
    if (!StringUtils.hasText(projectRef)) {
      missing.add(PROJECT_REF);
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing required settings: " + String.join(", ", missing)
          + ". Set them as environment variables. See README.md, Run locally.");
    }
    requireNamedProject(environment, projectRef);
    requireVworkConfiguration(environment);
  }

  private static void requireVworkConfiguration(ConfigurableEnvironment environment) {
    String mode = environment.getProperty("dashboard.vwork.mode");
    if (!StringUtils.hasText(mode)) mode = environment.getProperty("DASHBOARD_VWORK_MODE");
    if (!StringUtils.hasText(mode)) return;
    if (!mode.equals("mock") && !mode.equals("live"))
      throw new IllegalStateException("dashboard.vwork.mode must be mock or live when configured.");
    String configured = environment.getProperty("VWORK_CREDENTIAL_ENCRYPTION_KEY", "");
    try {
      byte[] key = Base64.getDecoder().decode(configured);
      if (key.length != 32) throw new IllegalArgumentException();
      String xero = environment.getProperty("XERO_TOKEN_ENCRYPTION_KEY", "");
      byte[] xeroBytes;
      try { xeroBytes = Base64.getDecoder().decode(xero); }
      catch (IllegalArgumentException invalid) { xeroBytes = new byte[0]; }
      if (xeroBytes.length == 32 && java.security.MessageDigest.isEqual(key, xeroBytes))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException("VWORK_CREDENTIAL_ENCRYPTION_KEY must be a separate base64-encoded 32-byte key.");
    }
  }

  private static String projectRef(ConfigurableEnvironment environment) {
    String projectRef = environment.getProperty(PROJECT_REF);
    if (StringUtils.hasText(projectRef)) {
      return projectRef;
    }
    return environment.getProperty(LEGACY_PROJECT_REF);
  }

  private static void requireNamedProject(ConfigurableEnvironment environment, String projectRef) {
    String expectedHost = projectRef + ".supabase.co";

    String issuerHost = hostOf(environment.getProperty("SUPABASE_JWT_ISSUER_URI"));
    String jwksHost = hostOf(environment.getProperty("SUPABASE_JWKS_URI"));
    if (!expectedHost.equals(issuerHost) || !expectedHost.equals(jwksHost)) {
      throw new IllegalStateException("Refusing to start: Supabase Auth settings do not point at the Supabase"
          + " project named by " + PROJECT_REF + ".");
    }

    String databaseUrl = environment.getProperty("DATABASE_URL");
    String databaseUser = environment.getProperty("DATABASE_USER");
    if (!databaseUrl.contains(projectRef) && !isProjectPooler(databaseUrl, databaseUser, projectRef)) {
      throw new IllegalStateException("Refusing to start: DATABASE_URL does not point at the Supabase"
          + " project named by " + PROJECT_REF + ".");
    }
    if (!databaseUrl.contains("sslmode=require")) {
      throw new IllegalStateException("Refusing to start: DATABASE_URL must require TLS (sslmode=require).");
    }
  }

  // Supabase's shared pooler host does not contain the project ref. The project is selected by the
  // username, postgres.<ref>. Accept only a Supabase pooler host together with that exact username.
  private static final Pattern SUPABASE_POOLER_URL = Pattern.compile(
      "^jdbc:postgresql://aws-\\d+-[a-z0-9-]+\\.pooler\\.supabase\\.com:\\d+/");

  private static boolean isProjectPooler(String databaseUrl, String databaseUser, String projectRef) {
    return SUPABASE_POOLER_URL.matcher(databaseUrl).find()
        && ("postgres." + projectRef).equals(databaseUser);
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
