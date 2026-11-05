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
        .hasMessageContaining("SUPABASE_TEST_PROJECT_REF");
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
  void requiresTheTestProjectRef() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_TEST_PROJECT_REF", "");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SUPABASE_TEST_PROJECT_REF");
  }

  @Test
  void rejectsAuthIssuerOutsideTheTestProject() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_JWT_ISSUER_URI", "https://live-project.supabase.co/auth/v1");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Test project");
  }

  @Test
  void rejectsJwksUriOnAnotherHost() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("SUPABASE_JWKS_URI", "https://live-project.supabase.co/auth/v1/.well-known/jwks.json");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Test project");
  }

  @Test
  void rejectsDatabaseOutsideTheTestProject() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", "jdbc:postgresql://live-project.supabase.co:5432/postgres?sslmode=require");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Test project");
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
  void acceptsTestPoolerWhenUserCarriesTheTestProjectRef() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", POOLER_URL);
    environment.setProperty("DATABASE_USER", "postgres.placeholder");

    assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsTestPoolerWhenUserCarriesAnotherProjectRef() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", POOLER_URL);
    environment.setProperty("DATABASE_USER", "postgres.live-project");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Test project");
  }

  @Test
  void rejectsPoolerWithPlainPostgresUser() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL", POOLER_URL);
    environment.setProperty("DATABASE_USER", "postgres");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Test project");
  }

  @Test
  void rejectsLookalikePoolerHostEvenWithTheTestUser() {
    MockEnvironment environment = allRequiredSettings();
    environment.setProperty("DATABASE_URL",
        "jdbc:postgresql://aws-0-ap-southeast-2.pooler.supabase.com.example.org:5432/postgres?sslmode=require");
    environment.setProperty("DATABASE_USER", "postgres.placeholder");

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Test project");
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
  void passesWhenAllSettingsPresentAndPointAtTheTestProject() {
    assertThatCode(() -> processor.postProcessEnvironment(allRequiredSettings(), new SpringApplication()))
        .doesNotThrowAnyException();
  }

  private static MockEnvironment allRequiredSettings() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("DATABASE_URL", "jdbc:postgresql://placeholder.supabase.co:5432/postgres?sslmode=require");
    environment.setProperty("DATABASE_USER", "placeholder");
    environment.setProperty("DATABASE_PASSWORD", "placeholder");
    environment.setProperty("SUPABASE_JWT_ISSUER_URI", "https://placeholder.supabase.co/auth/v1");
    environment.setProperty("SUPABASE_JWKS_URI", "https://placeholder.supabase.co/auth/v1/.well-known/jwks.json");
    environment.setProperty("SUPABASE_TEST_PROJECT_REF", "placeholder");
    return environment;
  }
}
