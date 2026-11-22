package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import java.sql.Timestamp;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistence is always scoped to the locked entity and its server-resolved Xero tenant. */
@Repository
public class InvoiceMirrorRepository {
  public record Candidate(UUID itemId, UUID invoiceId) {}
  public record Unlinked(UUID itemId, String jobId, String quoteNumber) {}
  public record Stored(int version, UUID invoiceId, StageMachine.State state, String number, String status,
      Boolean sent, BigDecimal total, BigDecimal amountDue, LocalDate dueDate) {
    public Stored(int version, UUID invoiceId, StageMachine.State state) { this(version,invoiceId,state,null,null,null,null,null,null); }
  }
  private final JdbcTemplate jdbc;
  public InvoiceMirrorRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

  public List<Candidate> candidates(UUID entity) {
    return jdbc.query("""
        select id,xero_invoice_id from public.work_items where entity_id=? and xero_invoice_id is not null
          and stage in ('TO_INVOICE','INVOICE_DRAFTED','INVOICED') order by stage_entered_at,id
        """, (r,n) -> new Candidate(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),entity);
  }
  public List<Unlinked> unlinked(UUID entity) {
    return jdbc.query("""
        select id,job_id,quote_number from public.work_items where entity_id=? and xero_invoice_id is null
          and stage='TO_INVOICE' order by stage_entered_at,id limit 20
        """, (r,n) -> new Unlinked(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),entity);
  }
  public boolean referenceMatches(QuoteMirrorService.Mapping mapping, Unlinked candidate) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from public.work_items where entity_id=? and id=? and xero_tenant_id=?
          and stage='TO_INVOICE' and xero_invoice_id is null and job_id is not distinct from ? and quote_number=?)
        """,Boolean.class,mapping.entityId(),candidate.itemId(),mapping.tenantId(),candidate.jobId(),candidate.quoteNumber()));
  }
  public Optional<Stored> lock(QuoteMirrorService.Mapping mapping, UUID item) {
    return jdbc.query("""
        select * from public.work_items where entity_id=? and id=? and xero_tenant_id=? for update
        """, (r,n) -> {
      var invoice=r.getObject("xero_invoice_id",UUID.class);
      var codes=(String[])r.getArray("attention_codes").getArray();
      return new Stored(r.getInt("version"),invoice,new StageMachine.State(WorkItemStage.valueOf(r.getString("stage")),
          r.getObject("owner_id",UUID.class),r.getString("close_reason"),r.getString("closing_note"),
          r.getString("vwork_job_id")!=null,invoice!=null,Set.of(codes)),r.getString("invoice_number"),
          r.getString("invoice_status"),r.getObject("invoice_sent",Boolean.class),r.getBigDecimal("invoice_total"),
          r.getBigDecimal("invoice_amount_due"),r.getObject("invoice_due_date",LocalDate.class));
    },mapping.entityId(),item,mapping.tenantId()).stream().findFirst();
  }
  public int update(QuoteMirrorService.Mapping mapping, UUID item, XeroInvoiceClient.WorkflowInvoice invoice,
      StageMachine.State next, int version, Instant now, LocalDate lateAfter, StageMachine.State before) {
    boolean stageChanged=next.stage()!=before.stage();
    boolean linked=next.invoiceLinked();
    return jdbc.update("""
        update public.work_items set xero_invoice_id=?,invoice_number=?,invoice_status=?,invoice_sent=?,invoice_total=?,
          invoice_amount_due=?,invoice_due_date=?,stage=?,close_reason=?,closing_note=?,attention_codes=string_to_array(?,','),
          stage_entered_at=case when ? then ? else stage_entered_at end,
          late_after=case when ? then ? else late_after end,version=version+1,updated_at=now()
        where entity_id=? and id=? and xero_tenant_id=? and version=? and xero_invoice_id=?
        """,linked?invoice.id():null,linked?invoice.number():null,linked?invoice.status().name():null,
        linked?invoice.sentToContact():null,linked?invoice.total():null,linked?invoice.amountDue():null,
        linked?invoice.dueDate():null,next.stage().name(),next.closeReason(),next.closingNote(),
        String.join(",",next.attention().stream().sorted().toList()),stageChanged,Timestamp.from(now),stageChanged,lateAfter,
        mapping.entityId(),item,mapping.tenantId(),version,invoice.id());
  }
  public int link(QuoteMirrorService.Mapping mapping, Unlinked candidate, XeroInvoiceClient.WorkflowInvoice invoice,
      StageMachine.State next, int version, Instant now, LocalDate lateAfter) {
    return jdbc.update("""
        update public.work_items set xero_invoice_id=?,invoice_number=?,invoice_status=?,invoice_sent=?,invoice_total=?,
          invoice_amount_due=?,invoice_due_date=?,stage=?,close_reason=?,closing_note=?,attention_codes=string_to_array(?,','),
          stage_entered_at=case when stage<>? then ? else stage_entered_at end,
          late_after=case when stage<>? then ? else late_after end,version=version+1,updated_at=now()
        where entity_id=? and id=? and xero_tenant_id=? and version=? and xero_invoice_id is null and stage='TO_INVOICE'
          and job_id is not distinct from ? and quote_number=?
        """,invoice.id(),invoice.number(),invoice.status().name(),invoice.sentToContact(),invoice.total(),invoice.amountDue(),
        invoice.dueDate(),next.stage().name(),next.closeReason(),next.closingNote(),String.join(",",next.attention().stream().sorted().toList()),
        next.stage().name(),Timestamp.from(now),next.stage().name(),lateAfter,mapping.entityId(),candidate.itemId(),mapping.tenantId(),
        version,candidate.jobId(),candidate.quoteNumber());
  }
  public int markMultiple(QuoteMirrorService.Mapping mapping, Unlinked candidate, int version) {
    return jdbc.update("""
        update public.work_items set attention_codes=array_append(attention_codes,'INVOICE_MULTIPLE_MATCHES'),version=version+1,updated_at=now()
        where entity_id=? and id=? and xero_tenant_id=? and version=? and stage='TO_INVOICE' and xero_invoice_id is null
          and job_id is not distinct from ? and quote_number=? and not ('INVOICE_MULTIPLE_MATCHES'=any(attention_codes))
        """,mapping.entityId(),candidate.itemId(),mapping.tenantId(),version,candidate.jobId(),candidate.quoteNumber());
  }
  public void markReportPending(QuoteMirrorService.Mapping mapping, UUID item) {
    jdbc.update("update public.work_items set report_status='PENDING' where entity_id=? and id=? and xero_tenant_id=? and report_status='NOT_APPLICABLE'",
        mapping.entityId(),item,mapping.tenantId());
  }
  public void audit(QuoteMirrorService.Mapping mapping, UUID item, XeroInvoiceClient.WorkflowInvoice invoice,
      Stored before, StageMachine.State after, String event) {
    jdbc.update("""
        insert into public.work_item_events(entity_id,work_item_id,source,event_type,from_stage,to_stage,before,after)
        values (?,?,'XERO_SYNC',?,?,?,jsonb_build_object('invoiceId',cast(? as uuid),'invoiceNumber',cast(? as text),
          'invoiceStatus',cast(? as text),'invoiceTotal',cast(? as numeric),'invoiceAmountDue',cast(? as numeric)),
          jsonb_build_object('closeReason',cast(? as text),'observedStatus',cast(? as text)))
        """,mapping.entityId(),item,event,before.state().stage().name(),after.stage().name(),before.invoiceId(),before.number(),
        before.status(),before.total(),before.amountDue(),after.closeReason(),invoice.status().name());
  }
}
