package com.airconcepts.dashboard.forecast;

import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ForecastAssumptionRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  public ForecastAssumptionRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
  public record OpeningBalance(@JsonFormat(shape=JsonFormat.Shape.STRING) @io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal openingBalance,String source) {}
  private static YearMonth month(ResultSet r,String key) throws SQLException {var d=r.getDate(key);return d==null?null:YearMonth.from(d.toLocalDate());}
  private static final RowMapper<ManualItem> MANUAL=(r,n)->new ManualItem(r.getObject("id",UUID.class),r.getString("direction"),r.getString("category"),r.getString("description"),
      r.getBigDecimal("amount"),r.getString("recurrence"),r.getObject("day_of_month",Integer.class),month(r,"start_month"),month(r,"end_month"),r.getBoolean("active"));
  private static final RowMapper<StaffingLine> STAFF=(r,n)->new StaffingLine(r.getObject("id",UUID.class),r.getString("label"),r.getBigDecimal("monthly_cost"),
      r.getObject("pay_day_of_month",Integer.class),month(r,"start_month"),month(r,"end_month"),r.getBoolean("active"));
  public OpeningBalance opening(UUID entity,LocalDate start) {
    return jdbc.query("select opening_balance,opening_balance_source from public.forecast_periods where entity_id=? and start_month=?",
        (r,n)->new OpeningBalance(r.getBigDecimal(1),r.getString(2)),entity,start).stream().findFirst().orElse(new OpeningBalance(null,null));
  }
  public List<ManualItem> manualItems(UUID entity,boolean inactive) {return jdbc.query("select * from public.forecast_manual_items where entity_id=? and (? or active) order by description,id",MANUAL,entity,inactive);}
  public Optional<ManualItem> manual(UUID entity,UUID id) {return jdbc.query("select * from public.forecast_manual_items where entity_id=? and id=?",MANUAL,entity,id).stream().findFirst();}
  @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
  public Optional<ManualItem> manualForUpdate(UUID entity,UUID id){lockEntity(entity);return jdbc.query("select * from public.forecast_manual_items where entity_id=? and id=? for update",MANUAL,entity,id).stream().findFirst();}
  public List<StaffingLine> staffing(UUID entity,boolean inactive) {return jdbc.query("select * from public.forecast_staffing where entity_id=? and (? or active) order by label,id",STAFF,entity,inactive);}
  public Optional<StaffingLine> staff(UUID entity,UUID id) {return jdbc.query("select * from public.forecast_staffing where entity_id=? and id=?",STAFF,entity,id).stream().findFirst();}
  @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
  public Optional<StaffingLine> staffForUpdate(UUID entity,UUID id){lockEntity(entity);return jdbc.query("select * from public.forecast_staffing where entity_id=? and id=? for update",STAFF,entity,id).stream().findFirst();}
  public record AuditRow(long id,UUID actorId,Instant at,String recordTable,String recordKey,String action,Map<String,Object> before,Map<String,Object> after) {}
  public record AuditPage(List<AuditRow> items,int page,int pageSize,long itemCount) {}
  public AuditPage auditRows(UUID entity,int page,int size) {
    var rows=jdbc.query("select * from public.forecast_audit where entity_id=? order by at desc,id desc limit ? offset ?",(r,n)->
        new AuditRow(r.getLong("id"),r.getObject("actor_id",UUID.class),r.getTimestamp("at").toInstant(),r.getString("record_table"),r.getString("record_key"),r.getString("action"),
            mapper.readValue(r.getString("before"),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){}),
            mapper.readValue(r.getString("after"),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){})),entity,size,(long)(page-1)*size);
    Long count=jdbc.queryForObject("select count(*) from public.forecast_audit where entity_id=?",Long.class,entity);
    return new AuditPage(rows,page,size,count==null?0:count);
  }
  @Transactional public OpeningBalance saveOpening(UUID entity,UUID actor,LocalDate start,BigDecimal value,String source) {
    lockEntity(entity);var old=opening(entity,start);
    jdbc.update("insert into public.forecast_periods(entity_id,start_month,opening_balance,opening_balance_source,updated_by) values(?,?,?,?,?) "
        +"on conflict(entity_id,start_month) do update set opening_balance=excluded.opening_balance,opening_balance_source=excluded.opening_balance_source,updated_by=excluded.updated_by,updated_at=now()",entity,start,value,source,actor);
    var before=new LinkedHashMap<String,Object>();before.put("opening_balance",old.openingBalance());before.put("opening_balance_source",old.source());
    var after=new LinkedHashMap<String,Object>();after.put("opening_balance",value);after.put("opening_balance_source",source);
    audit(entity,actor,"forecast_periods",start.toString(),old.openingBalance()==null?"INSERT":"UPDATE",before,after);
    return new OpeningBalance(value,source);
  }
  @Transactional public ManualItem saveManual(UUID entity,UUID actor,ManualItem item) {
    lockEntity(entity);var old=manual(entity,item.id());
    var values=new LinkedHashMap<String,Object>();values.put("direction",item.direction());values.put("category",item.category());values.put("description",item.description());
    values.put("amount",item.amount());values.put("recurrence",item.recurrence());values.put("day_of_month",item.dayOfMonth());
    values.put("start_month",item.startMonth().atDay(1));values.put("end_month",item.endMonth()==null?null:item.endMonth().atDay(1));values.put("active",item.active());
    save("forecast_manual_items",entity,item.id(),actor,values,old.isEmpty(),!item.active());return item;
  }
  @Transactional public StaffingLine saveStaff(UUID entity,UUID actor,StaffingLine item) {
    lockEntity(entity);var old=staff(entity,item.id());var values=new LinkedHashMap<String,Object>();
    values.put("label",item.label());values.put("monthly_cost",item.monthlyCost());values.put("pay_day_of_month",item.payDayOfMonth());
    values.put("start_month",item.startMonth().atDay(1));values.put("end_month",item.endMonth()==null?null:item.endMonth().atDay(1));values.put("active",item.active());
    save("forecast_staffing",entity,item.id(),actor,values,old.isEmpty(),!item.active());return item;
  }
  private void save(String table,UUID entity,UUID id,UUID actor,LinkedHashMap<String,Object> values,boolean insert,boolean inactive) {
    // Table and column identifiers come exclusively from the two fixed private callers above.
    Map<String,Object> before=jdbc.queryForList("select * from public."+table+" where entity_id=? and id=?",entity,id).stream().findFirst().orElse(Map.of());
    var args=new ArrayList<Object>(values.values());int count;
    if(insert) {
      args.add(id);args.add(entity);args.add(actor);args.add(actor);
      count=jdbc.update("insert into public."+table+" ("+String.join(",",values.keySet())+",id,entity_id,created_by,updated_by) values ("
          +String.join(",",Collections.nCopies(args.size(),"?"))+")",args.toArray());
    } else {
      args.add(actor);args.add(entity);args.add(id);
      count=jdbc.update("update public."+table+" set "+String.join(",",values.keySet().stream().map(k->k+"=?").toList())+",updated_by=?,updated_at=now() where entity_id=? and id=?",args.toArray());
    }
    if(count!=1) throw ForecastValidation.missing();
    audit(entity,actor,table,id.toString(),insert?"INSERT":inactive?"SOFT_DELETE":"UPDATE",before,values);
  }
  private void lockEntity(UUID entity) {
    if(jdbc.queryForList("select id from public.entities where id=? and archived_at is null for update",entity).isEmpty())throw ForecastValidation.missing();
  }
  public void audit(UUID entity,UUID actor,String table,String key,String action,Map<String,?> before,Map<String,?> after) {
    var old=new LinkedHashMap<String,Object>();var changed=new LinkedHashMap<String,Object>();
    for(var entry:after.entrySet()) {
      Object prior=normal(before.get(entry.getKey())),next=normal(entry.getValue());
      if(!Objects.equals(prior,next)){old.put(entry.getKey(),prior);changed.put(entry.getKey(),next);}
    }
    if(changed.isEmpty()) return;
    jdbc.update("insert into public.forecast_audit(entity_id,actor_id,record_table,record_key,action,before,after) values(?,?,?,?,?,?::jsonb,?::jsonb)",
        entity,actor,table,key,action,mapper.writeValueAsString(old),mapper.writeValueAsString(changed));
  }
  private static Object normal(Object value) {
    if(value instanceof BigDecimal number)return number.setScale(2).toPlainString();
    if(value instanceof java.sql.Date date)return date.toLocalDate().toString();
    if(value instanceof LocalDate date)return date.toString();return value;
  }
}
