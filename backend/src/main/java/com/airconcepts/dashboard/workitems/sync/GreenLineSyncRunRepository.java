package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.vwork.VworkConnectionService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class GreenLineSyncRunRepository {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  public GreenLineSyncRunRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
    this.jdbc = jdbc; this.tx = new TransactionTemplate(transactions);
  }

  public UUID begin(UUID entity, String trigger, UUID actor, Instant started) {
    return beginSystem(entity, "XERO", trigger, actor, started);
  }

  public UUID beginVwork(UUID entity, String trigger, UUID actor, Instant started) {
    return beginSystem(entity, "VWORK", trigger, actor, started);
  }

  private UUID beginSystem(UUID entity, String system, String trigger, UUID actor, Instant started) {
    return tx.execute(status -> {
      jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", "green-line-sync:" + system + ":" + entity);
      var enabled = jdbc.queryForList("select s.entity_id from public.green_line_entity_settings s join public.entities e on e.id=s.entity_id "
          + "where s.entity_id=? and s.enabled and e.archived_at is null for share of s,e", entity);
      if (enabled.isEmpty()) throw failure("GREEN_LINE_DISABLED", "The workflow is not enabled for this entity.");
      Boolean running = jdbc.queryForObject("select exists(select 1 from public.green_line_sync_runs where entity_id=? and system=? "
          + "and status='RUNNING' and started_at>?)", Boolean.class, entity, system, Timestamp.from(started.minusSeconds(1800)));
      if (Boolean.TRUE.equals(running)) throw failure("SYNC_IN_PROGRESS", "A sync is already running.");
      if ("MANUAL".equals(trigger)) {
        Boolean recent = jdbc.queryForObject("select exists(select 1 from public.green_line_sync_runs where entity_id=? and system=? "
            + "and trigger='MANUAL' and started_at>?)", Boolean.class, entity, system, Timestamp.from(started.minusSeconds(60)));
        if (Boolean.TRUE.equals(recent)) throw new ApiFailure(HttpStatus.TOO_MANY_REQUESTS, "SYNC_RATE_LIMITED", "Wait one minute before refreshing again.");
      }
      jdbc.update("update public.green_line_sync_runs set status='FAILED',finished_at=?,error_code='SYNC_EXPIRED' "
          + "where entity_id=? and system=? and status='RUNNING'", Timestamp.from(started), entity, system);
      var id = UUID.randomUUID();
      jdbc.update("insert into public.green_line_sync_runs(id,entity_id,system,trigger,triggered_by,started_at,status) values(?,?,?,?,?,?,'RUNNING')",
          id, entity, system, trigger, actor, Timestamp.from(started));
      return id;
    });
  }

  public List<UUID> enabledEntities() {
    return jdbc.query("select s.entity_id from public.green_line_entity_settings s join public.entities e on e.id=s.entity_id "
        + "join public.xero_entity_connections c on c.entity_id=e.id where s.enabled and e.archived_at is null order by s.entity_id",
        (r, n) -> r.getObject(1, UUID.class));
  }

  public boolean isServiceManager(UUID entity, UUID actor) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.green_line_entity_settings s "
        + "join public.staff_users u on u.id=s.service_manager_id where s.entity_id=? and s.enabled and u.id=? and u.active)",
        Boolean.class, entity, actor));
  }

  public GreenLineSyncViews.Status status(UUID entity) {
    return new GreenLineSyncViews.Status(entity, systemStatus(entity, "XERO"), systemStatus(entity, "VWORK"));
  }

  private GreenLineSyncViews.SystemStatus systemStatus(UUID entity, String system) {
    return new GreenLineSyncViews.SystemStatus(latest(entity, system, null), latest(entity, system, "SUCCEEDED"),
        latest(entity, system, "FAILED"));
  }

  private GreenLineSyncViews.Run latest(UUID entity, String system, String runStatus) {
    return jdbc.query("select id,status,started_at,finished_at,error_code from public.green_line_sync_runs "
        + "where entity_id=? and system=? and (cast(? as text) is null or status=?) order by started_at desc,id desc limit 1",
        (r, n) -> new GreenLineSyncViews.Run(r.getObject("id", UUID.class), r.getString("status"),
            r.getTimestamp("started_at").toInstant(), r.getTimestamp("finished_at") == null ? null : r.getTimestamp("finished_at").toInstant(),
            r.getString("error_code")), entity, system, runStatus, runStatus).stream().findFirst().orElse(null);
  }

  public void succeed(UUID entity, UUID run, TenantAccess access, QuoteSync.Result result, Instant finished) {
    if (!result.complete() || !entity.equals(access.entityId())) throw failure("SYNC_INCOMPLETE", "The sync did not complete.");
    tx.executeWithoutResult(status -> {
      var mapping = jdbc.queryForList("select c.entity_id from public.xero_entity_connections c "
          + "join public.entities e on e.id=c.entity_id join public.green_line_entity_settings s on s.entity_id=e.id "
          + "where c.entity_id=? and c.tenant_id=? and c.grant_id=? and e.archived_at is null and s.enabled for share of c,e,s",
          entity, access.tenantId(), access.grantId());
      if (mapping.isEmpty()) throw failure("QUOTE_SYNC_CONNECTION_CHANGED", "The Xero connection changed. Rerun sync.");
      int updated = jdbc.update("update public.green_line_sync_runs set status='SUCCEEDED',finished_at=?,items_seen=?,items_created=?,items_updated=? "
          + "where entity_id=? and id=? and system='XERO' and status='RUNNING' and started_at>?",
          Timestamp.from(finished), result.seen(), result.created(), result.updated(), entity, run, Timestamp.from(finished.minusSeconds(1800)));
      if (updated != 1) throw failure("SYNC_EXPIRED", "The sync expired. Start another sync.");
      jdbc.update("update public.green_line_entity_settings set first_synced_at=coalesce(first_synced_at,?) where entity_id=?",
          Timestamp.from(finished), entity);
    });
  }

  public void fail(UUID entity, UUID run, String code, Integer upstream, Instant finished) {
    failSystem(entity, run, "XERO", code, upstream, finished);
  }
  public void failVwork(UUID entity, UUID run, String code, Integer upstream, Instant finished) {
    failSystem(entity, run, "VWORK", code, upstream, finished);
  }
  private void failSystem(UUID entity, UUID run, String system, String code, Integer upstream, Instant finished) {
    jdbc.update("update public.green_line_sync_runs set status='FAILED',finished_at=?,error_code=?,upstream_status=? "
        + "where entity_id=? and id=? and system=? and status='RUNNING'", Timestamp.from(finished), code, upstream, entity, run, system);
  }

  public void succeedVwork(UUID entity, UUID run, VworkConnectionService.Access access, VworkSync.Result result, Instant finished) {
    tx.executeWithoutResult(status -> {
      var connection = jdbc.queryForList("select v.id from public.vwork_connection v join public.green_line_entity_settings s on s.entity_id=? "
          + "join public.entities e on e.id=s.entity_id where v.id=1 and v.status='ACTIVE' and v.xmin::text=? "
          + "and s.enabled and e.archived_at is null for share of v,s,e", entity, access.revision());
      if (connection.isEmpty()) throw failure("VWORK_CONNECTION_CHANGED", "The vWork connection changed. Retry sync.");
      int updated = jdbc.update("update public.green_line_sync_runs set status='SUCCEEDED',finished_at=?,items_seen=?,items_updated=? "
          + "where entity_id=? and id=? and system='VWORK' and status='RUNNING' and started_at>?",
          Timestamp.from(finished), result.observed() + result.ignored(), result.linked(), entity, run, Timestamp.from(finished.minusSeconds(1800)));
      if (updated != 1) throw failure("SYNC_EXPIRED", "The sync expired. Start another sync.");
    });
  }

  private static ApiFailure failure(String code, String detail) { return new ApiFailure(HttpStatus.CONFLICT, code, detail); }
}
