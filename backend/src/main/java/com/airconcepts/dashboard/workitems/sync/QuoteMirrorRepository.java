package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** All methods participate in the service transaction. No user-entered data is interpolated into SQL. */
@Repository
public class QuoteMirrorRepository {
  public record Config(boolean firstSync, int backfillDays, int threshold, StageMachine.Settings owners) {}
  public record Stored(UUID id, int version, StageMachine.State state, XeroQuoteClient.Quote quote, LocalDate lateAfter) {}
  private final JdbcTemplate jdbc;
  public QuoteMirrorRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<Config> lockConfig(UUID entity) {
    // Serialises hand-offs with staff deactivation and settings edits.
    jdbc.execute("select pg_advisory_xact_lock(150016)");
    return jdbc.query("""
        select s.first_synced_at,s.backfill_days,s.late_threshold_working_days,s.default_owner_id,s.service_manager_id,
          coalesce(d.active and d.role in ('DIRECTOR','FINANCIAL_ADMIN'),false) as owner_active,
          coalesce(m.active,false) as manager_active
        from public.green_line_entity_settings s join public.entities e on e.id=s.entity_id
        left join public.staff_users d on d.id=s.default_owner_id
        left join public.staff_users m on m.id=s.service_manager_id
        where s.entity_id=? and s.enabled and e.archived_at is null for share of s,e
        """, (r, n) -> new Config(r.getTimestamp("first_synced_at") == null, r.getInt("backfill_days"),
        r.getInt("late_threshold_working_days"), new StageMachine.Settings(r.getObject("default_owner_id", UUID.class),
        r.getBoolean("owner_active"), r.getObject("service_manager_id", UUID.class), r.getBoolean("manager_active"))), entity)
        .stream().findFirst();
  }

  /** Held until the caller's transaction ends, preventing a concurrent mapping update. */
  public boolean lockMapping(QuoteMirrorService.Mapping mapping) {
    return !jdbc.queryForList("""
        select c.entity_id from public.xero_entity_connections c
        join public.entities e on e.id=c.entity_id
        where c.entity_id=? and c.tenant_id=? and c.grant_id=? and e.archived_at is null
        for share of c,e
        """, mapping.entityId(), mapping.tenantId(), mapping.grantId()).isEmpty();
  }

  public boolean provenanceMatches(QuoteMirrorService.Mapping mapping) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        select not exists(select 1 from public.work_items
          where entity_id=? and xero_tenant_id is distinct from ?)
        """, Boolean.class, mapping.entityId(), mapping.tenantId()));
  }

  public void lockIdentity(UUID entity, UUID quoteId) {
    jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
        "green-line-quote:" + entity + ":" + quoteId);
  }

  public Optional<Stored> lockQuote(UUID entity, UUID quoteId) {
    return jdbc.query("select * from public.work_items where entity_id=? and xero_quote_id=? for update", (r, n) -> {
      String[] codes = (String[]) r.getArray("attention_codes").getArray();
      var state = new StageMachine.State(WorkItemStage.valueOf(r.getString("stage")), r.getObject("owner_id", UUID.class),
          r.getString("close_reason"), r.getString("closing_note"), r.getString("vwork_job_id") != null,
          r.getObject("xero_invoice_id", UUID.class) != null, Set.of(codes));
      Timestamp updated = r.getTimestamp("xero_quote_updated_at");
      var quote = new XeroQuoteClient.Quote(r.getObject("xero_quote_id", UUID.class), r.getString("quote_number"),
          r.getString("xero_quote_reference"), XeroQuoteClient.Status.valueOf(r.getString("xero_quote_status")),
          r.getObject("xero_contact_id", UUID.class), r.getString("customer_name"), r.getObject("quote_date", LocalDate.class),
          r.getObject("quote_expiry_date", LocalDate.class), updated == null ? null : updated.toInstant(), r.getBigDecimal("quote_total"),
          r.getString("quote_currency"), null, null);
      return new Stored(r.getObject("id", UUID.class), r.getInt("version"), state, quote, r.getObject("late_after", LocalDate.class));
    }, entity, quoteId).stream().findFirst();
  }

  /** Caller already holds the work-item lock shared by operation reservation and outcome changes. */
  public boolean hasUnresolvedOperation(UUID entity, UUID item) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from public.external_operations where entity_id=? and work_item_id=?
          and status in ('PENDING','UNKNOWN'))
        """, Boolean.class, entity, item));
  }

  public UUID insert(QuoteMirrorService.Mapping mapping, XeroQuoteClient.Quote q, StageMachine.State state, Instant now, LocalDate lateAfter, String origin) {
    return Objects.requireNonNull(jdbc.queryForObject("""
        insert into public.work_items(entity_id,xero_tenant_id,xero_quote_id,quote_number,job_id,xero_quote_reference,xero_quote_status,
          xero_contact_id,customer_name,quote_total,quote_currency,quote_date,quote_expiry_date,xero_quote_updated_at,
          stage,close_reason,closing_note,owner_id,stage_entered_at,late_after,attention_codes,origin)
        values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,string_to_array(?,','),?) returning id
        """, UUID.class, mapping.entityId(), mapping.tenantId(), q.id(), q.number(), jobId(q.reference()), q.reference(), q.status().name(),
        q.contactId(), q.contactName(), q.total(), q.currency(), q.date(), q.expiryDate(), Timestamp.from(q.updatedAt()),
        state.stage().name(), state.closeReason(), state.closingNote(), state.ownerId(), Timestamp.from(now), lateAfter,
        codes(state.attention()), origin));
  }

  public int update(UUID entity, UUID id, XeroQuoteClient.Quote q, StageMachine.State state, int version,
      Instant now, boolean stageChanged, LocalDate lateAfter) {
    return jdbc.update("""
        update public.work_items set quote_number=?,job_id=?,xero_quote_reference=?,xero_quote_status=?,xero_contact_id=?,
          customer_name=?,quote_total=?,quote_currency=?,quote_date=?,quote_expiry_date=?,xero_quote_updated_at=?,
          stage=?,close_reason=?,closing_note=?,owner_id=?,attention_codes=string_to_array(?,','),
          stage_entered_at=case when ? then ? else stage_entered_at end,
          late_after=case when ? then ? else late_after end,updated_at=now(),version=version+1
        where entity_id=? and id=? and version=?
        """, q.number(), jobId(q.reference()), q.reference(), q.status().name(), q.contactId(), q.contactName(), q.total(),
        q.currency(), q.date(), q.expiryDate(), Timestamp.from(q.updatedAt()), state.stage().name(), state.closeReason(),
        state.closingNote(), state.ownerId(), codes(state.attention()), stageChanged, Timestamp.from(now), stageChanged, lateAfter,
        entity, id, version);
  }

  public void audit(UUID entity, UUID item, StageMachine.State before, StageMachine.State after, String event) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,source,event_type,from_stage,to_stage,from_owner_id,to_owner_id,after)
        values (?,?,'XERO_SYNC',?,?,?,?,?,jsonb_build_object('closeReason',cast(? as text),'closingNote',cast(? as text)))
        """, entity, item, event, before == null ? null : before.stage().name(), after.stage().name(),
        before == null ? null : before.ownerId(), after.ownerId(), after.closeReason(), after.closingNote());
  }

  public static String jobId(String reference) {
    return reference != null && reference.codePointCount(0, reference.length()) <= 255 ? reference : null;
  }
  private static String codes(Set<String> attention) { return String.join(",", attention.stream().sorted().toList()); }
}
