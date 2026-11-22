package com.airconcepts.dashboard.vwork;

import static org.assertj.core.api.Assertions.*;

import com.airconcepts.dashboard.DockerRequirement;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.workitems.settings.GreenLineSettingsRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** CI-only disposable PostgreSQL tests. Never run against hosted or local user data. */
class VworkConnectionRepositoryTest {
  private static PostgreSQLContainer postgres;
  private static JdbcTemplate jdbc;
  private static TransactionTemplate transaction;
  private static VworkConnectionRepository repository;

  @BeforeAll static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")), DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
    var data = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(data);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
    repository = new VworkConnectionRepository(jdbc);
    jdbc.execute("create role anon; create role authenticated; create role service_role bypassrls; create role supabase_auth_admin; "
        + "create schema auth; create table auth.users(id uuid primary key)");
    try (var migrations = Files.list(Path.of("..", "supabase", "migrations"))) {
      for (var file : migrations.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) jdbc.execute(Files.readString(file));
    }
  }

  @AfterAll static void stop() { if (postgres != null) postgres.stop(); }

  @Test void gl26_connectionIsSingletonEncryptedAuditedAndProtectedAgainstStaleWrites() {
    var director = staff("DIRECTOR");
    var staff = staff("STANDARD_USER");
    var active = new VworkConnectionRepository.Change("synthetic-ciphertext", "ACTIVE", null, null, null);
    write(director, null, active);
    var initial = repository.read().orElseThrow();
    assertThat(initial.ciphertext()).isEqualTo("synthetic-ciphertext");
    assertThat(jdbc.queryForObject("select count(*) from public.vwork_connection_events", Integer.class)).isEqualTo(1);
    assertThatThrownBy(() -> write(staff, initial.revision(), active)).isInstanceOf(AccessDeniedException.class);
    write(director, initial.revision(), new VworkConnectionRepository.Change("new-ciphertext", "UNVERIFIED", "VWORK_UNAVAILABLE", null, null));
    assertThatThrownBy(() -> write(director, initial.revision(), active))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("VWORK_CONNECTION_CHANGED"));
    var current = repository.read().orElseThrow();
    write(director, current.revision(), new VworkConnectionRepository.Change(null, "DISCONNECTED", null, null, null));
    assertThat(repository.read().orElseThrow().ciphertext()).isNull();
    assertThat(jdbc.queryForObject("select count(*) from public.vwork_connection_events", Integer.class)).isEqualTo(3);
    assertThat(jdbc.queryForObject("select string_agg(status, ',') from public.vwork_connection_events", String.class))
        .doesNotContain("ciphertext");
    assertThatThrownBy(() -> jdbc.update("insert into public.vwork_connection(id,status,configured_by) values (2,'DISCONNECTED',?)", director))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("select has_table_privilege('authenticated','public.vwork_connection','SELECT')", Boolean.class)).isFalse();
    assertThat(jdbc.queryForObject("select has_table_privilege('service_role','public.vwork_connection_events','UPDATE')", Boolean.class)).isFalse();
    assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid='public.vwork_connection'::regclass", Boolean.class)).isTrue();
  }

  @Test void gl06_templatesCannotCrossEntitiesAndAreAuditedAtomically() {
    var director = staff("DIRECTOR");
    var actor = new CurrentUser(director.toString(), Role.DIRECTOR);
    var first = entity();
    var second = entity();
    var service = new VworkTemplatesService(new VworkTemplatesRepository(jdbc), new GreenLineSettingsRepository(jdbc));
    var ref = new VworkTemplatesRepository.Template("synthetic-template", true);
    transaction.executeWithoutResult(tx -> {
      jdbc.execute("set local role service_role");
      assertThat(service.save(actor, first, List.of(ref))).containsExactly(ref);
    });
    var other = new VworkTemplatesRepository.Template("second-template", true);
    transaction.executeWithoutResult(tx -> {
      jdbc.execute("set local role service_role");
      assertThat(service.save(actor, first, List.of(new VworkTemplatesRepository.Template(ref.templateRef(), false), other)))
          .containsExactly(other, new VworkTemplatesRepository.Template(ref.templateRef(), false));
    });
    assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
      jdbc.execute("set local role service_role");
      service.save(actor, second, List.of(ref));
    })).isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("VWORK_TEMPLATE_IN_USE"));
    assertThat(jdbc.queryForObject("select entity_id from public.vwork_entity_templates where template_ref=?", UUID.class, ref.templateRef())).isEqualTo(first);
    assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where entity_id=? and event_type='SETTINGS_CHANGED'", Integer.class, first)).isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where entity_id=?", Integer.class, second)).isZero();
  }

  private static void write(UUID actor, String revision, VworkConnectionRepository.Change change) {
    transaction.executeWithoutResult(ignored -> {
      jdbc.execute("set local role service_role");
      repository.change(actor, revision, change);
    });
  }

  private static UUID staff(String role) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into auth.users(id) values (?)", id);
    jdbc.update("insert into public.staff_users(id,email,display_name,role) values (?,?,?,?)",
        id, id + "@example.invalid", "Synthetic user", role);
    return id;
  }

  private static UUID entity() {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into public.entities(id,code,display_name) values (?,?,?)", id, id.toString(), "Synthetic entity");
    return id;
  }
}
