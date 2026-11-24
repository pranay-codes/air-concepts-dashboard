package com.airconcepts.dashboard;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Destructive tests use only a disposable database; never a hosted project. */
class Uc4SchemaTest {
  private static PostgreSQLContainer postgres;
  private static final List<String> TABLES = List.of("forecast_documents", "forecast_sync_runs", "forecast_audit",
      "forecast_document_adjustments", "forecast_split_lines", "forecast_periods", "forecast_manual_items", "forecast_staffing", "forecast_exports",
      "forecast_generated_months", "forecast_snapshot_lines", "forecast_grid_revisions", "forecast_grid_lines");

  @BeforeAll static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")), DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
    try (var c = connect(); var s = c.createStatement()) {
      s.execute("create role anon; create role authenticated; create role service_role; create role supabase_auth_admin;"
          + "create schema auth; create table auth.users(id uuid primary key)");
      try (var files = Files.list(Path.of("..", "supabase", "migrations"))) {
        for (var file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) s.execute(Files.readString(file));
      }
    }
  }
  @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  @Test void frUc04_08_allForecastTablesHaveRlsAndNoBrowserPrivileges() throws Exception {
    try (var c = connect()) {
      for (String table : TABLES) {
        try (var s = c.prepareStatement("select relrowsecurity from pg_class where oid=?::regclass")) {
          s.setString(1, "public." + table);
          try (var r = s.executeQuery()) { assertThat(r.next()).isTrue(); assertThat(r.getBoolean(1)).isTrue(); }
        }
        for (String role : List.of("anon", "authenticated")) {
          for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
            try (var s = c.prepareStatement("select has_table_privilege(?, ?, ?)")) {
              s.setString(1, role); s.setString(2, "public." + table); s.setString(3, privilege);
              try (var r = s.executeQuery()) {
                assertThat(r.next()).isTrue(); assertThat(r.getBoolean(1)).as("%s %s %s", role, table, privilege).isFalse();
              }
            }
          }
        }
        try (var s = c.prepareStatement("select has_table_privilege('service_role',?,'DELETE')")) {
          s.setString(1, "public." + table);
          try (var r = s.executeQuery()) { r.next(); assertThat(r.getBoolean(1)).isEqualTo(table.equals("forecast_split_lines")); }
        }
      }
    }
  }

  @Test void frUc04_01_documentKeyMoneyAndEntityHistoryAreConstrained() throws Exception {
    try (var c = connect(); var s = c.createStatement()) {
      c.setAutoCommit(false);
      UUID entity = UUID.randomUUID(), document = UUID.randomUUID(), tenant = UUID.randomUUID();
      s.execute("insert into public.entities(id,code,display_name) values ('" + entity + "','schema_uc4','Schema')");
      String insert = "insert into public.forecast_documents(entity_id,xero_document_id,tenant_id,document_type,status,"
          + "issue_date,currency,total,total_tax,amount_due,amount_paid,amount_credited,outstanding,xero_updated_at) values ('"
          + entity + "','" + document + "','" + tenant + "','ACCREC','AUTHORISED','2026-11-01','NZD',115,15,100,15,0,true,now())";
      s.execute(insert);
      assertRejected(c, s, insert);
      assertRejected(c, s, "update public.forecast_documents set document_type='OTHER'");
      assertRejected(c, s, "update public.forecast_documents set status='INVALID'");
      assertRejected(c, s, "delete from public.entities where id='" + entity + "'");
      try (var r = s.executeQuery("select numeric_precision,numeric_scale from information_schema.columns "
          + "where table_name='forecast_documents' and column_name='amount_due'")) {
        r.next(); assertThat(r.getInt(1)).isEqualTo(18); assertThat(r.getInt(2)).isEqualTo(2);
      }
      c.rollback();
    }
  }

  @Test void frUc04_17_syncDiagnosticsRejectBodiesAndInvalidState() throws Exception {
    try (var c = connect(); var s = c.createStatement()) {
      c.setAutoCommit(false);
      UUID entity = UUID.randomUUID(), run = UUID.randomUUID();
      s.execute("insert into public.entities(id,code,display_name) values ('" + entity + "','sync_uc4','Sync')");
      s.execute("insert into public.forecast_sync_runs(id,entity_id,trigger,status) values ('" + run + "','" + entity
          + "','SCHEDULED','RUNNING')");
      assertRejected(c, s, "update public.forecast_sync_runs set error_code='raw provider body'");
      assertRejected(c, s, "update public.forecast_sync_runs set status='UNKNOWN'");
      assertRejected(c, s, "update public.forecast_sync_runs set documents_seen=-1");
      c.rollback();
    }
  }

  private static void assertRejected(Connection c, Statement s, String sql) throws SQLException {
    var savepoint = c.setSavepoint();
    assertThatThrownBy(() -> s.execute(sql)).isInstanceOf(SQLException.class);
    c.rollback(savepoint);
  }
}
