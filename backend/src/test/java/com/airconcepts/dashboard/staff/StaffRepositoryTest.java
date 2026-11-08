package com.airconcepts.dashboard.staff;

import static org.assertj.core.api.Assertions.*;

import com.airconcepts.dashboard.DockerRequirement;
import com.airconcepts.dashboard.security.JdbcRoleLookup;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

class StaffRepositoryTest {
  private static PostgreSQLContainer postgres;
  private static JdbcTemplate jdbc;
  private static StaffRepository repository;
  private static TransactionTemplate transaction;

  @BeforeAll
  static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")), DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
    var datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(datasource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(datasource));
    repository = new StaffRepository(jdbc);
    jdbc.execute("create role anon; create role authenticated; create role service_role; create role supabase_auth_admin; create schema auth; create table auth.users(id uuid primary key)");
    try (var files = Files.list(Path.of("..", "supabase", "migrations"))) {
      for (var file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) jdbc.execute(Files.readString(file));
    }
  }

  @AfterAll
  static void stop() { if (postgres != null) postgres.stop(); }

  @Test
  void frUc15_16_retryReservesSameUuidAndConflictingRequestIsRejected() {
    String email = UUID.randomUUID() + "@example.invalid";
    var first = transaction.execute(s -> repository.reserve(email, "Staff", Role.STANDARD_USER));
    StaffProvision retried = transaction.execute(s -> repository.reserve(email, "Staff", Role.STANDARD_USER));
    assertThat(retried).isEqualTo(first);
    assertThatThrownBy(() -> transaction.execute(s -> repository.reserve(email, "Changed", Role.DIRECTOR)))
        .isInstanceOf(ApiFailure.class);
    assertThat(repository.findByEmail(email)).isEmpty();
    jdbc.update("insert into auth.users(id) values (?)", first.id());
    assertThat(transaction.execute(s -> repository.complete(first)).active()).isTrue();
    assertThat(transaction.execute(s -> repository.complete(first)).id()).isEqualTo(first.id());
  }

  @Test
  void frUc15_16_18_lastDirectorCannotBeDeactivatedOrDemotedAndRoleLookupSeesCommittedRevocation() {
    transaction.executeWithoutResult(status -> {
      var director = insert(Role.DIRECTOR);
      var standard = insert(Role.STANDARD_USER);
      assertThatThrownBy(() -> repository.update(director, director, "Director", Role.DIRECTOR, false))
          .isInstanceOf(ApiFailure.class);
      assertThatThrownBy(() -> repository.update(director, director, "Director", Role.STANDARD_USER, true))
          .isInstanceOf(ApiFailure.class);
      repository.update(director, standard, "Staff", Role.STANDARD_USER, false);
      assertThat(new JdbcRoleLookup(jdbc).findRole(standard.toString())).isEmpty();
      assertThat(repository.findById(standard).orElseThrow().active()).isFalse();
      assertThat(repository.activeChoices()).noneMatch(option -> option.id().equals(standard));
      status.setRollbackOnly();
    });
  }

  @Test
  void frUc15_18_concurrentDirectorsCannotBothDeactivateTheLastAccounts() throws Exception {
    var first = insert(Role.DIRECTOR);
    var second = insert(Role.DIRECTOR);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var attempts = new ArrayList<Future<Boolean>>();
      for (var id : List.of(first, second)) {
        attempts.add(executor.submit(() -> {
          start.await();
          try {
            transaction.execute(s -> repository.update(id, id, "Director", Role.DIRECTOR, false));
            return true;
          } catch (ApiFailure blocked) { return false; }
        }));
      }
      start.countDown();
      int changed = 0;
      for (var attempt : attempts) if (attempt.get(10, TimeUnit.SECONDS)) changed++;
      assertThat(changed).isEqualTo(1);
      assertThat(jdbc.queryForObject("select count(*) from public.staff_users where active and role='DIRECTOR'", Integer.class)).isEqualTo(1);
    } finally {
      jdbc.update("delete from public.staff_users where id in (?, ?)", first, second);
      jdbc.update("delete from auth.users where id in (?, ?)", first, second);
    }
  }

  private static UUID insert(Role role) {
    var id = UUID.randomUUID();
    jdbc.update("insert into auth.users(id) values (?)", id);
    jdbc.update("insert into public.staff_users(id,email,display_name,role) values (?, ?, ?, ?)",
        id, id + "@example.invalid", "Staff", role.name());
    return id;
  }
}
