package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.Split;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.airconcepts.dashboard.web.ApiFailure;
import org.springframework.http.HttpStatus;
@Repository
public class ForecastAdjustmentRepository {
  public record DocumentState(UUID id,BigDecimal amountDue,boolean outstanding) {}
  public record AdjustmentState(UUID documentId,LocalDate expectedDateOverride,boolean excluded,String exclusionReason,
      Instant dateConflictSince,LocalDate previousXeroDate,LocalDate newXeroDate,List<Split> lines) {}
  private final JdbcTemplate jdbc;private final ForecastAssumptionRepository audit;
  public ForecastAdjustmentRepository(JdbcTemplate jdbc,ForecastAssumptionRepository audit){this.jdbc=jdbc;this.audit=audit;}
  public DocumentState document(UUID entity,UUID id){return document(entity,id,false);}
  private DocumentState document(UUID entity,UUID id,boolean lock){
    return jdbc.query("select d.xero_document_id,d.amount_due,d.outstanding from public.forecast_documents d join public.entities e on e.id=d.entity_id "
        +"where d.entity_id=? and d.xero_document_id=? and e.archived_at is null"+(lock?" for update of d for share of e":""),
        (r,n)->new DocumentState(r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBoolean(3)),entity,id).stream().findFirst().orElseThrow(ForecastValidation::missing);
  }
  public AdjustmentState state(UUID entity,UUID id){
    var lines=jdbc.query("select sequence,expected_date,amount from public.forecast_split_lines where entity_id=? and xero_document_id=? order by sequence",
        (r,n)->new Split(r.getInt(1),r.getDate(2).toLocalDate(),r.getBigDecimal(3)),entity,id);
    return jdbc.query("select * from public.forecast_document_adjustments where entity_id=? and xero_document_id=?",(r,n)->new AdjustmentState(id,date(r,"expected_date_override"),r.getBoolean("excluded"),r.getString("exclusion_reason"),
        r.getTimestamp("date_conflict_since")==null?null:r.getTimestamp("date_conflict_since").toInstant(),date(r,"conflict_previous_xero_date"),date(r,"conflict_new_xero_date"),lines),entity,id)
        .stream().findFirst().orElse(new AdjustmentState(id,null,false,null,null,null,null,lines));
  }
  @Transactional public AdjustmentState adjust(UUID entity,UUID id,UUID actor,ForecastAdjustmentService.AdjustmentChange change){
    ForecastAdjustmentService.outstanding(document(entity,id,true));var old=state(entity,id);
    LocalDate override=change.dateSupplied()?change.getExpectedDateOverride():old.expectedDateOverride();
    boolean excluded=change.getExcluded()==null?old.excluded():change.getExcluded();
    String reason=excluded?(change.getExclusionReason()==null?old.exclusionReason():change.getExclusionReason().strip()):null;
    if(excluded&&(reason==null||reason.isBlank()||reason.length()>500))throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"FORECAST_EXCLUSION_REASON_REQUIRED","Enter an exclusion reason of 1–500 characters.");
    jdbc.update("insert into public.forecast_document_adjustments(entity_id,xero_document_id,expected_date_override,excluded,exclusion_reason,updated_by) values(?,?,?,?,?,?) "
        +"on conflict(entity_id,xero_document_id) do update set expected_date_override=excluded.expected_date_override,excluded=excluded.excluded,exclusion_reason=excluded.exclusion_reason,updated_by=excluded.updated_by,updated_at=now()",
        entity,id,override,excluded,reason,actor);
    audit.audit(entity,actor,"forecast_document_adjustments",id.toString(),"UPDATE",values(old),values(state(entity,id)));return state(entity,id);
  }
  @Transactional public AdjustmentState splits(UUID entity,UUID id,UUID actor,List<Split> lines){
    var doc=document(entity,id,true);ForecastAdjustmentService.outstanding(doc);ForecastAdjustmentService.validateSplits(lines,doc.amountDue());var old=state(entity,id);
    // An open date conflict is cleared only by an explicit KEEP_SCHEDULE or DISCARD_SCHEDULE resolution (AC-6).
    replace(entity,id,lines);
    var after=state(entity,id);audit.audit(entity,actor,"forecast_split_lines",id.toString(),"UPDATE",Map.of("lines",old.lines()),Map.of("lines",after.lines()));
    audit.audit(entity,actor,"forecast_document_adjustments",id.toString(),"UPDATE",values(old),values(after));return after;
  }
  @Transactional public AdjustmentState resolve(UUID entity,UUID id,UUID actor,String resolution){
    ForecastAdjustmentService.outstanding(document(entity,id,true));var old=state(entity,id);
    if(old.dateConflictSince()==null)throw new ApiFailure(HttpStatus.CONFLICT,"FORECAST_CONFLICT_UNAVAILABLE","No open conflict remains. Refresh this document.");
    if(resolution.equals("DISCARD_SCHEDULE"))replace(entity,id,List.of());
    clearConflict(entity,id,actor);var after=state(entity,id);
    audit.audit(entity,actor,"forecast_document_adjustments",id.toString(),"UPDATE",values(old),values(after));
    if(resolution.equals("DISCARD_SCHEDULE"))audit.audit(entity,actor,"forecast_split_lines",id.toString(),"UPDATE",Map.of("lines",old.lines()),Map.of("lines",after.lines()));
    return after;
  }
  private void replace(UUID entity,UUID id,List<Split> lines){
    jdbc.update("delete from public.forecast_split_lines where entity_id=? and xero_document_id=?",entity,id);
    int sequence=0;for(var line:lines)jdbc.update("insert into public.forecast_split_lines(id,entity_id,xero_document_id,sequence,expected_date,amount) values(?,?,?,?,?,?)",
        UUID.randomUUID(),entity,id,++sequence,line.expectedDate(),line.amount());
  }
  private void clearConflict(UUID entity,UUID id,UUID actor){
    jdbc.update("insert into public.forecast_document_adjustments(entity_id,xero_document_id,updated_by) values(?,?,?) on conflict(entity_id,xero_document_id) "
        +"do update set date_conflict_since=null,conflict_previous_xero_date=null,conflict_new_xero_date=null,updated_by=excluded.updated_by,updated_at=now()",entity,id,actor);
  }
  private static Map<String,Object> values(AdjustmentState s){
    var m=new LinkedHashMap<String,Object>();m.put("expected_date_override",s.expectedDateOverride());m.put("excluded",s.excluded());m.put("exclusion_reason",s.exclusionReason());
    m.put("date_conflict_since",s.dateConflictSince());m.put("conflict_previous_xero_date",s.previousXeroDate());m.put("conflict_new_xero_date",s.newXeroDate());return m;
  }
  private static LocalDate date(ResultSet r,String key)throws SQLException{var d=r.getDate(key);return d==null?null:d.toLocalDate();}
}
