package com.airconcepts.dashboard;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Destructive migration tests run exclusively on this disposable database. */
class Uc5SchemaTest {
  private static PostgreSQLContainer postgres;

  @BeforeAll
  static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")),
        DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
    try (var connection = connect(); var statement = connection.createStatement()) {
      statement.execute("create role anon; create role authenticated; create role service_role; create role supabase_auth_admin;"
          + "create schema auth; create table auth.users (id uuid primary key)");
      try (var migrations = Files.list(Path.of("..", "supabase", "migrations"))) {
        for (var file : migrations.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
          statement.execute(Files.readString(file));
        }
      }
    }
  }

  @AfterAll
  static void stop() {
    if (postgres != null) postgres.stop();
  }

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  @Test
  void frUc15_02_supportsMoreThanThreeEntitiesAndKeepsUniqueCodes() throws Exception {
    try (var connection = connect(); var statement = connection.createStatement()) {
      statement.execute("insert into public.entities(code,display_name) values "
          + "('services','Services'),('commercial','Commercial'),('hotas','HotAS'),('additional','Additional')");
      try (var result = statement.executeQuery("select count(*) from public.entities")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(4);
      }
      assertThatThrownBy(() -> statement.execute("insert into public.entities(code,display_name) values ('additional','Duplicate')"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> statement.execute("insert into public.entities(code,display_name) values ('invalid name','Invalid')"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void frUc15_16_18_staffHistorySurvivesDeactivationAndBrowserCannotReadRoles() throws Exception {
    try (var connection = connect(); var statement = connection.createStatement()) {
      var id = UUID.randomUUID();
      statement.execute("insert into auth.users(id) values ('" + id + "')");
      statement.execute("insert into public.staff_users(id,email,display_name,role) values ('"
          + id + "','staff@example.invalid','Staff','STANDARD_USER')");
      statement.execute("update public.staff_users set active=false where id='" + id + "'");
      try (var result = statement.executeQuery("select active from public.staff_users where id='" + id + "'")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isFalse();
      }
      try (var result = statement.executeQuery("select has_table_privilege('authenticated','public.staff_users','SELECT'),"
          + "has_table_privilege('authenticated','public.entities','SELECT'),"
          + "(select relrowsecurity from pg_class where oid='public.staff_users'::regclass)")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isFalse();
        assertThat(result.getBoolean(2)).isFalse();
        assertThat(result.getBoolean(3)).isTrue();
      }
      assertThatThrownBy(() -> statement.execute("delete from auth.users where id='" + id + "'"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void frUc15_17_18_authHookBlocksInactivePasswordAndUnknownUsersAndPreservesClaims() throws Exception {
    try (var connection = connect(); var statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      var active = UUID.randomUUID();
      var inactive = UUID.randomUUID();
      statement.execute("insert into auth.users(id) values ('" + active + "'),('" + inactive + "')");
      statement.execute("insert into public.staff_users(id,email,display_name,role,active) values ('"
          + active + "','hook-active@example.invalid','Active','STANDARD_USER',true),('"
          + inactive + "','hook-inactive@example.invalid','Inactive','STANDARD_USER',false)");
      statement.execute("set local role supabase_auth_admin");
      for (var method : java.util.List.of("otp", "token_refresh")) {
        String event = "{\"user_id\":\"" + active + "\",\"authentication_method\":\"" + method
            + "\",\"claims\":{\"sub\":\"" + active + "\",\"role\":\"authenticated\"}}";
        try (var result = statement.executeQuery("select public.uc5_access_token_hook('" + event + "'::jsonb) = '" + event + "'::jsonb")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getBoolean(1)).isTrue();
        }
      }
      for (String event : java.util.List.of(
          "{\"user_id\":\"" + inactive + "\",\"authentication_method\":\"otp\"}",
          "{\"user_id\":\"" + UUID.randomUUID() + "\",\"authentication_method\":\"otp\"}",
          "{\"user_id\":\"" + active + "\",\"authentication_method\":\"password\"}",
          "{\"user_id\":\"invalid\",\"authentication_method\":\"otp\"}")) {
        try (var result = statement.executeQuery("select public.uc5_access_token_hook('" + event + "'::jsonb)->'error'->>'http_code'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("403");
        }
      }
      connection.rollback();
    }
  }
}
