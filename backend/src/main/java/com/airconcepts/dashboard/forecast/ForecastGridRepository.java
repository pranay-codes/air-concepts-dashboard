package com.airconcepts.dashboard.forecast;

import com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ForecastGridRepository {
  public record Line(YearMonth month,UUID documentId,Integer splitSequence,String direction,String contact,BigDecimal amount,String number) {
    public Line(YearMonth month,UUID documentId,Integer splitSequence,String direction,String contact,BigDecimal amount){this(month,documentId,splitSequence,direction,contact,amount,null);}
  }
  public record ManualLine(YearMonth month,UUID itemId,String direction,String description,BigDecimal amount) {}
  private final JdbcTemplate jdbc;
  public ForecastGridRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public List<ForecastGridOverrides.Override> overrides(UUID entity,YearMonth from,YearMonth to) {
    return jdbc.query("select forecast_month,direction,scope,scope_key,amount,display_company,display_number from public.forecast_grid_amount_overrides where entity_id=? and forecast_month between ? and ? and active",
        (r,n)->new ForecastGridOverrides.Override(YearMonth.from(r.getDate(1).toLocalDate()),r.getString(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getString(6),r.getString(7)),entity,from.atDay(1),to.atDay(1));
  }
  public void saveOverride(UUID entity,UUID actor,ForecastGridOverrides.Override change) {
    jdbc.update("insert into public.forecast_grid_amount_overrides(entity_id,forecast_month,direction,scope,scope_key,amount,display_company,display_number,updated_by) values(?,?,?,?,?,?,?,?,?) on conflict(entity_id,forecast_month,direction,scope,scope_key) do update set amount=excluded.amount,display_company=excluded.display_company,display_number=excluded.display_number,active=true,updated_by=excluded.updated_by,updated_at=now()",
        entity,change.month().atDay(1),change.direction(),change.scope(),change.key(),change.amount(),change.company(),change.number(),actor);
  }
  public void deactivateOverride(UUID entity,UUID actor,ForecastGridOverrides.Override change) {
    jdbc.update("update public.forecast_grid_amount_overrides set active=false,updated_by=?,updated_at=now() where entity_id=? and forecast_month=? and direction=? and scope=? and scope_key=?",actor,entity,change.month().atDay(1),change.direction(),change.scope(),change.key());
  }
  public Line documentTemplate(UUID entity,UUID document,String direction,YearMonth month) {
    var result=jdbc.query("select d.xero_document_id,coalesce((select l.contact_name from public.forecast_grid_lines l where l.entity_id=d.entity_id and l.document_id=d.xero_document_id order by (l.forecast_month=?) desc,l.forecast_month desc,l.revision desc limit 1),d.contact_name,'Unknown contact'),d.number from public.forecast_documents d where d.entity_id=? and d.xero_document_id=? and d.document_type=?",
        (r,n)->new Line(month,r.getObject(1,UUID.class),null,direction,r.getString(2),BigDecimal.ZERO,r.getString(3)),month.atDay(1),entity,document,"IN".equals(direction)?"ACCREC":"ACCPAY");
    return result.isEmpty()?null:result.get(0);
  }
  public boolean companyExists(UUID entity,String company,String direction) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.forecast_documents where entity_id=? and coalesce(contact_name,'Unknown contact')=? and document_type=?)",Boolean.class,entity,company,"IN".equals(direction)?"ACCREC":"ACCPAY"));
  }
  public boolean documentEligible(UUID entity,UUID document) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.forecast_documents d left join public.forecast_document_adjustments a on a.entity_id=d.entity_id and a.xero_document_id=d.xero_document_id where d.entity_id=? and d.xero_document_id=? and d.outstanding and not coalesce(a.excluded,false))",Boolean.class,entity,document));
  }
  public Map<YearMonth,Instant> months(UUID entity,YearMonth from,YearMonth to){
    Map<YearMonth,Instant> result=new LinkedHashMap<>();
    jdbc.query("select distinct on (forecast_month) forecast_month,created_at from public.forecast_grid_revisions where entity_id=? and forecast_month between ? and ? order by forecast_month,revision desc",r->{result.put(YearMonth.from(r.getDate(1).toLocalDate()),r.getTimestamp(2).toInstant());},entity,from.atDay(1),to.atDay(1));
    return result;
  }
  public List<Line> lines(UUID entity,YearMonth from,YearMonth to){
    return jdbc.query("select l.forecast_month,l.document_id,l.split_sequence,l.direction,l.contact_name,l.amount,d.number from public.forecast_grid_lines l left join public.forecast_documents d on d.entity_id=l.entity_id and d.xero_document_id=l.document_id join (select forecast_month,max(revision) revision from public.forecast_grid_revisions where entity_id=? and forecast_month between ? and ? group by forecast_month) r on r.forecast_month=l.forecast_month and r.revision=l.revision where l.entity_id=? order by l.forecast_month,l.id",(r,n)->new Line(YearMonth.from(r.getDate(1).toLocalDate()),r.getObject(2,UUID.class),r.getObject(3,Integer.class),r.getString(4),r.getString(5),r.getBigDecimal(6),r.getString(7)),entity,from.atDay(1),to.atDay(1),entity);
  }
  public List<ManualLine> manualLines(UUID entity,YearMonth from,YearMonth to) {
    return jdbc.query("select forecast_month,manual_item_id,direction,description,amount from public.forecast_grid_manual_snapshots where entity_id=? and forecast_month between ? and ? and active order by forecast_month,manual_item_id",
        (r,n)->new ManualLine(YearMonth.from(r.getDate(1).toLocalDate()),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getBigDecimal(5)),entity,from.atDay(1),to.atDay(1));
  }
  public void append(UUID entity,YearMonth month,UUID actor,List<Contribution> lines){
    int revision=jdbc.queryForObject("select coalesce(max(revision),0)+1 from public.forecast_grid_revisions where entity_id=? and forecast_month=?",Integer.class,entity,month.atDay(1));
    jdbc.update("insert into public.forecast_grid_revisions(entity_id,forecast_month,revision,created_by) values(?,?,?,?)",entity,month.atDay(1),revision,actor);
    for(var line:lines) jdbc.update("insert into public.forecast_grid_lines(entity_id,forecast_month,revision,document_id,split_sequence,direction,contact_name,amount) values(?,?,?,?,?,?,?,?)",entity,month.atDay(1),revision,line.sourceId(),line.splitSequence(),line.direction(),line.label()==null?"Unknown contact":line.label(),line.amount());
  }
}
