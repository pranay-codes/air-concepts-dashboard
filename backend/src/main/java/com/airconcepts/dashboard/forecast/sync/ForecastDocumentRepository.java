package com.airconcepts.dashboard.forecast.sync;

import com.airconcepts.dashboard.invoices.InvoiceType;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ForecastDocumentRepository {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private static final RowMapper<SyncRun> RUN = (r,n) -> new SyncRun(r.getObject("id",UUID.class), r.getString("trigger"),
      r.getTimestamp("started_at").toInstant(), r.getTimestamp("finished_at")==null?null:r.getTimestamp("finished_at").toInstant(),
      r.getString("status"),r.getInt("documents_seen"),r.getInt("documents_inserted"),r.getInt("documents_updated"),
      r.getInt("documents_settled"),r.getInt("conflicts_flagged"),r.getString("error_code"),r.getObject("upstream_status",Integer.class),
      r.getObject("day_limit_remaining",Integer.class),r.getObject("minute_limit_remaining",Integer.class),r.getObject("app_minute_limit_remaining",Integer.class));
  public ForecastDocumentRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
    this.jdbc=jdbc; this.tx=new TransactionTemplate(transactions);
  }
  public UUID begin(UUID entity, String trigger, UUID actor, Instant started) {
    return tx.execute(s -> {
      jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", "forecast-sync:"+entity);
      Boolean running=jdbc.queryForObject("select exists(select 1 from public.forecast_sync_runs where entity_id=? and status='RUNNING' and started_at>?)",
          Boolean.class,entity,Timestamp.from(started.minusSeconds(1800)));
      if (Boolean.TRUE.equals(running)) throw failure("FORECAST_SYNC_IN_PROGRESS","A sync is already running. Refresh its history.");
      jdbc.update("update public.forecast_sync_runs set status='FAILED',finished_at=?,error_code='FORECAST_SYNC_EXPIRED' where entity_id=? and status='RUNNING'",
          Timestamp.from(started),entity);
      UUID id=UUID.randomUUID();
      jdbc.update("insert into public.forecast_sync_runs(id,entity_id,trigger,triggered_by,started_at,status) values(?,?,?,?,?,'RUNNING')",
          id,entity,trigger,actor,Timestamp.from(started));
      return id;
    });
  }
  public List<UUID> connectedEntities() {
    return jdbc.query("select e.id from public.entities e join public.xero_entity_connections c on c.entity_id=e.id where e.archived_at is null order by e.id",
        (r,n)->r.getObject(1,UUID.class));
  }
  public List<UUID> outstanding(UUID entity, InvoiceType type) {
    return jdbc.query("select xero_document_id from public.forecast_documents where entity_id=? and document_type=? and outstanding order by xero_document_id",
        (r,n)->r.getObject(1,UUID.class),entity,type.name());
  }
  public List<SyncRun> history(UUID entity) {
    return jdbc.query("select * from public.forecast_sync_runs where entity_id=? order by started_at desc,id desc limit 10",RUN,entity);
  }
  public SyncRun get(UUID entity, UUID run) {
    return jdbc.query("select * from public.forecast_sync_runs where entity_id=? and id=?",RUN,entity,run).stream().findFirst()
        .orElseThrow(()->failure("FORECAST_RUN_UNAVAILABLE","Sync run is unavailable."));
  }
  public void apply(UUID entity, UUID run, TenantAccess access, List<ForecastSource> documents,
      List<UUID> deleted, ForecastSource.Limits limits, Instant now) {
    tx.executeWithoutResult(s -> {
      var owners=jdbc.queryForList("select id from public.forecast_sync_runs where entity_id=? and id=? and status='RUNNING' and started_at>? for update",
          entity,run,Timestamp.from(now.minusSeconds(1800)));
      if (owners.isEmpty()) throw failure("FORECAST_SYNC_EXPIRED","This sync expired; request a new run.");
      var mappings=jdbc.queryForList("select c.entity_id from public.xero_entity_connections c join public.entities e on e.id=c.entity_id "
          +"where c.entity_id=? and c.tenant_id=? and c.grant_id=? and e.archived_at is null for share of c,e",entity,access.tenantId(),access.grantId());
      if (mappings.isEmpty()) throw failure("XERO_CONNECTION_CHANGED","The entity connection changed. Rerun sync.");
      Boolean remapped=jdbc.queryForObject("select exists(select 1 from public.forecast_documents where entity_id=? and tenant_id<>?)",Boolean.class,entity,access.tenantId());
      if (Boolean.TRUE.equals(remapped)) throw failure("FORECAST_TENANT_CHANGED","Stored forecast belongs to a different Xero organisation. Ask an administrator to review the mapping.");
      int inserted=0,updated=0,settled=0,flagged=0;
      for(var d:documents) {
        // Only documents that stay outstanding can be reviewed; a settled document must not keep an unresolvable conflict.
        if(d.outstanding()) flagged+=flagConflict(entity,d.id(),d.expectedDate(),d.amountDue(),now);
        boolean wasOutstanding=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.forecast_documents where entity_id=? and xero_document_id=? and outstanding)",Boolean.class,entity,d.id()));
        var rows=jdbc.query("""
            insert into public.forecast_documents as old (entity_id,xero_document_id,tenant_id,document_type,number,reference,contact_name,status,
            issue_date,due_date,xero_expected_date,currency,total,total_tax,amount_due,amount_paid,amount_credited,outstanding,xero_updated_at,last_synced_at,settled_at)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            on conflict(entity_id,xero_document_id) do update set number=excluded.number,reference=excluded.reference,contact_name=excluded.contact_name,
            status=excluded.status,issue_date=excluded.issue_date,due_date=excluded.due_date,xero_expected_date=excluded.xero_expected_date,
            currency=excluded.currency,total=excluded.total,total_tax=excluded.total_tax,amount_due=excluded.amount_due,amount_paid=excluded.amount_paid,
            amount_credited=excluded.amount_credited,outstanding=excluded.outstanding,xero_updated_at=excluded.xero_updated_at,last_synced_at=excluded.last_synced_at,
            settled_at=case when old.outstanding and not excluded.outstanding then excluded.last_synced_at when excluded.outstanding then null else old.settled_at end
            where row(old.number,old.reference,old.contact_name,old.status,old.issue_date,old.due_date,old.xero_expected_date,old.currency,
            old.total,old.total_tax,old.amount_due,old.amount_paid,old.amount_credited,old.outstanding,old.xero_updated_at) is distinct from
            row(excluded.number,excluded.reference,excluded.contact_name,excluded.status,excluded.issue_date,excluded.due_date,excluded.xero_expected_date,
            excluded.currency,excluded.total,excluded.total_tax,excluded.amount_due,excluded.amount_paid,excluded.amount_credited,excluded.outstanding,excluded.xero_updated_at)
            returning (xmax=0) as inserted
            """,(r,n)->r.getBoolean("inserted"),entity,d.id(),access.tenantId(),d.type().name(),d.number(),d.reference(),d.contact(),d.status().name(),
            d.issueDate(),d.dueDate(),d.expectedDate(),d.currency(),d.total(),d.totalTax(),d.amountDue(),d.amountPaid(),d.amountCredited(),d.outstanding(),
            Timestamp.from(d.updatedAt()),Timestamp.from(now),d.outstanding()?null:Timestamp.from(now));
        if(!rows.isEmpty()) { if(rows.getFirst()) inserted++; else updated++; if(wasOutstanding&&!d.outstanding()) settled++; }
      }
      for(UUID id:deleted) {
        settled+=jdbc.update("update public.forecast_documents set status='DELETED',amount_due=0,outstanding=false,settled_at=?,last_synced_at=? "
          +"where entity_id=? and xero_document_id=? and outstanding",Timestamp.from(now),Timestamp.from(now),entity,id);
      }
      jdbc.update("update public.forecast_sync_runs set documents_seen=documents_seen+?,documents_inserted=documents_inserted+?,documents_updated=documents_updated+?,"
          +"documents_settled=documents_settled+?,conflicts_flagged=conflicts_flagged+?,day_limit_remaining=?,minute_limit_remaining=?,app_minute_limit_remaining=? where entity_id=? and id=?",
          documents.size()+deleted.size(),inserted,updated,settled,flagged,limits.day(),limits.minute(),limits.appMinute(),entity,run);
    });
  }
  private int flagConflict(UUID entity,UUID id,java.time.LocalDate expected,java.math.BigDecimal amount,Instant now){
    var old=jdbc.query("select xero_expected_date,amount_due,exists(select 1 from public.forecast_split_lines s where s.entity_id=d.entity_id and s.xero_document_id=d.xero_document_id) as has_splits "
        +"from public.forecast_documents d where entity_id=? and xero_document_id=? for update",(r,n)->{
          var date=r.getDate("xero_expected_date");var previous=date==null?null:date.toLocalDate();
          return new Object[]{previous,com.airconcepts.dashboard.forecast.ForecastConflictPolicy.changed(r.getBoolean("has_splits"),previous,r.getBigDecimal("amount_due"),expected,amount)};
        },entity,id);
    if(old.isEmpty()||!Boolean.TRUE.equals(old.getFirst()[1]))return 0;
    return jdbc.update("insert into public.forecast_document_adjustments as a(entity_id,xero_document_id,date_conflict_since,conflict_previous_xero_date,conflict_new_xero_date) values(?,?,?,?,?) "
        +"on conflict(entity_id,xero_document_id) do update set date_conflict_since=coalesce(a.date_conflict_since,excluded.date_conflict_since),"
        +"conflict_previous_xero_date=case when a.date_conflict_since is null then excluded.conflict_previous_xero_date else a.conflict_previous_xero_date end,conflict_new_xero_date=excluded.conflict_new_xero_date",
        entity,id,Timestamp.from(now),old.getFirst()[0],expected);
  }
  public void succeed(UUID entity,UUID run) {
    tx.executeWithoutResult(s -> {
      int count=jdbc.update("update public.forecast_sync_runs set status='SUCCEEDED',finished_at=now() where entity_id=? and id=? and status='RUNNING'",entity,run);
      if(count!=1) throw failure("FORECAST_SYNC_EXPIRED","The sync run changed. Refresh history.");
      jdbc.update("insert into public.forecast_audit(entity_id,record_table,record_key,action,after) select entity_id,'forecast_sync_runs',id::text,'SYNC',"
          +"jsonb_build_object('documents_seen',documents_seen,'documents_inserted',documents_inserted,'documents_updated',documents_updated,'documents_settled',documents_settled) "
          +"from public.forecast_sync_runs where entity_id=? and id=?",entity,run);
    });
  }
  public void fail(UUID entity,UUID run,String code,Integer status) {
    jdbc.update("update public.forecast_sync_runs set status='FAILED',finished_at=now(),error_code=?,upstream_status=? where entity_id=? and id=? and status='RUNNING'",code,status,entity,run);
  }
  private static ApiFailure failure(String code,String message) { return new ApiFailure(HttpStatus.CONFLICT,code,message); }
}
