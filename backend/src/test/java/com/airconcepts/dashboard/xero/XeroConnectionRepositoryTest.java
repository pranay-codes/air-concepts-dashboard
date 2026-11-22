package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.DockerRequirement;
import com.airconcepts.dashboard.web.ApiFailure;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

class XeroConnectionRepositoryTest {
  private static PostgreSQLContainer postgres;
  private static JdbcTemplate jdbc;
  private static XeroConnectionRepository repository;
  private static DataSourceTransactionManager manager;
  private static TransactionTemplate tx;
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);
  private static final TokenCipher CIPHER = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));

  @BeforeAll
  static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")), DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine"); postgres.start();
    var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(ds); jdbc.setQueryTimeout(15);
    manager = new DataSourceTransactionManager(ds); tx = new TransactionTemplate(manager);
    repository = new XeroConnectionRepository(jdbc);
    jdbc.execute("create role anon; create role authenticated; create role service_role; create role supabase_auth_admin; create schema auth; create table auth.users(id uuid primary key)");
    try (var files = Files.list(Path.of("..", "supabase", "migrations"))) {
      for (var file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) jdbc.execute(Files.readString(file));
    }
  }
  @AfterAll static void stop() { if (postgres != null) postgres.stop(); }

  @Test void gl01_grantedScopesRoundTripAndMissingEvidenceRemainsUnknown() {
    tx.executeWithoutResult(status -> {
      var id = UUID.randomUUID();
      insertGrant(id);
      var existing = repository.lockGrant(id);
      assertThat(existing.grantedScopes()).isNull();
      var withScopes = new StoredGrant(id, existing.accessCiphertext(), existing.refreshCiphertext(), existing.expiresAt(),
          GrantState.ACTIVE, null, null, null, Set.of("accounting.invoices.read", "accounting.contacts.read"));
      repository.saveRotated(withScopes);
      assertThat(repository.lockGrant(id).grantedScopes()).containsExactlyInAnyOrder("accounting.invoices.read", "accounting.contacts.read");
      repository.saveRotated(new StoredGrant(id, existing.accessCiphertext(), existing.refreshCiphertext(), existing.expiresAt(),
          GrantState.ACTIVE, null, null, null, null));
      assertThat(repository.lockGrant(id).grantedScopes()).isNull();
      status.setRollbackOnly();
    });
  }

  @Test
  void frUc15_10_stateIsSingleUseExpiringAndInvalidAfterDirectorDeactivation() {
    tx.executeWithoutResult(status -> {
      var director = director(); var entity = entity();
      var first = repository.createAttempt(UUID.randomUUID(), director, entity, hash(), CLOCK.instant().plusSeconds(600));
      String stateHash = jdbc.queryForObject("select state_hash from public.xero_oauth_attempts where id=?", String.class, first.id());
      assertThat(repository.claimAttempt(stateHash, CLOCK.instant())).isPresent();
      assertThat(repository.claimAttempt(stateHash, CLOCK.instant())).isEmpty();
      String expired = hash();
      repository.createAttempt(UUID.randomUUID(), director, entity, expired, CLOCK.instant().minusSeconds(1));
      assertThat(repository.claimAttempt(expired, CLOCK.instant())).isEmpty();
      String revoked = hash();
      repository.createAttempt(UUID.randomUUID(), director, entity, revoked, CLOCK.instant().plusSeconds(600));
      jdbc.update("update public.staff_users set active=false where id=?", director);
      assertThat(repository.claimAttempt(revoked, CLOCK.instant())).isEmpty();
      status.setRollbackOnly();
    });
  }

  @Test
  void frUc15_03_duplicateTenantRejectsRemapAndReconsentSharesNewGrantWithoutChangingTenantIds() {
    tx.executeWithoutResult(status -> {
      var director = director(); var firstEntity = entity(); var secondEntity = entity();
      var firstTenant = new XeroTenant(UUID.randomUUID(), UUID.randomUUID(), "First");
      var secondTenant = new XeroTenant(UUID.randomUUID(), UUID.randomUUID(), "Additional");
      var first = ready(director, firstEntity); var second = ready(director, secondEntity);
      repository.completeMapping(first, firstTenant, List.of(firstTenant), CLOCK.instant());
      assertThatThrownBy(() -> repository.completeMapping(second, firstTenant, List.of(firstTenant), CLOCK.instant()))
          .isInstanceOf(ApiFailure.class);
      assertThat(repository.connection(secondEntity)).isEmpty();
      repository.completeMapping(second, secondTenant, List.of(firstTenant, secondTenant), CLOCK.instant());
      assertThat(repository.connection(firstEntity).orElseThrow().tenantId()).isEqualTo(firstTenant.tenantId());
      assertThat(repository.connection(firstEntity).orElseThrow().grantId()).isEqualTo(second.grantId());
      assertThat(repository.connection(secondEntity).orElseThrow().grantId()).isEqualTo(second.grantId());
      status.setRollbackOnly();
    });
  }

  @Test
  void frUc15_10_twoConcurrentReadsOfSharedGrantRefreshOnceAndFailureStateCommits() throws Exception {
    var id = UUID.randomUUID(); insertGrant(id);
    var provider = mock(XeroOAuthClient.class);
    var refreshing = new CountDownLatch(1); var release = new CountDownLatch(1);
    when(provider.refresh(anyString())).thenAnswer(call -> {
      refreshing.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not release refresh");
      return new XeroTokens("next-synthetic-access", "next-synthetic-refresh", CLOCK.instant().plusSeconds(1800));
    });
    var service = proxied(provider);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.access(id));
      assertThat(refreshing.await(5, TimeUnit.SECONDS)).isTrue();
      var second = executor.submit(() -> service.access(id));
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean waiting = false;
        while (!waiting && System.nanoTime() < deadline) {
          Integer waits = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() "
              + "and wait_event_type='Lock' and query like '%xero_grants%for update%'", Integer.class);
          waiting = waits != null && waits > 0;
          if (!waiting) Thread.sleep(25);
        }
        assertThat(waiting).as("second request waits on the shared grant row, not another token refresh").isTrue();
        verify(provider, times(1)).refresh(anyString());
      } finally { release.countDown(); }
      assertThat(first.get(15, TimeUnit.SECONDS).accessToken()).isEqualTo("next-synthetic-access");
      assertThat(second.get(15, TimeUnit.SECONDS).accessToken()).isEqualTo("next-synthetic-access");
      verify(provider, times(1)).refresh(anyString());
    }
    jdbc.update("update public.xero_grants set expires_at=? where id=?", java.sql.Timestamp.from(CLOCK.instant().minusSeconds(1)), id);
    when(provider.refresh(anyString())).thenThrow(new XeroProviderFailure("XERO_RECONNECT_REQUIRED", "Reconnect", 400, true, false, null));
    assertThatThrownBy(() -> service.access(id)).isInstanceOf(XeroProviderFailure.class);
    assertThat(jdbc.queryForObject("select status from public.xero_grants where id=?", String.class, id)).isEqualTo("NEEDS_RECONNECTION");
  }

  @Test
  void frUc15_10_browserRolesCannotReadAnyGrantStateOrTenantMapping() {
    for (String table : List.of("public.xero_grants", "public.xero_oauth_attempts", "public.xero_entity_connections")) {
      assertThat(jdbc.queryForObject("select has_table_privilege('authenticated',?,'SELECT')", Boolean.class, table)).isFalse();
      assertThat(jdbc.queryForObject("select has_table_privilege('anon',?,'SELECT')", Boolean.class, table)).isFalse();
      assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid=?::regclass", Boolean.class, table)).isTrue();
    }
  }

  @Test
  void frUc15_14_recoveryDoesNotEraseANewerErrorOrUnexpiredRetryDeadline() {
    var id = UUID.randomUUID(); insertGrant(id);
    jdbc.update("update public.xero_grants set error_code='XERO_PROVIDER_FAILED',updated_at=? where id=?",
        java.sql.Timestamp.from(CLOCK.instant().minusSeconds(10)), id);
    repository.clearReadFailure(id, CLOCK.instant(), CLOCK.instant());
    assertThat(jdbc.queryForObject("select error_code from public.xero_grants where id=?", String.class, id)).isNull();
    jdbc.update("update public.xero_grants set error_code='XERO_RATE_LIMITED',retry_at=? where id=?",
        java.sql.Timestamp.from(CLOCK.instant().plusSeconds(120)), id);
    repository.clearReadFailure(id, CLOCK.instant(), CLOCK.instant());
    assertThat(jdbc.queryForObject("select error_code from public.xero_grants where id=?", String.class, id)).isEqualTo("XERO_RATE_LIMITED");
    jdbc.update("update public.xero_grants set retry_at=null,error_code='XERO_PROVIDER_FAILED',updated_at=? where id=?",
        java.sql.Timestamp.from(CLOCK.instant().plusSeconds(1)), id);
    repository.clearReadFailure(id, CLOCK.instant(), CLOCK.instant());
    assertThat(jdbc.queryForObject("select error_code from public.xero_grants where id=?", String.class, id)).isEqualTo("XERO_PROVIDER_FAILED");
  }

  private static XeroGrantService proxied(XeroOAuthClient provider) {
    var factory = new ProxyFactory(new XeroGrantService(repository, provider, CIPHER, CLOCK));
    factory.setProxyTargetClass(true);
    factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
    return (XeroGrantService) factory.getProxy();
  }

  @Test
  void frUc15_13_30_accountingDenialAndRateLimitsAreTenantScopedAndOldResultsCannotOverwriteNewState() {
    tx.executeWithoutResult(transaction -> {
      var director = director(); var firstEntity = entity(); var secondEntity = entity();
      var firstTenant = new XeroTenant(UUID.randomUUID(), UUID.randomUUID(), "First");
      var secondTenant = new XeroTenant(UUID.randomUUID(), UUID.randomUUID(), "Second");
      var first = ready(director, firstEntity); var second = ready(director, secondEntity);
      repository.completeMapping(first, firstTenant, List.of(firstTenant), CLOCK.instant().minusSeconds(10));
      repository.completeMapping(second, secondTenant, List.of(firstTenant, secondTenant), CLOCK.instant().minusSeconds(10));
      var a = new TenantAccess(firstEntity, firstTenant.tenantId(), second.grantId(), "synthetic-access");
      var b = new TenantAccess(secondEntity, secondTenant.tenantId(), second.grantId(), "synthetic-access");
      var health = new XeroReadHealth(jdbc, CLOCK);
      // Independent healthy parallel reads must not invalidate each other.
      health.success(a, CLOCK.instant().minusSeconds(2));
      health.success(a, CLOCK.instant().minusSeconds(1));
      health.failure(a, CLOCK.instant(), new com.airconcepts.dashboard.web.ProviderFailure("XERO_ORGANISATION_ACCESS_DENIED", "Denied", 403));
      assertThatThrownBy(() -> health.check(a)).isInstanceOf(ApiFailure.class);
      health.check(b);
      assertThat(repository.connection(firstEntity).orElseThrow().grantState()).isEqualTo(GrantState.NEEDS_RECONNECTION);
      assertThat(repository.connection(secondEntity).orElseThrow().grantState()).isEqualTo(GrantState.ACTIVE);
      assertThatThrownBy(() -> health.success(a, CLOCK.instant().minusSeconds(1))).isInstanceOf(ApiFailure.class);
      // A replacement mapping resets the read failure; a late failure from its predecessor is ignored.
      var replacement = ready(director, firstEntity);
      repository.completeMapping(replacement, firstTenant, List.of(firstTenant), CLOCK.instant().plusSeconds(1));
      health.failure(a, CLOCK.instant(), new com.airconcepts.dashboard.web.ProviderFailure("XERO_RATE_LIMITED", "Wait", 429, 60));
      assertThat(repository.connection(firstEntity).orElseThrow().errorCode()).isNull();
      var next = new TenantAccess(firstEntity, firstTenant.tenantId(), replacement.grantId(), "synthetic-access");
      health.failure(next, CLOCK.instant().plusSeconds(2), new com.airconcepts.dashboard.web.ProviderFailure("XERO_RATE_LIMITED", "Wait", 429, 60));
      assertThatThrownBy(() -> health.check(next)).isInstanceOfSatisfying(com.airconcepts.dashboard.web.ProviderFailure.class,
          e -> assertThat(e.retryAfterSeconds()).isEqualTo(60));
      assertThatThrownBy(() -> health.success(next, CLOCK.instant().plusSeconds(2))).isInstanceOf(ApiFailure.class);
      transaction.setRollbackOnly();
    });
  }
  private static OAuthAttempt ready(UUID director, UUID entity) {
    String hash = hash();
    repository.createAttempt(UUID.randomUUID(), director, entity, hash, CLOCK.instant().plusSeconds(600));
    var claimed = repository.claimAttempt(hash, CLOCK.instant()).orElseThrow();
    var grant = UUID.randomUUID();
    repository.saveGrant(claimed, stored(grant), CLOCK.instant());
    return repository.ownedAttempt(claimed.id(), director).orElseThrow();
  }
  private static StoredGrant stored(UUID id) {
    return new StoredGrant(id, CIPHER.encrypt(id,"access","synthetic-access"), CIPHER.encrypt(id,"refresh","synthetic-refresh"),
        CLOCK.instant().minusSeconds(1), GrantState.ACTIVE, null, null, null);
  }
  private static void insertGrant(UUID id) {
    var grant = stored(id);
    jdbc.update("insert into public.xero_grants(id,access_ciphertext,refresh_ciphertext,expires_at,status) values(?,?,?,?,'ACTIVE')",
        id, grant.accessCiphertext(), grant.refreshCiphertext(), java.sql.Timestamp.from(grant.expiresAt()));
  }
  private static String hash() { return UUID.randomUUID().toString().replace("-", "").repeat(2); }
  private static UUID entity() {
    var id = UUID.randomUUID(); jdbc.update("insert into public.entities(id,code,display_name) values(?,?,'Entity')", id, "test-" + id); return id;
  }
  private static UUID director() {
    var id = UUID.randomUUID(); jdbc.update("insert into auth.users(id) values(?)", id);
    jdbc.update("insert into public.staff_users(id,email,display_name,role) values(?,?,'Director','DIRECTOR')", id,id+"@example.invalid"); return id;
  }
}
