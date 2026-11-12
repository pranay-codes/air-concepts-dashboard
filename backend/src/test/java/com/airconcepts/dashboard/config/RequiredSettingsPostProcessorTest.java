package com.airconcepts.dashboard.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class RequiredSettingsPostProcessorTest {

  private static final String POOLER_URL =
      "jdbc:postgresql://aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres?sslmode=require";

  private final RequiredSettingsPostProcessor processor = new RequiredSettingsPostProcessor();

  @Test
  void failsNamingEveryMissingSetting() {
    MockEnvironment environment = new MockEnvironment();

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DATABASE_URL")
        .hasMessageContaining("DATABASE_USER")
        .hasMessageContaining("DATABASE_PASSWORD")
        .hasMessageContaining("SUPABASE_JWT_ISSUER_URI")
        .hasMessageContaining("SUPABASE_JWKS_URI")
        .hasMessageContaining("SUPABASE_PROJECT_REF");
  }

  @Test
  void blankSettingCountsAsMissing() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_PASSWORD", "   ");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DATABASE_PASSWORD")
        .hasMessageNotContaining("DATABASE_URL");
  }

  @Test
  void requiresTheProjectRef() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_PROJECT_REF", "");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SUPABASE_PROJECT_REF");
  }

  @Test
  void rejectsAuthIssuerOutsideTheNamedProject() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_JWT_ISSUER_URI", "https://other-project.supabase.co/auth/v1");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("project named by SUPABASE_PROJECT_REF");
  }

  @Test
  void rejectsJwksUriOnAnotherHost() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_JWKS_URI", "https://other-project.supabase.co/auth/v1/.well-known/jwks.json");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("project named by SUPABASE_PROJECT_REF");
  }

  @Test
  void rejectsDatabaseOutsideTheNamedProject() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", "jdbc:postgresql://other-project.supabase.co:5432/postgres?sslmode=require");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("project named by SUPABASE_PROJECT_REF");
  }

  @Test
  void rejectsDatabaseConnectionWithoutTls() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", "jdbc:postgresql://placeholder.supabase.co:5432/postgres");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sslmode=require");
  }

  @Test
  void acceptsPoolerWhenUserCarriesTheProjectRef() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", POOLER_URL);
    environment.setProperty("DATABASE_USER", "postgres.placeholder");

    assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsPoolerWhenUserCarriesAnotherProjectRef() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", POOLER_URL);
    environment.setProperty("DATABASE_USER", "postgres.other-project");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("project named by SUPABASE_PROJECT_REF");
  }

  @Test
  void rejectsPoolerWithPlainPostgresUser() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", POOLER_URL);
    environment.setProperty("DATABASE_USER", "postgres");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("project named by SUPABASE_PROJECT_REF");
  }

  @Test
  void rejectsLookalikePoolerHostEvenWithTheProjectUser() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL",
        "jdbc:postgresql://aws-0-ap-southeast-2.pooler.supabase.com.example.org:5432/postgres?sslmode=require");
    environment.setProperty("DATABASE_USER", "postgres.placeholder");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("project named by SUPABASE_PROJECT_REF");
  }

  @Test
  void rejectsPoolerWithoutTls() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", "jdbc:postgresql://aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres");
    environment.setProperty("DATABASE_USER", "postgres.placeholder");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sslmode=require");
  }

  @Test
  void passesWhenAllSettingsPresentAndPointAtTheNamedProject() {
    assertThatCode(() -> processor.postProcessEnvironment(allRequiredSettings(), new SpringApplication()))
        .doesNotThrowAnyException();
  }

  // INF-006: the same image runs on Test and Live, so the allowed project comes only from configuration.
  @Test
  void acceptsAnyProjectRefWhenEverySettingPointsAtIt() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_PROJECT_REF", "prodref");
    environment.setProperty("SUPABASE_JWT_ISSUER_URI", "https://prodref.supabase.co/auth/v1");
    environment.setProperty("SUPABASE_JWKS_URI", "https://prodref.supabase.co/auth/v1/.well-known/jwks.json");
    environment.setProperty("DATABASE_URL", POOLER_URL);
    environment.setProperty("DATABASE_USER", "postgres.prodref");

    assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsTestSettingsWhenTheRefNamesAnotherProject() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_PROJECT_REF", "prodref");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("project named by SUPABASE_PROJECT_REF");
  }

  @Test
  void stillReadsTheLegacyTestProjectRefWhenTheNewNameIsAbsent() {
    MockEnvironment onlyLegacy = new MockEnvironment();
    onlyLegacy.setProperty("DATABASE_URL", "jdbc:postgresql://placeholder.supabase.co:5432/postgres?sslmode=require");
    onlyLegacy.setProperty("DATABASE_USER", "placeholder");
    onlyLegacy.setProperty("DATABASE_PASSWORD", "placeholder");
    onlyLegacy.setProperty("SUPABASE_JWT_ISSUER_URI", "https://placeholder.supabase.co/auth/v1");
    onlyLegacy.setProperty("SUPABASE_JWKS_URI", "https://placeholder.supabase.co/auth/v1/.well-known/jwks.json");
    onlyLegacy.setProperty("SUPABASE_TEST_PROJECT_REF", "placeholder");

    assertThatCode(() -> processor.postProcessEnvironment(onlyLegacy, new SpringApplication()))
        .doesNotThrowAnyException();
  }

  @Test
  void theNewNameWinsOverTheLegacyName() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_TEST_PROJECT_REF", "someone-else");

    assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .doesNotThrowAnyException();
  }

  private static MockEnvironment allRequiredSettings() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("DATABASE_URL", "jdbc:postgresql://placeholder.supabase.co:5432/postgres?sslmode=require");
    environment.setProperty("DATABASE_USER", "placeholder");
    environment.setProperty("DATABASE_PASSWORD", "placeholder");
    environment.setProperty("SUPABASE_JWT_ISSUER_URI", "https://placeholder.supabase.co/auth/v1");
    environment.setProperty("SUPABASE_JWKS_URI", "https://placeholder.supabase.co/auth/v1/.well-known/jwks.json");
    environment.setProperty("SUPABASE_PROJECT_REF", "placeholder");
    return environment;
  }
}
