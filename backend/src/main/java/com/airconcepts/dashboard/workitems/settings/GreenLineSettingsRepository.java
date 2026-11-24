package com.airconcepts.dashboard.workitems.settings;

import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.time.WorkingDays;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Called inside a service transaction; staff administration uses the same advisory lock. */
@Repository
public class GreenLineSettingsRepository {
  public record Settings(UUID entityId, boolean enabled, UUID defaultOwnerId, UUID serviceManagerId,
      int lateThresholdWorkingDays, int backfillDays) {
    public static Settings defaults(UUID entityId) { return new Settings(entityId, false, null, null, 2, 90); }
  }

  private final JdbcTemplate jdbc;
  public GreenLineSettingsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<Role> lockActor(UUID actor) {
    jdbc.execute("select pg_advisory_xact_lock(150016)");
    return findStaffRole(actor);
  }

  public Optional<Role> findStaffRole(UUID id) {
    return jdbc.query("select role from public.staff_users where id=? and active",
        (row, index) -> Role.valueOf(row.getString("role")), id).stream().findFirst();
  }

  public boolean lockEntity(UUID entity) {
    return !jdbc.query("select id from public.entities where id=? and archived_at is null for share",
        (row, index) -> row.getObject("id", UUID.class), entity).isEmpty();
  }

  public boolean entityExists(UUID entity) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "select exists(select 1 from public.entities where id=? and archived_at is null)", Boolean.class, entity));
  }

  public Optional<Settings> get(UUID entity) {
    return jdbc.query("""
        select entity_id,enabled,default_owner_id,service_manager_id,late_threshold_working_days,backfill_days
        from public.green_line_entity_settings where entity_id=?
        """, (row, index) -> new Settings(row.getObject("entity_id", UUID.class), row.getBoolean("enabled"),
        row.getObject("default_owner_id", UUID.class), row.getObject("service_manager_id", UUID.class),
        row.getInt("late_threshold_working_days"), row.getInt("backfill_days")), entity).stream().findFirst();
  }

  public void save(Settings settings, UUID actor) {
    jdbc.update("""
        insert into public.green_line_entity_settings(entity_id,enabled,default_owner_id,service_manager_id,
          late_threshold_working_days,backfill_days,updated_by)
        values (?,?,?,?,?,?,?)
        on conflict (entity_id) do update set enabled=excluded.enabled,default_owner_id=excluded.default_owner_id,
          service_manager_id=excluded.service_manager_id,late_threshold_working_days=excluded.late_threshold_working_days,
          backfill_days=excluded.backfill_days,updated_by=excluded.updated_by,updated_at=now()
        """, settings.entityId(), settings.enabled(), settings.defaultOwnerId(), settings.serviceManagerId(),
        settings.lateThresholdWorkingDays(), settings.backfillDays(), actor);
  }

  public void auditSettings(UUID entity, UUID actor, Settings before, Settings after) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,actor_id,source,event_type,before,after)
        values (?,?,'USER','SETTINGS_CHANGED',
          jsonb_build_object('enabled',cast(? as boolean),'defaultOwnerId',cast(? as uuid),
            'serviceManagerId',cast(? as uuid),'lateThresholdWorkingDays',cast(? as integer),'backfillDays',cast(? as integer)),
          jsonb_build_object('enabled',cast(? as boolean),'defaultOwnerId',cast(? as uuid),
            'serviceManagerId',cast(? as uuid),'lateThresholdWorkingDays',cast(? as integer),'backfillDays',cast(? as integer)))
        """, entity, actor, before.enabled(), before.defaultOwnerId(), before.serviceManagerId(),
        before.lateThresholdWorkingDays(), before.backfillDays(), after.enabled(), after.defaultOwnerId(),
        after.serviceManagerId(), after.lateThresholdWorkingDays(), after.backfillDays());
  }

  /** Update only currently unowned items and append an event for every assignment. */
  public void fillUnowned(UUID entity, UUID actor, UUID defaultOwner, UUID serviceManager) {
    lockCurrentMapping(entity);
    if (serviceManager != null) assignUnowned(entity, actor, serviceManager, true);
    if (defaultOwner != null) assignUnowned(entity, actor, defaultOwner, false);
  }

  /** Derived deadlines use the Auckland calendar date on which the current stage began. */
  public void recalculateLateDates(UUID entity, int threshold) {
    lockCurrentMapping(entity);
    record Entry(UUID id, java.time.LocalDate entered) {}
    var entries = jdbc.query("""
        select w.id,w.stage_entered_at from public.work_items w
        join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id
        where w.entity_id=? and w.stage in ('JOB_COMPLETED') for update of w
        """, (row, index) -> new Entry(row.getObject("id", UUID.class),
        row.getObject("stage_entered_at", OffsetDateTime.class).atZoneSameInstant(ZoneId.of("Pacific/Auckland")).toLocalDate()), entity);
    var workingDays = new WorkingDays(Set.of());
    for (var entry : entries) {
      var deadline = workingDays.addWorkingDays(entry.entered(), threshold);
      jdbc.update("""
          update public.work_items set late_after=?,version=version+1,updated_at=now()
          where entity_id=? and id=? and late_after is distinct from ?
          """, deadline, entity, entry.id(), deadline);
    }
  }

  private void assignUnowned(UUID entity, UUID actor, UUID owner, boolean manager) {
    String stages = manager ? "('TO_SCHEDULE','SCHEDULED','WORK_IN_PROGRESS')" : "('QUOTED','JOB_COMPLETED','INVOICED')";
    // The stage list is a fixed server-side constant; no user-controlled SQL is interpolated.
    jdbc.update("""
        with assigned as (
          update public.work_items set owner_id=?,
            attention_codes=array_remove(array_remove(array_remove(attention_codes,'NO_SERVICE_MANAGER'),
              'NO_DEFAULT_OWNER'),'OWNER_INACTIVE'),version=version+1,updated_at=now()
          where entity_id=? and owner_id is null and stage in %s
            and exists(select 1 from public.xero_entity_connections c where c.entity_id=work_items.entity_id
              and c.tenant_id=work_items.xero_tenant_id) returning id,stage
        )
        insert into public.work_item_events(entity_id,work_item_id,actor_id,source,event_type,
          from_stage,to_stage,from_owner_id,to_owner_id)
        select ?,id,?,'USER','OWNER_ASSIGNED',stage,stage,null,? from assigned
        """.formatted(stages), owner, entity, entity, actor, owner);
  }

  private void lockCurrentMapping(UUID entity) {
    jdbc.queryForList("select entity_id from public.xero_entity_connections where entity_id=? for share", entity);
  }
}
