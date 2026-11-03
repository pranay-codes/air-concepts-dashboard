package com.airconcepts.dashboard.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class RequiredSettingsPostProcessorTest {

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
        .hasMessageContaining("SUPABASE_JWKS_URI");
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
  void passesWhenAllSettingsPresent() {
    assertThatCode(() -> processor.postProcessEnvironment(allRequiredSettings(), new SpringApplication()))
        .doesNotThrowAnyException();
  }

  private static MockEnvironment allRequiredSettings() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/placeholder");
    environment.setProperty("DATABASE_USER", "placeholder");
    environment.setProperty("DATABASE_PASSWORD", "placeholder");
    environment.setProperty("SUPABASE_JWT_ISSUER_URI", "https://placeholder.invalid/auth/v1");
    environment.setProperty("SUPABASE_JWKS_URI", "https://placeholder.invalid/auth/v1/.well-known/jwks.json");
    return environment;
  }
}
