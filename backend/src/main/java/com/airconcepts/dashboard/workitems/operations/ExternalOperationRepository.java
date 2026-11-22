package com.airconcepts.dashboard.workitems.operations;

import java.util.Optional;
import java.util.UUID;
import com.airconcepts.dashboard.vwork.VworkClient;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Used under the item lock and the surrounding reservation transaction. */
@Repository
public class ExternalOperationRepository {
  public enum Kind { VWORK_CREATE_JOB, XERO_CREATE_INVOICE, XERO_ATTACH_REPORT }
  public record Context(String jobId, UUID invoiceId, UUID serviceManagerId) {}
  public record Existing(UUID id, OperationState.Status status, int attempt) {
    public Existing(OperationState.Status status, int attempt) { this(null, status, attempt); }
  }
  public record Locked(Kind kind, OperationState.Status status, String lookupReference) {
    public Locked(Kind kind, OperationState.Status status) { this(kind, status, null); }
  }
  private final JdbcTemplate jdbc;
  public ExternalOperationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Context context(UUID entity, UUID item) {
    return jdbc.queryForObject("""
        select w.vwork_job_id,w.xero_invoice_id,s.service_manager_id from public.work_items w
        join public.green_line_entity_settings s on s.entity_id=w.entity_id
        where w.entity_id=? and w.id=?
        """, (row, index) -> new Context(row.getString("vwork_job_id"), row.getObject("xero_invoice_id", UUID.class),
        row.getObject("service_manager_id", UUID.class)), entity, item);
  }

  public Optional<Existing> latest(UUID entity, UUID item, Kind kind) {
    return jdbc.query("""
        select id,status,attempt from public.external_operations where entity_id=? and work_item_id=? and kind=?
        order by attempt desc limit 1
        """, (row, index) -> new Existing(row.getObject("id",UUID.class), OperationState.Status.valueOf(row.getString("status")), row.getInt("attempt")),
        entity, item, kind.name()).stream().findFirst();
  }

  public void insert(UUID id, UUID entity, UUID item, Kind kind, int attempt, String lookup, String fingerprint, UUID actor) {
    jdbc.update("""
        insert into public.external_operations(id,entity_id,work_item_id,kind,status,attempt,lookup_reference,request_fingerprint,requested_by)
        values (?,?,?,?,'PENDING',?,?,?,?)
        """, id, entity, item, kind.name(), attempt, lookup, fingerprint, actor);
  }

  public int bumpVersion(UUID entity, UUID item, int version) {
    return jdbc.update("update public.work_items set version=version+1,updated_at=now() where entity_id=? and id=? and version=?",
        entity, item, version);
  }

  public void auditRequested(UUID entity, UUID item, UUID actor, UUID operation, Kind kind) {
    String event = kind == Kind.VWORK_CREATE_JOB ? "JOB_CREATE_REQUESTED" : "INVOICE_REQUESTED";
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,actor_id,source,event_type,after)
        values (?,?,?,'USER',?,jsonb_build_object('operationId',cast(? as uuid),'kind',cast(? as text)))
        """, entity, item, actor, event, operation, kind.name());
  }

  public Optional<Locked> lockOperation(UUID entity, UUID item, UUID operation) {
    return jdbc.query("""
        select kind,status,lookup_reference from public.external_operations where entity_id=? and work_item_id=? and id=? for update
        """, (row, index) -> new Locked(Kind.valueOf(row.getString("kind")),
        OperationState.Status.valueOf(row.getString("status")), row.getString("lookup_reference")), entity, item, operation).stream().findFirst();
  }

  public int linkVwork(UUID entity, UUID item, int version, String reference, VworkClient.Job job) {
    return jdbc.update("""
        update public.work_items set job_id=?,vwork_job_id=?,vwork_status=?,technician_name=?,scheduled_start=?,completed_at=?,
          vwork_search_state='FOUND',version=version+1,updated_at=now()
        where entity_id=? and id=? and version=? and stage='TO_SCHEDULE' and vwork_job_id is null
        """, reference, job.id(), job.status().name(), job.workerName(),
        job.plannedStart() == null ? null : Timestamp.from(job.plannedStart()),
        job.completedAt() == null ? null : Timestamp.from(job.completedAt()), entity, item, version);
  }
  public int complete(UUID entity, UUID item, UUID operation, OperationState.Result result, String externalId) {
    return jdbc.update("""
        update public.external_operations set status='SUCCEEDED',outcome=?,external_id=?,completed_at=now(),error_code=null,upstream_status=null
        where entity_id=? and work_item_id=? and id=? and status in ('PENDING','UNKNOWN')
        """, result.name(), externalId, entity, item, operation);
  }
  public void auditCompleted(UUID entity, UUID item, UUID operation, OperationState.Result result) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,source,event_type,after)
        values (?,?,'SYSTEM','JOB_LINKED',jsonb_build_object('operationId',cast(? as uuid),'outcome',cast(? as text)))
        """, entity, item, operation, result.name());
  }
  public void auditInvoiceCompleted(UUID entity, UUID item, UUID operation, OperationState.Result result) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,source,event_type,after)
        values (?,?,'SYSTEM','INVOICE_LINKED',jsonb_build_object('operationId',cast(? as uuid),'outcome',cast(? as text)))
        """,entity,item,operation,result.name());
  }

  /** The caller holds the item lock and has already made the operation terminal. */
  public void clearResolvedUnknown(UUID entity, UUID item) {
    jdbc.update("""
        update public.work_items w set attention_codes=array_remove(w.attention_codes,'OPERATION_UNKNOWN')
        where w.entity_id=? and w.id=? and 'OPERATION_UNKNOWN'=any(w.attention_codes)
          and not exists(select 1 from public.external_operations op where op.entity_id=w.entity_id
            and op.work_item_id=w.id and op.status='UNKNOWN')
        """, entity, item);
  }
  public void clearResolvedFailed(UUID entity, UUID item) {
    jdbc.update("""
        update public.work_items w set attention_codes=array_remove(w.attention_codes,'OPERATION_FAILED')
        where w.entity_id=? and w.id=? and 'OPERATION_FAILED'=any(w.attention_codes)
          and not exists(select 1 from public.external_operations op where op.entity_id=w.entity_id
            and op.work_item_id=w.id and op.status='FAILED' and not exists(
              select 1 from public.external_operations newer where newer.entity_id=op.entity_id
                and newer.work_item_id=op.work_item_id and newer.kind=op.kind and newer.attempt>op.attempt))
        """, entity, item);
  }
  public int markNotFound(UUID entity, UUID item, UUID operation) {
    return jdbc.update("""
        update public.external_operations set status='FAILED',outcome='NOT_FOUND',completed_at=now(),error_code=null,upstream_status=null
        where entity_id=? and work_item_id=? and id=? and kind='VWORK_CREATE_JOB' and status='UNKNOWN'
        """, entity, item, operation);
  }
  public int markLookupFailedAttention(UUID entity, UUID item, int version) {
    return jdbc.update("""
        update public.work_items w set attention_codes=
          case when 'OPERATION_FAILED'=any(w.attention_codes) then
            case when not exists(select 1 from public.external_operations op where op.entity_id=w.entity_id
              and op.work_item_id=w.id and op.status='UNKNOWN') then array_remove(w.attention_codes,'OPERATION_UNKNOWN')
              else w.attention_codes end
          else array_append(case when not exists(select 1 from public.external_operations op where op.entity_id=w.entity_id
              and op.work_item_id=w.id and op.status='UNKNOWN') then array_remove(w.attention_codes,'OPERATION_UNKNOWN')
              else w.attention_codes end,'OPERATION_FAILED') end,version=version+1,updated_at=now()
        where w.entity_id=? and w.id=? and w.version=?
        """, entity, item, version);
  }

  public int recordOutcome(UUID entity, UUID item, UUID operation, OperationState.Status status, String error, Integer upstreamStatus) {
    return jdbc.update("""
        update public.external_operations set status=?,error_code=?,upstream_status=?,
          completed_at=case when ?='FAILED' then now() else null end
        where entity_id=? and work_item_id=? and id=? and status='PENDING'
        """, status.name(), error, upstreamStatus, status.name(), entity, item, operation);
  }

  public int addAttention(UUID entity, UUID item, String attention, int version) {
    return jdbc.update("""
        update public.work_items set attention_codes=case when ?=any(attention_codes) then attention_codes
          else array_append(attention_codes,cast(? as text)) end,version=version+1,updated_at=now()
        where entity_id=? and id=? and version=?
        """, attention, attention, entity, item, version);
  }

  public void auditOutcome(UUID entity, UUID item, UUID operation, OperationState.Status status, String error) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,source,event_type,after)
        values (?,?,'SYSTEM','OPERATION_OUTCOME',jsonb_build_object('operationId',cast(? as uuid),
          'status',cast(? as text),'errorCode',cast(? as text)))
        """, entity, item, operation, status.name(), error);
  }
}
