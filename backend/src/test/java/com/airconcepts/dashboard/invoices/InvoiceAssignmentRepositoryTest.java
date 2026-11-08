package com.airconcepts.dashboard.invoices;

import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.DockerRequirement;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

class InvoiceAssignmentRepositoryTest {
  private static PostgreSQLContainer postgres;
  private static JdbcTemplate jdbc;
  private static InvoiceAssignmentRepository repository;

  @BeforeAll static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")), DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine"); postgres.start();
    var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(ds);
    jdbc.execute("create role anon; create role authenticated; create role service_role; create role supabase_auth_admin; create schema auth; create table auth.users(id uuid primary key)");
    try (var files = Files.list(Path.of("..", "supabase", "migrations"))) {
      for (var file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) jdbc.execute(Files.readString(file));
    }
    var factory = new ProxyFactory(new InvoiceAssignmentRepository(jdbc));
    factory.setProxyTargetClass(true);
    factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
    repository = (InvoiceAssignmentRepository) factory.getProxy();
  }
  @AfterAll static void stop() { if (postgres != null) postgres.stop(); }

  @Test void frUc15_07_19_compositeOwnershipRetainsInactiveHistoryAndAllowsUnassignment() {
    var first = connection(); var second = connection(); var actor = staff("DIRECTOR", true);
    var owner = staff("STANDARD_USER", true); var id = UUID.randomUUID();
    repository.assign(first, actor, id, owner);
    assertThat(repository.owners(first.entityId(), List.of(id))).containsKey(id);
    assertThat(repository.owners(second.entityId(), List.of(id))).isEmpty();
    repository.assign(second, actor, id, actor);
    jdbc.update("update public.staff_users set active=false where id=?", owner);
    assertThat(repository.owners(first.entityId(), List.of(id)).get(id).active()).isFalse();
    assertThat(repository.owners(second.entityId(), List.of(id)).get(id).id()).isEqualTo(actor);
    assertThatThrownBy(() -> repository.assign(first, actor, UUID.randomUUID(), owner)).isInstanceOf(ApiFailure.class);
    assertThat(repository.assign(first, actor, id, null)).isNull();
    assertThat(repository.owners(first.entityId(), List.of(id))).isEmpty();
  }

  @Test void frUc15_07_18_rechecksActorTargetAndConnectionAtCommitBoundary() {
    var access = connection(); var invoice = UUID.randomUUID();
    var standard = staff("STANDARD_USER", true); var inactive = staff("DIRECTOR", false);
    for (UUID actor : List.of(standard, inactive)) {
      assertThatThrownBy(() -> repository.assign(access, actor, invoice, standard)).isInstanceOf(AccessDeniedException.class);
    }
    var director = staff("DIRECTOR", true);
    assertThatThrownBy(() -> repository.assign(access, director, invoice, UUID.randomUUID())).isInstanceOf(ApiFailure.class);
    var obsolete = new TenantAccess(access.entityId(), UUID.randomUUID(), access.grantId(), "synthetic-access");
    assertThatThrownBy(() -> repository.assign(obsolete, director, invoice, standard)).isInstanceOf(ApiFailure.class);
    assertThat(repository.owners(access.entityId(), List.of(invoice))).isEmpty();
  }

  @Test void frUc15_07_assignmentTableHasRlsAndNoBrowserPrivileges() {
    assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid='public.invoice_assignments'::regclass", Boolean.class)).isTrue();
    for (String role : List.of("anon", "authenticated")) {
      assertThat(jdbc.queryForObject("select has_table_privilege(?,'public.invoice_assignments','SELECT,INSERT,UPDATE,DELETE')", Boolean.class, role)).isFalse();
    }
  }

  private static UUID staff(String role, boolean active) {
    var id = UUID.randomUUID(); jdbc.update("insert into auth.users(id) values(?)", id);
    jdbc.update("insert into public.staff_users(id,email,display_name,role,active) values(?,?,'Staff',?,?)", id,id+"@example.invalid",role,active);
    return id;
  }
  private static TenantAccess connection() {
    var entity = UUID.randomUUID(); var tenant = UUID.randomUUID(); var grant = UUID.randomUUID();
    jdbc.update("insert into public.entities(id,code,display_name) values(?,?,'Entity')", entity,"test-"+entity);
    jdbc.update("insert into public.xero_grants(id,access_ciphertext,refresh_ciphertext,expires_at,status) values(?,?,?,now(),'ACTIVE')",
        grant,"synthetic-ciphertext".repeat(3),"synthetic-ciphertext".repeat(3));
    jdbc.update("insert into public.xero_entity_connections(entity_id,grant_id,tenant_id,connection_id,tenant_name,verified_at) values(?,?,?,?,'Organisation',now())",
        entity,grant,tenant,UUID.randomUUID());
    return new TenantAccess(entity,tenant,grant,"synthetic-access");
  }
}
