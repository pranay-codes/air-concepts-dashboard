package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Called under a transaction; entity, current Xero mapping and shared vWork connection are locked. */
@Repository
public class VworkMirrorRepository {
  public record Candidate(UUID id, String reference, String jobId) {}
  public record Item(int version, String reference, String jobId, String status, String worker,
      Instant planned, Instant completed, StageMachine.State state, StageMachine.Settings settings, int threshold) {}
  /** The vWork facts kept with each history record; technician notes are deliberately excluded. */
  public record JobSnapshot(String status, String technician, Instant scheduledStart, Instant completedAt) {}
  private final JdbcTemplate jdbc;
  public VworkMirrorRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public List<Candidate> candidates(UUID entity) {
    String base = "select w.id,w.job_id,w.vwork_job_id from public.work_items w "
        + "join public.entities e on e.id=w.entity_id and e.archived_at is null "
        + "join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled "
        + "join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id "
        + "where w.entity_id=? and w.stage in ('TO_SCHEDULE','SCHEDULED','WORK_IN_PROGRESS','INVOICE_CREATED') ";
    var mapper = (org.springframework.jdbc.core.RowMapper<Candidate>) (r, n) -> new Candidate(
        r.getObject("id", UUID.class), r.getString("job_id"), r.getString("vwork_job_id"));
    var result = new ArrayList<>(jdbc.query(base + "and w.vwork_job_id is not null order by w.stage_entered_at,w.id", mapper, entity));
    result.addAll(jdbc.query(base + "and w.vwork_job_id is null and w.stage='TO_SCHEDULE' and w.job_id is not null "
        + "order by w.stage_entered_at,w.id limit 50", mapper, entity));
    return List.copyOf(result);
  }
  public List<Candidate> additionalTrackingCandidates(UUID entity) {
    return jdbc.query("""
        select w.id,w.job_id,w.vwork_job_id from public.work_items w
        join public.entities e on e.id=w.entity_id and e.archived_at is null
        join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled
        join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id
        where w.entity_id=? and w.stage in ('QUOTED','JOB_COMPLETED','INVOICE_CREATED','INVOICED')
          and (w.vwork_job_id is not null or w.job_id is not null)
        order by w.stage_entered_at,w.id
        """, (r,n) -> new Candidate(r.getObject("id",UUID.class),r.getString("job_id"),r.getString("vwork_job_id")),entity);
  }
  public Map<String, UUID> templateOwners() {
    var result = new HashMap<String, UUID>();
    jdbc.query("select template_ref,entity_id from public.vwork_entity_templates", r -> {
      result.put(r.getString("template_ref"), r.getObject("entity_id", UUID.class));
    });
    return Map.copyOf(result);
  }
  public Set<String> linkedJobs() {
    return Set.copyOf(jdbc.query("select vwork_job_id from public.work_items where vwork_job_id is not null",
        (r, n) -> r.getString(1)));
  }

  public Optional<Item> lock(UUID entity, UUID item, String revision) {
    jdbc.execute("select pg_advisory_xact_lock(150016)");
    jdbc.execute("select pg_advisory_xact_lock(150023)");
    return jdbc.query("""
        select w.*,s.default_owner_id,s.service_manager_id,s.late_threshold_working_days,
          coalesce(d.active and d.role in ('DIRECTOR','FINANCIAL_ADMIN'),false) as owner_active,
          coalesce(m.active,false) as manager_active
        from public.work_items w join public.entities e on e.id=w.entity_id and e.archived_at is null
        join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled
        join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id
        join public.vwork_connection v on v.id=1 and v.status='ACTIVE' and v.xmin::text=?
        left join public.staff_users d on d.id=s.default_owner_id
        left join public.staff_users m on m.id=s.service_manager_id
        where w.entity_id=? and w.id=? for update of w for share of e,s,c,v
        """, (r, n) -> {
      var codes = (String[]) r.getArray("attention_codes").getArray();
      var state = new StageMachine.State(WorkItemStage.valueOf(r.getString("stage")), r.getObject("owner_id", UUID.class),
          r.getString("close_reason"), r.getString("closing_note"), r.getString("vwork_job_id") != null,
          r.getObject("xero_invoice_id", UUID.class) != null, Set.of(codes));
      var settings = new StageMachine.Settings(r.getObject("default_owner_id", UUID.class), r.getBoolean("owner_active"),
          r.getObject("service_manager_id", UUID.class), r.getBoolean("manager_active"));
      return new Item(r.getInt("version"), r.getString("job_id"), r.getString("vwork_job_id"), r.getString("vwork_status"),
          r.getString("technician_name"), instant(r.getTimestamp("scheduled_start")), instant(r.getTimestamp("completed_at")),
          state, settings, r.getInt("late_threshold_working_days"));
    }, revision, entity, item).stream().findFirst();
  }

  public boolean templateBelongsTo(UUID entity, String template) {
    if (template == null || template.isBlank()) return false;
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.vwork_entity_templates where entity_id=? and template_ref=?)",
        Boolean.class, entity, template));
  }
  public boolean jobLinkedElsewhere(UUID item, String job) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.work_items where vwork_job_id=? and id<>?)",
        Boolean.class, job, item));
  }
  public boolean hasUnresolvedOperation(UUID entity, UUID item) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.external_operations "
        + "where entity_id=? and work_item_id=? and status in ('PENDING','UNKNOWN'))", Boolean.class, entity, item));
  }
  public int update(UUID entity, UUID item, VworkClient.Job job, StageMachine.State next, StageMachine.State before,
      int version, Instant now, LocalDate lateAfter) {
    boolean stageChanged = next.stage() != before.stage();
    return jdbc.update("""
        update public.work_items set vwork_job_id=?,vwork_status=?,technician_name=?,scheduled_start=?,completed_at=?,vwork_search_state='FOUND',
          stage=?,owner_id=?,attention_codes=string_to_array(?,','),
          stage_entered_at=case when ? then ? else stage_entered_at end,
          late_after=case when ? then ? else late_after end,updated_at=now(),version=version+1
        where entity_id=? and id=? and version=?
        """, job.id(), job.status().name(), job.workerName(), timestamp(job.plannedStart()), timestamp(job.completedAt()),
        next.stage().name(), next.ownerId(), String.join(",", next.attention().stream().sorted().toList()),
        stageChanged, Timestamp.from(now), stageChanged, lateAfter, entity, item, version);
  }
  /** A null previous snapshot means the job was not linked before this observation. */
  public void audit(UUID entity, UUID item, String event, StageMachine.State before, StageMachine.State after,
      JobSnapshot fromJob, JobSnapshot toJob) {
    var from = fromJob == null ? new JobSnapshot(null, null, null, null) : fromJob;
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,source,event_type,from_stage,to_stage,from_owner_id,to_owner_id,before,after)
        values (?,?,'VWORK_SYNC',?,?,?,?,?,
          jsonb_build_object('jobStatus',cast(? as text),'technician',cast(? as text),
            'scheduledStart',cast(? as text),'completedAt',cast(? as text)),
          jsonb_build_object('jobStatus',cast(? as text),'technician',cast(? as text),
            'scheduledStart',cast(? as text),'completedAt',cast(? as text)))
        """, entity, item, event, before.stage().name(), after.stage().name(), before.ownerId(), after.ownerId(),
        from.status(), from.technician(), text(from.scheduledStart()), text(from.completedAt()),
        toJob.status(), toJob.technician(), text(toJob.scheduledStart()), text(toJob.completedAt()));
  }
  private static String text(Instant value) { return value == null ? null : value.toString(); }
  private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
  private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
}
