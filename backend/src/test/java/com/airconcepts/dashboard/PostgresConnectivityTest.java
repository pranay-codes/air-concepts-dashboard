package com.airconcepts.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * QA-001, AC-7. Runs against a disposable PostgreSQL container, never a hosted project.
 * Fails in CI when Docker is missing; skipped locally when Docker is not running.
 *
 * <p>The container is started by hand, not with the Testcontainers JUnit extension, because the extension
 * would error before the Docker check runs. Starting it here keeps the CI-fail and local-skip rule in force.
 */
class PostgresConnectivityTest {

  private static PostgreSQLContainer postgres;

  @BeforeAll
  static void startPostgres() {
    DockerRequirement.check(
        "true".equals(System.getenv("CI")),
        DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
  }

  @AfterAll
  static void stopPostgres() {
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void connectsToDisposablePostgresAndRunsQuery() throws SQLException {
    try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
         Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("select 1")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isEqualTo(1);
    }
  }
}
