package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.airconcepts.dashboard.DockerRequirement;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import com.airconcepts.dashboard.workitems.WorkItemService;
import com.airconcepts.dashboard.workitems.WorkItemReadService;
import com.airconcepts.dashboard.workitems.WorkItemAction;
import com.airconcepts.dashboard.workitems.MyWorkService;
import com.airconcepts.dashboard.workitems.WorkItemListQuery;
import com.airconcepts.dashboard.workitems.WorkItemListService;
import com.airconcepts.dashboard.workitems.settings.GreenLineSettingsRepository;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CI-only disposable PostgreSQL coverage; never run against a hosted database. */
class QuoteMirrorRepositoryTest {
  private static PostgreSQLContainer postgres;
  private static JdbcTemplate jdbc;
  private static TransactionTemplate transaction;
  private static QuoteMirrorService service;
  private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

  @BeforeAll static void start() throws Exception {
    DockerRequirement.check("true".equals(System.getenv("CI")), DockerClientFactory.instance().isDockerAvailable());
    postgres = new PostgreSQLContainer("postgres:17-alpine");
    postgres.start();
    var datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(datasource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(datasource));
    service = new QuoteMirrorService(new QuoteMirrorRepository(jdbc), Clock.fixed(NOW, ZoneOffset.UTC));
    jdbc.execute("create role anon; create role authenticated; create role service_role bypassrls; create role supabase_auth_admin; "
        + "create schema auth; create table auth.users(id uuid primary key)");
    try (var migrations = Files.list(Path.of("..", "supabase", "migrations"))) {
      for (var file : migrations.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) jdbc.execute(Files.readString(file));
    }
  }

  @AfterAll static void stop() { if (postgres != null) postgres.stop(); }

  @Test void gl02_gl07_gl08_mirrorIsIdempotentAndPreservesUserOwnedFields() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var mapping = mapping(entity);
      var owner = staff("FINANCIAL_ADMIN");
      var manager = staff("STANDARD_USER");
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled,default_owner_id,service_manager_id) values (?,true,?,?)",
          entity, owner, manager);
      var id = UUID.randomUUID();
      var sent = quote(id, XeroQuoteClient.Status.SENT, "J-123", NOW.minusSeconds(120));
      jdbc.execute("set local role service_role");
      assertThat(service.apply(mapping, sent, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.CREATED);
      assertThat(service.apply(mapping, sent, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.UNCHANGED);
      jdbc.update("update public.work_items set notes='Keep this', follow_up_date='2026-11-25',version=version+1 where entity_id=? and xero_quote_id=?", entity, id);
      var accepted = quote(id, XeroQuoteClient.Status.ACCEPTED, "J-123", NOW.minusSeconds(60));
      assertThat(service.apply(mapping, accepted, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.UPDATED);
      var row = jdbc.queryForMap("select stage,owner_id,notes,follow_up_date,version from public.work_items where entity_id=? and xero_quote_id=?", entity, id);
      assertThat(row.get("stage")).isEqualTo("TO_SCHEDULE");
      assertThat(row.get("owner_id")).isEqualTo(manager);
      assertThat(row.get("notes")).isEqualTo("Keep this");
      assertThat(row.get("follow_up_date").toString()).isEqualTo("2026-11-25");
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where entity_id=?", Integer.class, entity)).isEqualTo(2);
      tx.setRollbackOnly();
    });
  }

  @Test void gl03_longReferenceIsRetainedWithoutCreatingAnUnsafeJobId() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var mapping = mapping(entity);
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      var id = UUID.randomUUID();
      var reference = "X".repeat(300);
      jdbc.execute("set local role service_role");
      assertThat(service.apply(mapping, quote(id, XeroQuoteClient.Status.SENT, reference, NOW), QuoteMirrorService.InvoiceMatch.UNCHECKED))
          .isEqualTo(QuoteMirrorService.Outcome.CREATED);
      var row = jdbc.queryForMap("select job_id,xero_quote_reference from public.work_items where entity_id=? and xero_quote_id=?", entity, id);
      assertThat(row.get("job_id")).isNull();
      assertThat(row.get("xero_quote_reference")).isEqualTo(reference);
      tx.setRollbackOnly();
    });
  }

  @Test void gl06_changedMappingIsRejectedWithoutCreatingAnItem() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var old = mapping(entity);
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      jdbc.update("update public.xero_entity_connections set tenant_id=? where entity_id=?", UUID.randomUUID(), entity);
      jdbc.execute("set local role service_role");
      assertThatThrownBy(() -> service.apply(old, quote(UUID.randomUUID(), XeroQuoteClient.Status.SENT, "J-1", NOW),
          QuoteMirrorService.InvoiceMatch.UNCHECKED)).isInstanceOf(ApiFailure.class)
          .extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_CONNECTION_CHANGED");
      assertThat(jdbc.queryForObject("select count(*) from public.work_items where entity_id=?", Integer.class, entity)).isZero();
      tx.setRollbackOnly();
    });
  }

  @Test void gl06_mappingShareLockBlocksConcurrentRemapUntilTransactionEnds() {
    var entity = entity();
    var mapping = mapping(entity);
    try (var executor = Executors.newSingleThreadExecutor()) {
      transaction.executeWithoutResult(tx -> {
        assertThat(new QuoteMirrorRepository(jdbc).lockMapping(mapping)).isTrue();
        var result = executor.submit(() -> {
          try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
               var setup = connection.createStatement();
               var update = connection.prepareStatement("update public.xero_entity_connections set tenant_id=? where entity_id=?")) {
            setup.execute("set lock_timeout='250ms'");
            update.setObject(1, UUID.randomUUID()); update.setObject(2, entity);
            update.executeUpdate();
            return "unexpected success";
          } catch (SQLException error) { return error.getSQLState(); }
        });
        try { assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("55P03"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        catch (ExecutionException | TimeoutException failure) { throw new AssertionError(failure); }
        tx.setRollbackOnly();
      });
    }
  }

  @Test void gl06_detailReadHoldsMappingStableUntilItsTransactionEnds() {
    var entity = entity();
    var mapping = mapping(entity);
    var actor = staff("STANDARD_USER");
    var id = UUID.randomUUID();
    jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
    jdbc.update("""
        insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,xero_quote_status,stage)
        values (?,?,?,?,'Q-1','SENT','QUOTED')
        """, id, entity, mapping.tenantId(), UUID.randomUUID());
    try (var executor = Executors.newSingleThreadExecutor()) {
      transaction.executeWithoutResult(tx -> {
        var read = new WorkItemReadService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(read.get(new CurrentUser(actor.toString(), Role.STANDARD_USER), entity, id).summary().id()).isEqualTo(id);
        var result = executor.submit(() -> {
          try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
               var setup = connection.createStatement();
               var update = connection.prepareStatement("update public.xero_entity_connections set tenant_id=? where entity_id=?")) {
            setup.execute("set lock_timeout='250ms'");
            update.setObject(1, UUID.randomUUID()); update.setObject(2, entity);
            update.executeUpdate();
            return "unexpected success";
          } catch (SQLException error) { return error.getSQLState(); }
        });
        try { assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("55P03"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        catch (ExecutionException | TimeoutException failure) { throw new AssertionError(failure); }
        tx.setRollbackOnly();
      });
    }
  }

  @Test void gl06_remapAndUnknownOriginBlockReadsEditsAndNewSync() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var old = mapping(entity);
      var actor = staff("FINANCIAL_ADMIN");
      var id = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      jdbc.update("""
          insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,xero_quote_status,stage,owner_id,notes)
          values (?,?,?,?,'Q-1','SENT','QUOTED',?,'Keep')
          """, id, entity, old.tenantId(), UUID.randomUUID(), actor);
      var newTenant = UUID.randomUUID();
      jdbc.update("update public.xero_entity_connections set tenant_id=? where entity_id=?", newTenant, entity);
      var current = new QuoteMirrorService.Mapping(entity, newTenant, old.grantId());
      jdbc.execute("set local role service_role");
      var user = new CurrentUser(actor.toString(), Role.FINANCIAL_ADMIN);
      var clock = Clock.fixed(NOW, ZoneOffset.UTC);
      var reads = new WorkItemReadService(jdbc, clock);
      var list = new WorkItemListService(jdbc, reads, clock);
      var query = new WorkItemListQuery(entity, null, null, null, null, null, "",
          WorkItemListQuery.Sort.stageEnteredAt, WorkItemListQuery.Direction.ASC, 1, 25);
      assertThatThrownBy(() -> reads.get(user, entity, id)).isInstanceOf(ApiFailure.class)
          .extracting(e -> ((ApiFailure)e).code()).isEqualTo("XERO_TENANT_CHANGED");
      assertThatThrownBy(() -> list.list(user, query)).isInstanceOf(ApiFailure.class)
          .extracting(e -> ((ApiFailure)e).code()).isEqualTo("XERO_TENANT_CHANGED");
      assertThatThrownBy(() -> new MyWorkService(jdbc, reads, clock).get(user, entity)).isInstanceOf(ApiFailure.class)
          .extracting(e -> ((ApiFailure)e).code()).isEqualTo("XERO_TENANT_CHANGED");
      assertThatThrownBy(() -> new WorkItemService(new WorkItemRepository(jdbc)).edit(user, entity, id, 0, null, "Changed"))
          .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure)e).code()).isEqualTo("WORK_ITEM_NOT_FOUND");
      assertThatThrownBy(() -> service.apply(current, quote(UUID.randomUUID(), XeroQuoteClient.Status.SENT, "J-2", NOW),
          QuoteMirrorService.InvoiceMatch.UNCHECKED)).isInstanceOf(ApiFailure.class)
          .extracting(e -> ((ApiFailure)e).code()).isEqualTo("XERO_TENANT_CHANGED");
      jdbc.update("update public.work_items set xero_tenant_id=null where entity_id=? and id=?", entity, id);
      assertThatThrownBy(() -> reads.get(user, entity, id)).isInstanceOf(ApiFailure.class)
          .extracting(e -> ((ApiFailure)e).code()).isEqualTo("XERO_TENANT_CHANGED");
      assertThat(jdbc.queryForObject("select notes from public.work_items where id=?", String.class, id)).isEqualTo("Keep");
      tx.setRollbackOnly();
    });
  }

  @Test void gl06_settingsMaintenanceDoesNotAlterItemsWithUnknownOrOtherTenantOrigin() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var mapping = mapping(entity);
      var actor = staff("DIRECTOR");
      var owner = staff("FINANCIAL_ADMIN");
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      var matching = UUID.randomUUID(); var unknown = UUID.randomUUID(); var other = UUID.randomUUID();
      jdbc.update("""
          insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,xero_quote_status,stage,stage_entered_at)
          values (?,?,?,?,'Q-1','ACCEPTED','TO_INVOICE','2026-11-18T00:00:00Z'),
                 (?,?,null,?,'Q-2','ACCEPTED','TO_INVOICE','2026-11-18T00:00:00Z'),
                 (?,?,?,?,'Q-3','ACCEPTED','TO_INVOICE','2026-11-18T00:00:00Z')
          """, matching, entity, mapping.tenantId(), UUID.randomUUID(), unknown, entity, UUID.randomUUID(),
          other, entity, UUID.randomUUID(), UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var settings = new GreenLineSettingsRepository(jdbc);
      settings.fillUnowned(entity, actor, owner, null);
      settings.recalculateLateDates(entity, 2);
      var valid = jdbc.queryForMap("select owner_id,late_after from public.work_items where id=?", matching);
      assertThat(valid.get("owner_id")).isEqualTo(owner);
      assertThat(valid.get("late_after")).isNotNull();
      for (var id : List.of(unknown, other)) {
        var row = jdbc.queryForMap("select owner_id,late_after from public.work_items where id=?", id);
        assertThat(row.get("owner_id")).isNull();
        assertThat(row.get("late_after")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=?", Integer.class, id)).isZero();
      }
      tx.setRollbackOnly();
    });
  }

  @Test void gl20_noInvoiceClosureChecksLinkedInvoiceAndWritesAuditInSameTransaction() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var mapping = mapping(entity);
      var actor = staff("FINANCIAL_ADMIN");
      var id = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      jdbc.update("""
          insert into public.work_items(id,entity_id,xero_quote_id,quote_number,xero_quote_status,stage,xero_invoice_id)
          values (?,?,?,'Q-1','ACCEPTED','TO_INVOICE',?)
          """, id, entity, UUID.randomUUID(), UUID.randomUUID());
      jdbc.update("update public.work_items set xero_tenant_id=? where entity_id=?", mapping.tenantId(), entity);
      jdbc.execute("set local role service_role");
      var itemService = new WorkItemService(new WorkItemRepository(jdbc));
      var user = new CurrentUser(actor.toString(), Role.FINANCIAL_ADMIN);
      int version = jdbc.queryForObject("select version from public.work_items where id=?", Integer.class, id);
      assertThatThrownBy(() -> itemService.closeNoInvoice(user, entity, id, version, "No charge"))
          .isInstanceOf(ApiFailure.class);
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=?", Integer.class, id)).isZero();
      jdbc.update("update public.work_items set xero_invoice_id=null where entity_id=? and id=?", entity, id);
      var operation = UUID.randomUUID();
      jdbc.update("""
          insert into public.external_operations(id,entity_id,work_item_id,kind,status,attempt,lookup_reference,request_fingerprint,requested_by)
          values (?,?,?,'XERO_CREATE_INVOICE','PENDING',1,'J-1',?,?)
          """, operation, entity, id, "a".repeat(64), actor);
      var detail = new WorkItemReadService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
      assertThat(detail.get(user, entity, id).allowedActions()).doesNotContain(WorkItemAction.NO_INVOICE_NEEDED);
      assertThatThrownBy(() -> itemService.closeNoInvoice(user, entity, id, version, "No charge"))
          .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("OPERATION_IN_PROGRESS"));
      jdbc.update("update public.external_operations set status='UNKNOWN' where id=?", operation);
      assertThatThrownBy(() -> itemService.closeNoInvoice(user, entity, id, version, "No charge"))
          .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("OPERATION_IN_PROGRESS"));
      jdbc.update("update public.external_operations set status='FAILED',completed_at=now() where id=?", operation);
      assertThat(detail.get(user, entity, id).allowedActions()).contains(WorkItemAction.NO_INVOICE_NEEDED);
      itemService.closeNoInvoice(user, entity, id, version, "No charge");
      var row = jdbc.queryForMap("select stage,close_reason,closing_note,version from public.work_items where entity_id=? and id=?", entity, id);
      assertThat(row.get("stage")).isEqualTo("CLOSED");
      assertThat(row.get("close_reason")).isEqualTo("NO_INVOICE_NEEDED");
      assertThat(row.get("closing_note")).isEqualTo("No charge");
      assertThat(row.get("version")).isEqualTo(version + 1);
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and event_type='CLOSED_NO_INVOICE'", Integer.class, id)).isEqualTo(1);
      tx.setRollbackOnly();
    });
  }

  @Test void gl04_partialEditPreservesOmittedFieldAndAuditsOnlyChangedField() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var mapping = mapping(entity);
      var actor = staff("STANDARD_USER");
      var id = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      jdbc.update("""
          insert into public.work_items(id,entity_id,xero_quote_id,quote_number,xero_quote_status,stage,owner_id,follow_up_date,notes)
          values (?,?,?,'Q-1','SENT','QUOTED',?,'2026-11-25','Before')
          """, id, entity, UUID.randomUUID(), actor);
      jdbc.update("update public.work_items set xero_tenant_id=? where entity_id=?", mapping.tenantId(), entity);
      jdbc.execute("set local role service_role");
      var itemService = new WorkItemService(new WorkItemRepository(jdbc));
      var user = new CurrentUser(actor.toString(), Role.STANDARD_USER);
      itemService.editPartial(user, entity, id, 0, false, null, false, true, "After");
      var row = jdbc.queryForMap("select notes,follow_up_date,version from public.work_items where entity_id=? and id=?", entity, id);
      assertThat(row.get("notes")).isEqualTo("After");
      assertThat(row.get("follow_up_date").toString()).isEqualTo("2026-11-25");
      assertThat(row.get("version")).isEqualTo(1);
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and event_type='NOTES_CHANGED'", Integer.class, id)).isEqualTo(1);
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and event_type='FOLLOW_UP_CHANGED'", Integer.class, id)).isZero();
      tx.setRollbackOnly();
    });
  }

  @Test void gl01_detailIsEntityScopedAndShowsDuplicateFlagAndOwnerActions() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var otherEntity = entity();
      var mapping = mapping(entity);
      mapping(otherEntity);
      var actor = staff("STANDARD_USER");
      var id = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true),(?,true)", entity, otherEntity);
      jdbc.update("""
          insert into public.work_items(id,entity_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage,owner_id,notes)
          values (?,?,?,'Q-1','Job-1','SENT','QUOTED',?,'Call')
          """, id, entity, UUID.randomUUID(), actor);
      jdbc.update("""
          insert into public.work_items(entity_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage)
          values (?,?,'Q-2','job-1','SENT','QUOTED')
          """, entity, UUID.randomUUID());
      jdbc.update("update public.work_items set xero_tenant_id=? where entity_id=?", mapping.tenantId(), entity);
      jdbc.execute("set local role service_role");
      var reads = new WorkItemReadService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
      var user = new CurrentUser(actor.toString(), Role.STANDARD_USER);
      var detail = reads.get(user, entity, id);
      assertThat(detail.summary().entityId()).isEqualTo(entity);
      assertThat(detail.summary().flags()).contains("JOB_ID_DUPLICATE");
      assertThat(detail.notes()).isEqualTo("Call");
      assertThat(detail.allowedActions()).containsExactly(WorkItemAction.EDIT_NOTES);
      assertThatThrownBy(() -> reads.get(user, otherEntity, id)).isInstanceOf(ApiFailure.class)
          .extracting(e -> ((ApiFailure)e).code()).isEqualTo("WORK_ITEM_NOT_FOUND");
      tx.setRollbackOnly();
    });
  }

  @Test void gl10_myWorkIncludesOwnedAndDesignatedAttentionButNotOtherOwners() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var otherEntity = entity();
      var mapping = mapping(entity);
      var otherMapping = mapping(otherEntity);
      var actor = staff("STANDARD_USER");
      var other = staff("STANDARD_USER");
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled,service_manager_id) values (?,true,?),(?,true,null)",
          entity, actor, otherEntity);
      jdbc.update("""
          insert into public.work_items(entity_id,xero_quote_id,quote_number,xero_quote_status,stage,owner_id,follow_up_date)
          values (?,?,'Q-action','SENT','QUOTED',?,null), (?,?,'Q-wait','SENT','QUOTED',?,'2026-12-01'),
                 (?,?,'Q-other','SENT','QUOTED',?,null), (?,?,'Q-other-entity','SENT','QUOTED',?,null)
          """, entity, UUID.randomUUID(), actor, entity, UUID.randomUUID(), actor,
          entity, UUID.randomUUID(), other, otherEntity, UUID.randomUUID(), actor);
      jdbc.update("""
          insert into public.work_items(entity_id,xero_quote_id,quote_number,xero_quote_status,stage,attention_codes)
          values (?,?,'Q-attention','ACCEPTED','TO_SCHEDULE',array['NO_SERVICE_MANAGER'])
          """, entity, UUID.randomUUID());
      jdbc.update("update public.work_items set xero_tenant_id=? where entity_id=?", mapping.tenantId(), entity);
      jdbc.update("update public.work_items set xero_tenant_id=? where entity_id=?", otherMapping.tenantId(), otherEntity);
      jdbc.execute("set local role service_role");
      var clock = Clock.fixed(NOW, ZoneOffset.UTC);
      var work = new MyWorkService(jdbc, new WorkItemReadService(jdbc, clock), clock);
      var result = work.get(new CurrentUser(actor.toString(), Role.STANDARD_USER), entity);
      assertThat(result.needsAction()).extracting(WorkItemReadService.Summary::quoteNumber).containsExactly("Q-action");
      assertThat(result.waiting()).extracting(WorkItemReadService.Summary::quoteNumber).containsExactly("Q-wait");
      assertThat(result.needsAttention()).extracting(WorkItemReadService.Summary::quoteNumber).containsExactly("Q-attention");
      assertThat(result.truncated()).isFalse();
      tx.setRollbackOnly();
    });
  }

  @Test void gl01_listUsesLiteralSearchAndEntityScopedStablePages() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var otherEntity = entity();
      var mapping = mapping(entity);
      var otherMapping = mapping(otherEntity);
      var actor = staff("STANDARD_USER");
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true),(?,true)", entity, otherEntity);
      jdbc.update("""
          insert into public.work_items(entity_id,xero_quote_id,quote_number,xero_quote_status,stage)
          values (?,?,'Q-100%','SENT','QUOTED'), (?,?,'Q-100X','SENT','QUOTED'),
                 (?,?,'Q-draft','DRAFT','QUOTED'), (?,?,'Q-other','SENT','QUOTED')
          """, entity, UUID.randomUUID(), entity, UUID.randomUUID(), entity, UUID.randomUUID(), otherEntity, UUID.randomUUID());
      jdbc.update("update public.work_items set xero_tenant_id=? where entity_id=?", mapping.tenantId(), entity);
      jdbc.update("update public.work_items set xero_tenant_id=? where entity_id=?", otherMapping.tenantId(), otherEntity);
      jdbc.execute("set local role service_role");
      var clock = Clock.fixed(NOW, ZoneOffset.UTC);
      var list = new WorkItemListService(jdbc, new WorkItemReadService(jdbc, clock), clock);
      var user = new CurrentUser(actor.toString(), Role.STANDARD_USER);
      var literal = new WorkItemListQuery(entity, null, null, null, null, null, "%",
          WorkItemListQuery.Sort.quoteNumber, WorkItemListQuery.Direction.ASC, 1, 25);
      assertThat(list.list(user, literal).items()).extracting(WorkItemReadService.Summary::quoteNumber).containsExactly("Q-100%");
      var page1 = new WorkItemListQuery(entity, null, null, null, null, null, "",
          WorkItemListQuery.Sort.quoteNumber, WorkItemListQuery.Direction.ASC, 1, 1);
      var page2 = new WorkItemListQuery(entity, null, null, null, null, null, "",
          WorkItemListQuery.Sort.quoteNumber, WorkItemListQuery.Direction.ASC, 2, 1);
      assertThat(list.list(user, page1).total()).isEqualTo(3);
      assertThat(list.list(user, page1).items()).extracting(WorkItemReadService.Summary::quoteNumber).containsExactly("Q-100%");
      assertThat(list.list(user, page2).items()).extracting(WorkItemReadService.Summary::quoteNumber).containsExactly("Q-100X");
      var page3 = new WorkItemListQuery(entity, null, null, null, null, null, "",
          WorkItemListQuery.Sort.quoteNumber, WorkItemListQuery.Direction.ASC, 3, 1);
      assertThat(list.list(user, page3).items()).extracting(WorkItemReadService.Summary::quoteNumber).containsExactly("Q-draft");
      tx.setRollbackOnly();
    });
  }

  @Test void gl22_quoteMirrorDefersChangesUntilOperationIsResolved() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var mapping = mapping(entity);
      var item = UUID.randomUUID();
      var quoteId = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      jdbc.update("""
          insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_reference,
            xero_quote_status,xero_quote_updated_at,stage)
          values (?,?,?,?,'Q-1','J-1','J-1','SENT',?,'QUOTED')
          """, item, entity, mapping.tenantId(), quoteId, java.sql.Timestamp.from(NOW.minusSeconds(60)));
      var operation = UUID.randomUUID();
      jdbc.execute("set local role service_role");
      jdbc.update("""
          insert into public.external_operations(id,entity_id,work_item_id,kind,status,attempt,lookup_reference,request_fingerprint)
          values (?,?,?,'VWORK_CREATE_JOB','PENDING',1,'J-1',?)
          """, operation, entity, item, "a".repeat(64));
      var changed = quote(quoteId, XeroQuoteClient.Status.DECLINED, "J-2", NOW);
      assertThatThrownBy(() -> service.apply(mapping, changed, QuoteMirrorService.InvoiceMatch.UNCHECKED))
          .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("QUOTE_SYNC_OPERATION_IN_PROGRESS"));
      assertThat(jdbc.queryForObject("select job_id from public.work_items where id=?", String.class, item)).isEqualTo("J-1");
      jdbc.update("update public.external_operations set status='FAILED',completed_at=now() where id=?", operation);
      assertThat(service.apply(mapping, changed, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.UPDATED);
      assertThat(jdbc.queryForObject("select stage from public.work_items where id=?", String.class, item)).isEqualTo("CLOSED");
      tx.setRollbackOnly();
    });
  }

  @Test void gl22_reservationWritesPendingVersionAndAuditTogether() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var mapping = mapping(entity);
      var actor = staff("DIRECTOR");
      var item = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      jdbc.update("""
          insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,xero_quote_status,stage,version)
          values (?,?,?,?,'Q-1','ACCEPTED','TO_SCHEDULE',7)
          """, item, entity, mapping.tenantId(), UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var operations = new com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository(jdbc);
      var reserve = new com.airconcepts.dashboard.workitems.operations.OperationReservationService(new WorkItemRepository(jdbc), operations);
      var kind = com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository.Kind.VWORK_CREATE_JOB;
      var user = new com.airconcepts.dashboard.security.CurrentUser(actor.toString(), com.airconcepts.dashboard.security.Role.DIRECTOR);
      var result = reserve.reserve(user, entity, item, 7, kind, "J-1", "a".repeat(64));
      assertThat(jdbc.queryForObject("select status from public.external_operations where id=?", String.class, result.id())).isEqualTo("PENDING");
      assertThat(jdbc.queryForObject("select version from public.work_items where id=?", Integer.class, item)).isEqualTo(8);
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and event_type='JOB_CREATE_REQUESTED'", Integer.class, item)).isEqualTo(1);
      assertThatThrownBy(() -> reserve.reserve(user, entity, item, 8, kind, "J-1", "a".repeat(64)))
          .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("OPERATION_ALREADY_EXISTS"));
      jdbc.update("update public.green_line_entity_settings set enabled=false where entity_id=?", entity);
      var outcomes = new com.airconcepts.dashboard.workitems.operations.OperationOutcomeService(new WorkItemRepository(jdbc), operations);
      outcomes.markUnknown(entity, item, result.id());
      assertThat(jdbc.queryForObject("select status from public.external_operations where id=?", String.class, result.id())).isEqualTo("UNKNOWN");
      assertThat(jdbc.queryForObject("select 'OPERATION_UNKNOWN'=any(attention_codes) from public.work_items where id=?", Boolean.class, item)).isTrue();
      assertThat(jdbc.queryForObject("select version from public.work_items where id=?", Integer.class, item)).isEqualTo(9);
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and event_type='OPERATION_OUTCOME'", Integer.class, item)).isEqualTo(1);
      tx.setRollbackOnly();
    });
  }

  @Test void gl22_lookupNotFoundUpdatesAttentionAndExposesOnlyAuthorizedLookup() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity(); var mapping = mapping(entity); var manager = staff("STANDARD_USER");
      var other = staff("STANDARD_USER"); var item = UUID.randomUUID(); var operation = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled,service_manager_id) values (?,true,?)", entity, manager);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage,attention_codes) "
          + "values (?,?,?,?,'Q-1','J-1','ACCEPTED','TO_SCHEDULE',array['OPERATION_UNKNOWN','OPERATION_FAILED'])",
          item, entity, mapping.tenantId(), UUID.randomUUID());
      jdbc.execute("set local role service_role");
      jdbc.update("insert into public.external_operations(id,entity_id,work_item_id,kind,status,attempt,lookup_reference,request_fingerprint) "
          + "values (?,?,?,'VWORK_CREATE_JOB','UNKNOWN',1,'J-1',?)", operation, entity, item, "a".repeat(64));
      var reads = new WorkItemReadService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
      assertThat(reads.get(new CurrentUser(manager.toString(), Role.STANDARD_USER), entity, item).operations())
          .singleElement().satisfies(view -> assertThat(view.allowLookup()).isTrue());
      assertThat(reads.get(new CurrentUser(other.toString(), Role.STANDARD_USER), entity, item).operations())
          .singleElement().satisfies(view -> assertThat(view.allowLookup()).isFalse());
      var operations = new com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository(jdbc);
      assertThat(operations.markNotFound(entity,item,operation)).isEqualTo(1);
      assertThat(operations.markLookupFailedAttention(entity,item,0)).isEqualTo(1);
      assertThat(jdbc.queryForObject("select 'OPERATION_UNKNOWN'=any(attention_codes) from public.work_items where id=?", Boolean.class, item)).isFalse();
      assertThat(jdbc.queryForObject("select array_length(attention_codes,1) from public.work_items where id=?", Integer.class, item)).isEqualTo(1);
      assertThat(jdbc.queryForObject("select outcome from public.external_operations where id=?", String.class, operation)).isEqualTo("NOT_FOUND");
      var reserve = new com.airconcepts.dashboard.workitems.operations.OperationReservationService(new WorkItemRepository(jdbc), operations);
      var retry = reserve.retry(new CurrentUser(manager.toString(), Role.STANDARD_USER), entity, item, 1,
          com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository.Kind.VWORK_CREATE_JOB, operation, "J-1", "b".repeat(64));
      assertThat(retry.attempt()).isEqualTo(2);
      assertThat(operations.complete(entity,item,retry.id(),com.airconcepts.dashboard.workitems.operations.OperationState.Result.CREATED,"mock-retry")).isEqualTo(1);
      operations.clearResolvedFailed(entity,item);
      assertThat(jdbc.queryForObject("select 'OPERATION_FAILED'=any(attention_codes) from public.work_items where id=?", Boolean.class, item)).isFalse();
      tx.setRollbackOnly();
    });
  }

  @Test void gl21_invoiceMirrorPersistsPartialPaymentAndClosesPaid() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity); var item=UUID.randomUUID(); var invoice=UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)",entity);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,xero_quote_status,stage,xero_invoice_id) "
          + "values (?,?,?,?,'Q-1','ACCEPTED','INVOICED',?)",item,entity,mapping.tenantId(),UUID.randomUUID(),invoice);
      jdbc.execute("set local role service_role");
      var mirror=new InvoiceMirrorService(new InvoiceMirrorRepository(jdbc),new QuoteMirrorRepository(jdbc),Clock.fixed(NOW,ZoneOffset.UTC));
      mirror.apply(mapping,item,new com.airconcepts.dashboard.xero.XeroInvoiceClient.WorkflowInvoice(invoice,"INV-1",
          com.airconcepts.dashboard.invoices.InvoiceStatus.AUTHORISED,new java.math.BigDecimal("100"),new java.math.BigDecimal("40"),null,true),false);
      assertThat(jdbc.queryForObject("select stage from public.work_items where id=?",String.class,item)).isEqualTo("INVOICED");
      assertThat(jdbc.queryForObject("select invoice_amount_due from public.work_items where id=?",java.math.BigDecimal.class,item)).isEqualByComparingTo("40");
      mirror.apply(mapping,item,new com.airconcepts.dashboard.xero.XeroInvoiceClient.WorkflowInvoice(invoice,"INV-1",
          com.airconcepts.dashboard.invoices.InvoiceStatus.PAID,new java.math.BigDecimal("100"),java.math.BigDecimal.ZERO,null,true),false);
      assertThat(jdbc.queryForObject("select close_reason from public.work_items where id=?",String.class,item)).isEqualTo("PAID");
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and event_type='STAGE_CHANGED'",Integer.class,item)).isEqualTo(1);
      tx.setRollbackOnly();
    });
  }

  @Test void gl19_manualInvoiceLinksAndMovesToDrafted() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity); var item=UUID.randomUUID(); var invoice=UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)",entity);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage,attention_codes) "
          + "values (?,?,?,?,'Q-1','J-1','INVOICED','TO_INVOICE',array['QUOTE_INVOICED_IN_XERO'])",
          item,entity,mapping.tenantId(),UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var repository=new InvoiceMirrorRepository(jdbc);
      var candidate=repository.unlinked(entity).getFirst();
      var mirror=new InvoiceMirrorService(repository,new QuoteMirrorRepository(jdbc),Clock.fixed(NOW,ZoneOffset.UTC));
      mirror.link(mapping,candidate,new com.airconcepts.dashboard.xero.XeroInvoiceClient.WorkflowInvoice(invoice,"INV-1",
          com.airconcepts.dashboard.invoices.InvoiceStatus.DRAFT,new java.math.BigDecimal("100"),new java.math.BigDecimal("100"),null,false,"J-1 / Q-1"),false);
      assertThat(jdbc.queryForObject("select xero_invoice_id from public.work_items where id=?",UUID.class,item)).isEqualTo(invoice);
      assertThat(jdbc.queryForObject("select stage from public.work_items where id=?",String.class,item)).isEqualTo("INVOICE_DRAFTED");
      assertThat(jdbc.queryForObject("select 'QUOTE_INVOICED_IN_XERO'=any(attention_codes) from public.work_items where id=?",Boolean.class,item)).isFalse();
      tx.setRollbackOnly();
    });
  }

  @Test void gl21_markSentRequiresAuthorisedInvoiceAndAuditsTheActor() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity); var actor=staff("FINANCIAL_ADMIN"); var item=UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)",entity);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,xero_quote_status,stage,xero_invoice_id,invoice_status) "
          + "values (?,?,?,?,'Q-1','ACCEPTED','INVOICE_DRAFTED',?,'AUTHORISED')",item,entity,mapping.tenantId(),UUID.randomUUID(),UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var service=new WorkItemService(new WorkItemRepository(jdbc));
      service.markSent(new CurrentUser(actor.toString(),Role.FINANCIAL_ADMIN),entity,item,0);
      assertThat(jdbc.queryForObject("select stage from public.work_items where id=?",String.class,item)).isEqualTo("INVOICED");
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and actor_id=? and event_type='MARKED_SENT'",Integer.class,item,actor)).isEqualTo(1);
      tx.setRollbackOnly();
    });
  }

  @Test void gl17_invoiceDraftContextRequiresFinanceAndUnlinkedItem() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity); var actor=staff("FINANCIAL_ADMIN"); var other=staff("STANDARD_USER");
      var item=UUID.randomUUID(); var quote=UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)",entity);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage) "
          + "values (?,?,?,?,'Q-1','J-1','ACCEPTED','TO_INVOICE')",item,entity,mapping.tenantId(),quote);
      jdbc.execute("set local role service_role");
      var context=new com.airconcepts.dashboard.workitems.operations.InvoiceDraftContext(new WorkItemRepository(jdbc),
          new com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository(jdbc),jdbc);
      var result=context.load(new CurrentUser(actor.toString(),Role.FINANCIAL_ADMIN),entity,item);
      assertThat(result.quoteId()).isEqualTo(quote);
      assertThat(result.tenantId()).isEqualTo(mapping.tenantId());
      var reads=new WorkItemReadService(jdbc,Clock.fixed(NOW,ZoneOffset.UTC),new com.airconcepts.dashboard.workitems.operations.MockInvoiceGateway());
      assertThat(reads.get(new CurrentUser(actor.toString(),Role.FINANCIAL_ADMIN),entity,item).allowedActions()).contains(WorkItemAction.PREPARE_INVOICE);
      assertThat(reads.get(new CurrentUser(other.toString(),Role.STANDARD_USER),entity,item).allowedActions()).doesNotContain(WorkItemAction.PREPARE_INVOICE);
      assertThatThrownBy(() -> context.load(new CurrentUser(other.toString(),Role.STANDARD_USER),entity,item))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
      jdbc.update("update public.work_items set xero_invoice_id=? where id=?",UUID.randomUUID(),item);
      assertThatThrownBy(() -> context.load(new CurrentUser(actor.toString(),Role.FINANCIAL_ADMIN),entity,item))
          .isInstanceOf(ApiFailure.class);
      tx.setRollbackOnly();
    });
  }

  @Test void gl17_invoiceReservationAndConfirmationPersistTogether() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity); var actor=staff("FINANCIAL_ADMIN");
      var item=UUID.randomUUID(); var quote=UUID.randomUUID(); var invoice=UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)",entity);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage) "
          + "values (?,?,?,?,'Q-1','J-1','ACCEPTED','TO_INVOICE')",item,entity,mapping.tenantId(),quote);
      jdbc.execute("set local role service_role");
      var items=new WorkItemRepository(jdbc);
      var operations=new com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository(jdbc);
      var contexts=new com.airconcepts.dashboard.workitems.operations.InvoiceDraftContext(items,operations,jdbc);
      var user=new CurrentUser(actor.toString(),Role.FINANCIAL_ADMIN);
      var context=contexts.load(user,entity,item);
      var reservation=new com.airconcepts.dashboard.workitems.operations.InvoiceReservation(contexts,new QuoteMirrorRepository(jdbc),
          new com.airconcepts.dashboard.workitems.operations.OperationReservationService(items,operations)).reserve(user,entity,item,context,"a".repeat(64));
      var observed=new com.airconcepts.dashboard.xero.XeroInvoiceClient.WorkflowInvoice(invoice,"MOCK-1",
          com.airconcepts.dashboard.invoices.InvoiceStatus.DRAFT,java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,null,false,"J-1 / Q-1");
      new com.airconcepts.dashboard.workitems.operations.InvoiceOperationCompletion(new QuoteMirrorRepository(jdbc),new InvoiceMirrorRepository(jdbc),operations,
          Clock.fixed(NOW,ZoneOffset.UTC)).complete(entity,item,context,reservation.id(),observed,
          com.airconcepts.dashboard.workitems.operations.OperationState.Result.CREATED);
      assertThat(jdbc.queryForObject("select stage from public.work_items where id=?",String.class,item)).isEqualTo("INVOICE_DRAFTED");
      assertThat(jdbc.queryForObject("select xero_invoice_id from public.work_items where id=?",UUID.class,item)).isEqualTo(invoice);
      assertThat(jdbc.queryForObject("select status from public.external_operations where id=?",String.class,reservation.id())).isEqualTo("SUCCEEDED");
      tx.setRollbackOnly();
    });
  }

  @Test void gl25_runLedgerRecordsSuccessAndFailureWithoutAdvancingFirstSyncOnFailure() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity();
      var mapping = mapping(entity);
      var actor = staff("DIRECTOR");
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)", entity);
      jdbc.execute("set local role service_role");
      var ledger = new GreenLineSyncRunRepository(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()));
      var first = ledger.begin(entity, "MANUAL", actor, NOW);
      ledger.fail(entity, first, "QUOTE_SYNC_COMPLETENESS_UNVERIFIED", null, NOW.plusSeconds(1));
      assertThat(jdbc.queryForObject("select first_synced_at from public.green_line_entity_settings where entity_id=?", java.sql.Timestamp.class, entity)).isNull();
      assertThat(jdbc.queryForObject("select status from public.green_line_sync_runs where id=?", String.class, first)).isEqualTo("FAILED");
      var second = ledger.begin(entity, "MANUAL", actor, NOW.plusSeconds(61));
      var access = new com.airconcepts.dashboard.xero.TenantAccess(entity, mapping.tenantId(), mapping.grantId(), "synthetic");
      ledger.succeed(entity, second, access, new QuoteSync.Result(2, 1, 1, 0, 0, 0, true), NOW.plusSeconds(62));
      assertThat(jdbc.queryForObject("select status from public.green_line_sync_runs where id=?", String.class, second)).isEqualTo("SUCCEEDED");
      assertThat(jdbc.queryForObject("select first_synced_at from public.green_line_entity_settings where entity_id=?", java.sql.Timestamp.class, entity).toInstant())
          .isEqualTo(NOW.plusSeconds(62));
      var status = ledger.status(entity);
      assertThat(status.xero().latest().id()).isEqualTo(second);
      assertThat(status.xero().lastSuccess().id()).isEqualTo(second);
      assertThat(status.xero().lastFailure().id()).isEqualTo(first);
      assertThat(status.vwork().latest()).isNull();
      tx.setRollbackOnly();
    });
  }

  @Test void gl14_gl15_gl16_vworkMirrorLinksSchedulesAndHandsOffInDatabase() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity(); var mapping = mapping(entity);
      var owner = staff("FINANCIAL_ADMIN"); var manager = staff("STANDARD_USER");
      var item = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled,default_owner_id,service_manager_id) values (?,true,?,?)", entity, owner, manager);
      jdbc.update("insert into public.vwork_connection(id,key_ciphertext,status,configured_by) values (1,'synthetic-ciphertext','ACTIVE',?)", owner);
      jdbc.update("insert into public.vwork_entity_templates(template_ref,entity_id,created_by) values ('test-template',?,?)", entity, owner);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage,owner_id) "
          + "values (?,?,?,?,'Q-1','J-1','ACCEPTED','TO_SCHEDULE',?)", item, entity, mapping.tenantId(), UUID.randomUUID(), manager);
      jdbc.execute("set local role service_role");
      var revision = jdbc.queryForObject("select xmin::text from public.vwork_connection where id=1", String.class);
      var ledger = new GreenLineSyncRunRepository(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()));
      var run = ledger.beginVwork(entity, "MANUAL", owner, NOW);
      var mirror = new VworkMirrorService(new VworkMirrorRepository(jdbc), Clock.fixed(NOW, ZoneOffset.UTC));
      mirror.apply(entity, item, revision, new com.airconcepts.dashboard.vwork.VworkClient.Job("mock-1", "J-1", "test-template", null,
          com.airconcepts.dashboard.vwork.VworkClient.Status.ASSIGNED, "Synthetic worker", NOW, null));
      assertThat(jdbc.queryForObject("select stage from public.work_items where id=?", String.class, item)).isEqualTo("SCHEDULED");
      mirror.apply(entity, item, revision, new com.airconcepts.dashboard.vwork.VworkClient.Job("mock-1", "J-1", "test-template", null,
          com.airconcepts.dashboard.vwork.VworkClient.Status.COMPLETED, "Synthetic worker", NOW, NOW));
      var row = jdbc.queryForMap("select stage,owner_id,completed_at from public.work_items where id=?", item);
      assertThat(row.get("stage")).isEqualTo("TO_INVOICE");
      assertThat(row.get("owner_id")).isEqualTo(owner);
      assertThat(row.get("completed_at")).isNotNull();
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and source='VWORK_SYNC'", Integer.class, item)).isEqualTo(3);
      var access = new com.airconcepts.dashboard.vwork.VworkConnectionService.Access(revision,
          new com.airconcepts.dashboard.vwork.VworkCredential("synthetic"), new com.airconcepts.dashboard.vwork.MockVworkClient(), true);
      ledger.succeedVwork(entity, run, access, new VworkSync.Result(1, 1, 0), NOW.plusSeconds(1));
      assertThat(ledger.status(entity).vwork().lastSuccess().id()).isEqualTo(run);
      tx.setRollbackOnly();
    });
  }

  @Test void gl13_gl22_confirmedVworkCreationLinksItemAndOperationTogether() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity(); var mapping = mapping(entity); var actor = staff("DIRECTOR");
      var item = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled,service_manager_id) values (?,true,?)", entity, actor);
      jdbc.update("insert into public.vwork_connection(id,key_ciphertext,status,configured_by) values (1,'synthetic-ciphertext','ACTIVE',?)", actor);
      jdbc.update("insert into public.vwork_entity_templates(template_ref,entity_id,created_by) values ('test-template',?,?)", entity, actor);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage) "
          + "values (?,?,?,?,'Q-1','J-1','ACCEPTED','TO_SCHEDULE')", item, entity, mapping.tenantId(), UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var operations = new com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository(jdbc);
      var revision = jdbc.queryForObject("select xmin::text from public.vwork_connection where id=1", String.class);
      var reserve = new com.airconcepts.dashboard.workitems.operations.OperationReservationService(new WorkItemRepository(jdbc), operations);
      var reservation = new com.airconcepts.dashboard.workitems.operations.VworkJobReservation(new VworkMirrorRepository(jdbc), reserve)
          .reserve(new CurrentUser(actor.toString(), Role.DIRECTOR), entity, item, 0, revision, "test-template", "J-1", "a".repeat(64));
      var job = new com.airconcepts.dashboard.vwork.VworkClient.Job("mock-confirmed", "J-1", "test-template", null,
          com.airconcepts.dashboard.vwork.VworkClient.Status.UNASSIGNED, null, null, null);
      var completion = new com.airconcepts.dashboard.workitems.operations.VworkOperationCompletion(new VworkMirrorRepository(jdbc), operations);
      completion.complete(entity, item, reservation.id(), revision, job,
          com.airconcepts.dashboard.workitems.operations.OperationState.Result.CREATED);
      assertThat(jdbc.queryForObject("select vwork_job_id from public.work_items where id=?", String.class, item)).isEqualTo("mock-confirmed");
      assertThat(jdbc.queryForObject("select status from public.external_operations where id=?", String.class, reservation.id())).isEqualTo("SUCCEEDED");
      assertThat(jdbc.queryForObject("select count(*) from public.work_item_events where work_item_id=? and event_type='JOB_LINKED'", Integer.class, item)).isEqualTo(1);
      tx.setRollbackOnly();
    });
  }

  @Test void gl12_jobDraftContextChecksEntityRoleAndTemplates() {
    transaction.executeWithoutResult(tx -> {
      var entity = entity(); var mapping = mapping(entity); var manager = staff("STANDARD_USER");
      var other = staff("STANDARD_USER"); var item = UUID.randomUUID(); var quote = UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled,service_manager_id) values (?,true,?)", entity, manager);
      jdbc.update("insert into public.vwork_entity_templates(template_ref,entity_id,is_default,created_by) values ('draft-template',?,true,?)", entity, manager);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage) "
          + "values (?,?,?,?,'Q-1','J-1','ACCEPTED','TO_SCHEDULE')", item, entity, mapping.tenantId(), quote);
      jdbc.execute("set local role service_role");
      var context = new com.airconcepts.dashboard.workitems.operations.VworkJobDraftContext(new WorkItemRepository(jdbc),
          new com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository(jdbc),
          new com.airconcepts.dashboard.vwork.VworkTemplatesRepository(jdbc), jdbc);
      var draft = context.load(new CurrentUser(manager.toString(), Role.STANDARD_USER), entity, item);
      assertThat(draft.quoteId()).isEqualTo(quote);
      assertThat(draft.templates()).singleElement().satisfies(template -> assertThat(template.templateRef()).isEqualTo("draft-template"));
      var reads = new WorkItemReadService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
      assertThat(reads.get(new CurrentUser(manager.toString(), Role.STANDARD_USER), entity, item).allowedActions())
          .contains(WorkItemAction.CREATE_JOB);
      assertThat(reads.get(new CurrentUser(other.toString(), Role.STANDARD_USER), entity, item).allowedActions())
          .doesNotContain(WorkItemAction.CREATE_JOB);
      assertThatThrownBy(() -> context.load(new CurrentUser(other.toString(), Role.STANDARD_USER), entity, item))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
      tx.setRollbackOnly();
    });
  }

  @Test void gl_status_02_earlyInvoiceCandidateAndLinkAreScopedToTheEntity() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity); var other=entity(); var otherMapping=mapping(other);
      var item=UUID.randomUUID(); var otherItem=UUID.randomUUID(); var invoice=UUID.randomUUID(); var manager=staff("STANDARD_USER");
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled,service_manager_id) values (?,true,?),(?,true,null)",entity,manager,other);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage) values (?,?,?,?,'Q-1','J-1','ACCEPTED','TO_SCHEDULE'),(?,?,?,?,'Q-2','J-2','ACCEPTED','TO_SCHEDULE')",
          item,entity,mapping.tenantId(),UUID.randomUUID(),otherItem,other,otherMapping.tenantId(),UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var repository=new InvoiceMirrorRepository(jdbc);
      assertThat(repository.unlinked(entity)).isEmpty();
      var candidates=repository.unlinkedAllOpen(entity);
      assertThat(candidates).extracting(InvoiceMirrorRepository.Unlinked::itemId).containsExactly(item);
      var mirror=new InvoiceMirrorService(repository,new QuoteMirrorRepository(jdbc),Clock.fixed(NOW,ZoneOffset.UTC));
      mirror.link(mapping,candidates.getFirst(),new com.airconcepts.dashboard.xero.XeroInvoiceClient.WorkflowInvoice(invoice,"INV-1",
          com.airconcepts.dashboard.invoices.InvoiceStatus.AUTHORISED,BigDecimal.TEN,BigDecimal.TEN,null,true,"J-1 / Q-1"),true);
      assertThat(jdbc.queryForObject("select stage from public.work_items where id=?",String.class,item)).isEqualTo("INVOICED");
      assertThat(jdbc.queryForObject("select xero_invoice_id from public.work_items where id=?",UUID.class,otherItem)).isNull();
      jdbc.update("update public.work_items set vwork_job_id='synthetic-gl-status',vwork_status='ASSIGNED',technician_name='Synthetic',scheduled_start=? where id=?",
          java.sql.Timestamp.from(NOW),item);
      mirror.apply(mapping,item,new com.airconcepts.dashboard.xero.XeroInvoiceClient.WorkflowInvoice(invoice,"INV-1",
          com.airconcepts.dashboard.invoices.InvoiceStatus.VOIDED,BigDecimal.TEN,BigDecimal.TEN,null,false,"J-1 / Q-1"),true);
      assertThat(jdbc.queryForObject("select stage from public.work_items where id=?",String.class,item)).isEqualTo("SCHEDULED");
      assertThat(jdbc.queryForObject("select owner_id from public.work_items where id=?",UUID.class,item)).isEqualTo(manager);
      tx.setRollbackOnly();
    });
  }

  @Test void gl_status_03_extraJobCandidatesStayWithinTheirEntity() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity); var other=entity(); var otherMapping=mapping(other);
      var item=UUID.randomUUID(); var otherItem=UUID.randomUUID();
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true),(?,true)",entity,other);
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage) values (?,?,?,?,'Q-1','J-1','SENT','QUOTED'),(?,?,?,?,'Q-2','J-2','SENT','QUOTED')",
          item,entity,mapping.tenantId(),UUID.randomUUID(),otherItem,other,otherMapping.tenantId(),UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var repository=new VworkMirrorRepository(jdbc);
      assertThat(repository.candidates(entity)).isEmpty();
      assertThat(repository.additionalTrackingCandidates(entity)).extracting(VworkMirrorRepository.Candidate::id).containsExactly(item);
      tx.setRollbackOnly();
    });
  }

  @Test void gl_status_04_sharedReferencesAreFlaggedWithinTheCorrectTenant() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity); var other=entity(); var otherMapping=mapping(other);
      var item=UUID.randomUUID(); var otherItem=UUID.randomUUID(); var duplicate=UUID.randomUUID();
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage) values (?,?,?,?,'Q-1','J-1','SENT','QUOTED'),(?,?,?,?,'Q-2','J-1','SENT','QUOTED')",
          item,entity,mapping.tenantId(),UUID.randomUUID(),otherItem,other,otherMapping.tenantId(),UUID.randomUUID());
      jdbc.execute("set local role service_role");
      var repository=new InvoiceMirrorRepository(jdbc);
      var candidate=new InvoiceMirrorRepository.Unlinked(item,"J-1","Q-1");
      assertThat(repository.conflictingReferences(mapping,candidate)).isFalse();
      jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_status,stage) values (?,?,?,?,'Q-3','J-1','SENT','QUOTED')",
          duplicate,entity,mapping.tenantId(),UUID.randomUUID());
      assertThat(repository.conflictingReferences(mapping,candidate)).isTrue();
      var invoiceId=UUID.randomUUID();
      var multi=new com.airconcepts.dashboard.xero.XeroInvoiceClient.WorkflowInvoice(invoiceId,"INV-1",
          com.airconcepts.dashboard.invoices.InvoiceStatus.AUTHORISED,BigDecimal.TEN,BigDecimal.TEN,null,true,"Q-1 / Q-3");
      assertThat(repository.invoiceConflictsWithOtherWorkItems(mapping,candidate,multi)).isTrue();
      var unique=new com.airconcepts.dashboard.xero.XeroInvoiceClient.WorkflowInvoice(invoiceId,"INV-1",
          com.airconcepts.dashboard.invoices.InvoiceStatus.AUTHORISED,BigDecimal.TEN,BigDecimal.TEN,null,true,"Q-1");
      assertThat(repository.invoiceConflictsWithOtherWorkItems(mapping,candidate,unique)).isFalse();
      tx.setRollbackOnly();
    });
  }

  @Test void gl_status_01_invoiceLookupBatchesAdvanceWithEachSyncRun() {
    transaction.executeWithoutResult(tx -> {
      var entity=entity(); var mapping=mapping(entity);
      jdbc.update("insert into public.green_line_entity_settings(entity_id,enabled) values (?,true)",entity);
      for (int i=0;i<21;i++) jdbc.update("insert into public.work_items(id,entity_id,xero_tenant_id,xero_quote_id,quote_number,xero_quote_status,stage) values (?,?,?,?,?,'SENT','QUOTED')",
          UUID.randomUUID(),entity,mapping.tenantId(),UUID.randomUUID(),"Q-"+i);
      jdbc.update("insert into public.green_line_sync_runs(id,entity_id,system,trigger,started_at,status) values (?,?,'XERO','SCHEDULED',now(),'RUNNING')",UUID.randomUUID(),entity);
      jdbc.execute("set local role service_role");
      var repository=new InvoiceMirrorRepository(jdbc);
      var first=repository.unlinkedAllOpen(entity);
      jdbc.update("insert into public.green_line_sync_runs(id,entity_id,system,trigger,started_at,status) values (?,?,'XERO','SCHEDULED',now(),'RUNNING')",UUID.randomUUID(),entity);
      var second=repository.unlinkedAllOpen(entity);
      assertThat(first).hasSize(20);
      assertThat(second).hasSize(1);
      assertThat(first).doesNotContainAnyElementsOf(second);
      tx.setRollbackOnly();
    });
  }

  private static UUID entity() {
    var id = UUID.randomUUID();
    jdbc.update("insert into public.entities(id,code,display_name) values (?,?,?)", id, "test-" + id, "Synthetic entity");
    return id;
  }
  private static QuoteMirrorService.Mapping mapping(UUID entity) {
    var grant = UUID.randomUUID(); var tenant = UUID.randomUUID();
    jdbc.update("insert into public.xero_grants(id,access_ciphertext,refresh_ciphertext,expires_at,status) values (?,?,?,now(),'ACTIVE')",
        grant, "synthetic-access-ciphertext-padded-to-schema-minimum", "synthetic-refresh-ciphertext-padded-to-schema-minimum");
    jdbc.update("insert into public.xero_entity_connections(entity_id,grant_id,tenant_id,connection_id,tenant_name,verified_at) values (?,?,?,?,?,now())",
        entity, grant, tenant, UUID.randomUUID(), "Synthetic");
    return new QuoteMirrorService.Mapping(entity, tenant, grant);
  }
  private static UUID staff(String role) {
    var id = UUID.randomUUID();
    jdbc.update("insert into auth.users(id) values (?)", id);
    jdbc.update("insert into public.staff_users(id,email,display_name,role) values (?,?,?,?)", id, id + "@example.invalid", "Synthetic user", role);
    return id;
  }
  private static XeroQuoteClient.Quote quote(UUID id, XeroQuoteClient.Status status, String reference, Instant updated) {
    return new XeroQuoteClient.Quote(id, "Q-1", reference, status, null, "Synthetic customer",
        LocalDate.of(2026, 11, 18), null, updated, new BigDecimal("100.00"), "NZD", "EXCLUSIVE", List.of());
  }
}
