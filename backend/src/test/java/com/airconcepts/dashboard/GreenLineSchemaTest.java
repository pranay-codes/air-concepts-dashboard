package com.airconcepts.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

class GreenLineSchemaTest {
  private static PostgreSQLContainer postgres;

  @BeforeAll static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")), DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
    try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
         var statement = connection.createStatement()) {
      statement.execute("create role anon; create role authenticated; create role service_role; create role supabase_auth_admin;"
          + "create schema auth; create table auth.users (id uuid primary key)");
      try (var migrations = Files.list(Path.of("..", "supabase", "migrations"))) {
        for (var file : migrations.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
          statement.execute(Files.readString(file));
        }
      }
    }
  }

  @AfterAll static void stop() { if (postgres != null) postgres.stop(); }

  @Test void gl02_identityAndGl07AuditGrants() throws Exception {
    try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
         var statement = connection.createStatement()) {
      statement.execute("insert into public.entities(id, code, display_name) values "
          + "('00000000-0000-0000-0000-000000000001','green-line-test','Test')");
      var insert = "insert into public.work_items(entity_id,xero_quote_id,quote_number,xero_quote_status,stage) values "
          + "('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','Q1','SENT','QUOTED')";
      statement.execute(insert);
      assertThatThrownBy(() -> statement.execute(insert)).isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> statement.execute("update public.work_items set stage='CLOSED'"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> statement.execute("update public.work_items set stage='CLOSED', close_reason='LOST'"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> statement.execute("update public.work_items set attention_codes=array['UNRECOGNISED']"))
          .isInstanceOf(SQLException.class);
      statement.execute("insert into public.entities(id, code, display_name) values "
          + "('00000000-0000-0000-0000-000000000003','green-line-other','Other')");
      assertThatThrownBy(() -> statement.execute("insert into public.work_item_events(entity_id,work_item_id,source,event_type) "
          + "select '00000000-0000-0000-0000-000000000003',id,'SYSTEM','ITEM_CREATED' from public.work_items"))
          .isInstanceOf(SQLException.class);
      try (var result = statement.executeQuery("select has_table_privilege('authenticated','public.work_items','SELECT'),"
          + "has_table_privilege('service_role','public.work_item_events','UPDATE'),"
          + "(select relrowsecurity from pg_class where oid='public.work_items'::regclass)")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isFalse();
        assertThat(result.getBoolean(2)).isFalse();
        assertThat(result.getBoolean(3)).isTrue();
      }
    }
  }

  @Test void gl22_operationsEnforceEntityScopeAndPreventDuplicateActiveOrInvalidOutcomes() throws Exception {
    try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
         var statement = connection.createStatement()) {
      var first = java.util.UUID.randomUUID();
      var other = java.util.UUID.randomUUID();
      var item = java.util.UUID.randomUUID();
      statement.execute("insert into public.entities(id,code,display_name) values ('" + first + "','" + first + "','First'),('" + other + "','" + other + "','Other')");
      statement.execute("insert into public.work_items(id,entity_id,xero_quote_id,quote_number,xero_quote_status,stage) values ('"
          + item + "','" + first + "','" + java.util.UUID.randomUUID() + "','Q1','SENT','QUOTED')");
      String cols = "(id,entity_id,work_item_id,kind,status,attempt,lookup_reference,request_fingerprint) values ";
      String suffix = "','VWORK_CREATE_JOB','PENDING',1,'J1','" + "a".repeat(64) + "')";
      statement.execute("insert into public.external_operations" + cols + "('" + java.util.UUID.randomUUID() + "','" + first + "','" + item + suffix);
      assertThatThrownBy(() -> statement.execute("insert into public.external_operations" + cols + "('" + java.util.UUID.randomUUID() + "','" + other + "','" + item + suffix)).isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> statement.execute("insert into public.external_operations" + cols + "('" + java.util.UUID.randomUUID() + "','" + first + "','" + item + suffix)).isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> statement.execute("update public.external_operations set status='SUCCEEDED',completed_at=now(),external_id='job' where work_item_id='" + item + "'"))
          .isInstanceOf(SQLException.class);
      try (var result = statement.executeQuery("select has_table_privilege('authenticated','public.external_operations','SELECT'),"
          + "has_table_privilege('service_role','public.external_operations','DELETE'),"
          + "(select relrowsecurity from pg_class where oid='public.external_operations'::regclass)")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).isFalse();
        assertThat(result.getBoolean(2)).isFalse();
        assertThat(result.getBoolean(3)).isTrue();
      }
    }
  }
}
