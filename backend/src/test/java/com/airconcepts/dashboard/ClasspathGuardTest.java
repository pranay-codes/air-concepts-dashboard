package com.airconcepts.dashboard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Supabase CLI is the only migration tool, so ORM and migration libraries must stay off the classpath. */
class ClasspathGuardTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "org.hibernate.Session",
      "org.flywaydb.core.Flyway",
      "liquibase.Liquibase",
  })
  void forbiddenLibraryIsAbsent(String className) {
    assertThatThrownBy(() -> Class.forName(className))
        .isInstanceOf(ClassNotFoundException.class);
  }
}
