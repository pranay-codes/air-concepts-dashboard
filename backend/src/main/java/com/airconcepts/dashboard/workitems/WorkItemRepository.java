package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.security.Role;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL used under the service transaction; all item operations are scoped to an enabled entity. */
@Repository
public class WorkItemRepository {
  public record Item(UUID id, UUID entityId, WorkItemStage stage, UUID ownerId,
      LocalDate followUpDate, String notes, int version) {}

  private final JdbcTemplate jdbc;
  public WorkItemRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<Role> lockActor(UUID actor) {
    // Staff role changes and deactivations acquire the same transaction-scoped lock.
    jdbc.execute("select pg_advisory_xact_lock(150016)");
    return jdbc.query("select role from public.staff_users where id=? and active",
        (row, index) -> Role.valueOf(row.getString("role")), actor).stream().findFirst();
  }

  public Optional<Item> lockItem(UUID entity, UUID id) {
    return jdbc.query("""
        select w.id,w.entity_id,w.stage,w.owner_id,w.follow_up_date,w.notes,w.version
        from public.work_items w
        join public.entities e on e.id=w.entity_id and e.archived_at is null
        join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled
        join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id
        where w.entity_id=? and w.id=? for update of w for share of e,s,c
        """, (row, index) -> new Item(row.getObject("id", UUID.class), row.getObject("entity_id", UUID.class),
        WorkItemStage.valueOf(row.getString("stage")), row.getObject("owner_id", UUID.class),
        row.getObject("follow_up_date", LocalDate.class), row.getString("notes"), row.getInt("version")), entity, id)
        .stream().findFirst();
  }

  /** Internal outcome bookkeeping must survive an entity being disabled or remapped during a provider call. */
  public Optional<Item> lockItemForOutcome(UUID entity, UUID id) {
    return jdbc.query("""
        select id,entity_id,stage,owner_id,follow_up_date,notes,version from public.work_items
        where entity_id=? and id=? for update
        """, (row, index) -> new Item(row.getObject("id", UUID.class), row.getObject("entity_id", UUID.class),
        WorkItemStage.valueOf(row.getString("stage")), row.getObject("owner_id", UUID.class),
        row.getObject("follow_up_date", LocalDate.class), row.getString("notes"), row.getInt("version")), entity, id)
        .stream().findFirst();
  }

  /** Owners are active Directors or Financial Admins; the staff table is global, not per entity. */
  public boolean isAssignableOwner(UUID staffId) {
    Boolean assignable = jdbc.queryForObject("""
        select exists(select 1 from public.staff_users where id=? and active and role in ('DIRECTOR','FINANCIAL_ADMIN'))
        """, Boolean.class, staffId);
    return Boolean.TRUE.equals(assignable);
  }

  public int save(UUID entity, UUID id, WorkItemStage stage, LocalDate followUp, String notes, UUID ownerId, int version) {
    return jdbc.update("""
        update public.work_items set follow_up_date=?,notes=?,owner_id=?,version=version+1,updated_at=now()
        where entity_id=? and id=? and stage=? and version=?
        """, followUp, notes, ownerId, entity, id, stage.name(), version);
  }

  public int closeLost(UUID entity, UUID id, int version, String reason) {
    return jdbc.update("""
        update public.work_items set stage='CLOSED',close_reason='LOST',closing_note=?,
          stage_entered_at=now(),late_after=null,version=version+1,updated_at=now()
        where entity_id=? and id=? and stage='QUOTED' and version=?
          and not exists (select 1 from public.external_operations op where op.entity_id=work_items.entity_id
            and op.work_item_id=work_items.id and (op.status in ('PENDING','UNKNOWN')
              or (op.kind='XERO_CREATE_INVOICE' and op.status='SUCCEEDED')))
        """, reason, entity, id, version);
  }

  public int closeNoInvoice(UUID entity, UUID id, int version, String reason) {
    return jdbc.update("""
        update public.work_items set stage='CLOSED',close_reason='NO_INVOICE_NEEDED',closing_note=?,
          stage_entered_at=now(),late_after=null,version=version+1,updated_at=now()
        where entity_id=? and id=? and stage='TO_INVOICE' and version=? and xero_invoice_id is null
          and not exists (select 1 from public.external_operations op where op.entity_id=work_items.entity_id
            and op.work_item_id=work_items.id and (op.status in ('PENDING','UNKNOWN')
              or (op.kind='XERO_CREATE_INVOICE' and op.status='SUCCEEDED')))
        """, reason, entity, id, version);
  }

  public int markSent(UUID entity, UUID id, int version) {
    return jdbc.update("""
        update public.work_items set stage='INVOICED',stage_entered_at=now(),late_after=null,version=version+1,updated_at=now()
        where entity_id=? and id=? and version=? and stage='INVOICE_DRAFTED' and xero_invoice_id is not null
          and invoice_status='AUTHORISED' and not exists (select 1 from public.external_operations op
            where op.entity_id=work_items.entity_id and op.work_item_id=work_items.id and op.status in ('PENDING','UNKNOWN'))
        """,entity,id,version);
  }
  public void auditMarkSent(UUID entity, UUID item, UUID actor) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,actor_id,source,event_type,from_stage,to_stage)
        values (?,?,?,'USER','MARKED_SENT','INVOICE_DRAFTED','INVOICED')
        """,entity,item,actor);
  }

  /** Called after locking the item; reservation and outcome processing acquire the same item lock. */
  public boolean hasBlockingOperation(UUID entity, UUID item) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from public.external_operations where entity_id=? and work_item_id=?
          and (status in ('PENDING','UNKNOWN') or (kind='XERO_CREATE_INVOICE' and status='SUCCEEDED')))
        """, Boolean.class, entity, item));
  }

  public void auditEdit(UUID entity, UUID item, UUID actor, String event, String before, String after) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,actor_id,source,event_type,before,after)
        values (?,?,?,'USER',?,jsonb_build_object('value',cast(? as text)),jsonb_build_object('value',cast(? as text)))
        """, entity, item, actor, event, before, after);
  }

  public void auditClose(UUID entity, UUID item, UUID actor, String reason) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,actor_id,source,event_type,from_stage,to_stage,after)
        values (?,?,?,'USER','CLOSED_LOST','QUOTED','CLOSED',jsonb_build_object('reason',cast(? as text)))
        """, entity, item, actor, reason);
  }

  public void auditNoInvoice(UUID entity, UUID item, UUID actor, String reason) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,actor_id,source,event_type,from_stage,to_stage,after)
        values (?,?,?,'USER','CLOSED_NO_INVOICE','TO_INVOICE','CLOSED',jsonb_build_object('reason',cast(? as text)))
        """, entity, item, actor, reason);
  }
}
