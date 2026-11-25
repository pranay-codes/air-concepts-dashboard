package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.airconcepts.dashboard.DockerRequirement;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** CI-only disposable PostgreSQL coverage; never run against a hosted database. */
class OverviewServicePostgresTest {
  private static PostgreSQLContainer postgres;
  private static JdbcTemplate jdbc;
  private static TransactionTemplate transaction;
  private static OverviewService service;
  private static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");

  @BeforeAll static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")), DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
    var datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(datasource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(datasource));
    var clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new OverviewService(jdbc, new WorkItemReadService(jdbc, clock), clock);
    jdbc.execute("create role anon; create role authenticated; create role service_role bypassrls; create role supabase_auth_admin; "
        + "create schema auth; create table auth.users(id uuid primary key)");
    try (var migrations = Files.list(Path.of("..", "supabase", "migrations"))) {
      for (var file : migrations.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) jdbc.execute(Files.readString(file));
    }
  }

  @AfterAll static void stop() { if (postgres != null) postgres.stop(); }

  /** Every case starts from an empty database state and is rolled back. */
  private static void rolledBack(Consumer<CurrentUser> test) {
    transaction.executeWithoutResult(tx -> {
      var director = staff("DIRECTOR", "Synthetic director");
      test.accept(new CurrentUser(director.toString(), Role.DIRECTOR));
      tx.setRollbackOnly();
    });
  }

  @Test void gl24_glDir01_totalsMatchStoredStagesAndOwners() {
    rolledBack(director -> {
      var organisation = organisation(true);
      var owner = staff("FINANCIAL_ADMIN", "Synthetic finance");
      item(organisation, "QUOTED", null, owner, null);
      item(organisation, "QUOTED", null, null, null);
      item(organisation, "JOB_COMPLETED", null, owner, null);
      item(organisation, "INVOICE_CREATED", null, owner, UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var result = service.get(director, null, null, null);
      assertThat(result.stages()).filteredOn(s -> s.stage() == WorkItemStage.QUOTED).singleElement().satisfies(s -> {
        assertThat(s.count()).isEqualTo(2);
        assertThat(s.totals()).containsExactly(new OverviewService.OverviewMoney("NZD", "200.0000"));
      });
      assertThat(result.stages().stream().mapToInt(OverviewService.OverviewStageTotal::count).sum()).isEqualTo(4);
      assertThat(result.openItems()).hasSize(4);
      assertThat(result.openItems()).filteredOn(i -> i.summary().owner() == null).hasSize(1);
      assertThat(result.uninvoiced()).hasSize(1);
      assertThat(result.unsent()).hasSize(1);
    });
  }

  @Test void glDir04_glDir05_countsCompletionsAndSentInvoicesWithCredit() {
    rolledBack(director -> {
      var organisation = organisation(true);
      var manager = staff("STANDARD_USER", "Synthetic manager");
      var finance = staff("FINANCIAL_ADMIN", "Synthetic finance");
      var invoiced = item(organisation, "INVOICED", null, finance, UUID.randomUUID());
      jdbc.update("update public.work_items set technician_name='Synthetic technician' where id=?", invoiced);
      event(organisation, invoiced, "VWORK_SYNC", "SCHEDULED", "JOB_COMPLETED", manager, "2026-11-10T00:00:00Z");
      event(organisation, invoiced, "XERO_SYNC", "JOB_COMPLETED", "INVOICED", finance, "2026-11-12T00:00:00Z");
      var paid = item(organisation, "CLOSED", "PAID", finance, UUID.randomUUID());
      event(organisation, paid, "XERO_SYNC", "INVOICE_CREATED", "CLOSED", finance, "2026-11-13T00:00:00Z");
      jdbc.execute("set local role service_role");
      var result = service.get(director, organisation.id(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-25"));
      assertThat(result.completedByOwner()).containsExactly(
          new OverviewService.OverviewPersonCount(manager, "Synthetic manager", 1));
      assertThat(result.completedByTechnician()).containsExactly(
          new OverviewService.OverviewPersonCount(null, "Synthetic technician", 1));
      assertThat(result.invoicesSent().count()).isEqualTo(1);
      assertThat(result.invoicesSent().paidNotSeenSent()).isEqualTo(1);
      assertThat(result.closed()).containsExactly(new OverviewService.OverviewClosedTotal("PAID", 1));
      var earlier = service.get(director, organisation.id(), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
      assertThat(earlier.invoicesSent().count()).isZero();
      assertThat(earlier.completedTotal()).isZero();
    });
  }

  @Test void glDir06_excludesDisabledAndArchivedOrganisationsAndFiltersByEntity() {
    rolledBack(director -> {
      var visible = organisation(true);
      var other = organisation(true);
      var disabled = organisation(false);
      var archived = organisation(true);
      item(visible, "QUOTED", null, null, null);
      item(other, "QUOTED", null, null, null);
      item(disabled, "QUOTED", null, null, null);
      item(archived, "QUOTED", null, null, null);
      jdbc.update("update public.entities set archived_at=now() where id=?", archived.id());
      jdbc.execute("set local role service_role");
      assertThat(service.get(director, null, null, null).openItems()).extracting(i -> i.summary().entityId())
          .containsExactlyInAnyOrder(visible.id(), other.id());
      assertThat(service.get(director, visible.id(), null, null).openItems()).extracting(i -> i.summary().entityId())
          .containsExactly(visible.id());
    });
  }

  @Test void glDir06_changedTenantFailsClosed() {
    rolledBack(director -> {
      var organisation = organisation(true);
      item(organisation, "QUOTED", null, null, null);
      jdbc.update("update public.xero_entity_connections set tenant_id=? where entity_id=?", UUID.randomUUID(), organisation.id());
      jdbc.execute("set local role service_role");
      assertThatThrownBy(() -> service.get(director, null, null, null)).isInstanceOf(ApiFailure.class)
          .extracting(failure -> ((ApiFailure) failure).code()).isEqualTo("XERO_TENANT_CHANGED");
    });
  }

  private record Organisation(UUID id, UUID tenant) {}

  private static Organisation organisation(boolean enabled) {
    var id = UUID.randomUUID();
    var grant = UUID.randomUUID();
    var tenant = UUID.randomUUID();
    jdbc.update("insert into public.entities(id,code,display_name) values (?,?,?)", id, "test-" + id, "Synthetic entity");
    jdbc.update("insert into public.xero_grants(id,access_ciphertext,refresh_ciphertext,expires_at,status) values (?,?,?,now(),'ACTIVE')",
        grant, "synthetic-access-ciphertext-padded-to-schema-minimum", "synthetic-refresh-ciphertext-padded-to-schema-minimum");
    jdbc.update("insert into public.xero_entity_connections(entity_id,grant_id,tenant_id,connection_id,tenant_name,verified_at) values (?,?,?,?,?,now())",
        id, grant, tenant, UUID.randomUUID(), "Synthetic");
    jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,?)", id, enabled);
    return new Organisation(id, tenant);
  }

  private static UUID staff(String role, String name) {
    var id = UUID.randomUUID();
    jdbc.update("insert into auth.users(id) values (?)", id);
    jdbc.update("insert into public.staff_users(id,email,display_name,role) values (?,?,?,?)", id, id + "@example.invalid", name, role);
    return id;
  }

  private static UUID item(Organisation organisation, String stage, String closeReason, UUID owner, UUID invoice) {
    var id = UUID.randomUUID();
    jdbc.update("""
        insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,xero_quote_status,quote_total,
          quote_currency,quote_date,stage,close_reason,owner_id,xero_invoice_id,invoice_total)
        values (?,?,?,?,'Q-1','ACCEPTED',100,'NZD','2026-11-01',?,?,?,?,?)
        """, id, organisation.id(), organisation.tenant(), UUID.randomUUID(), stage, closeReason, owner, invoice,
        invoice == null ? null : new java.math.BigDecimal("115"));
    return id;
  }

  private static void event(Organisation organisation, UUID item, String source, String from, String to, UUID owner, String at) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,source,event_type,from_stage,to_stage,from_owner_id,at,before,after)
        values (?,?,?,'STAGE_CHANGED',?,?,?,?,
          jsonb_build_object('jobStatus',case when ?='VWORK_SYNC' then 'STARTED' end),
          jsonb_build_object('jobStatus',case when ?='VWORK_SYNC' then 'COMPLETED' end))
        """, organisation.id(), item, source, from, to, owner, Timestamp.from(Instant.parse(at)), source, source);
  }
}
