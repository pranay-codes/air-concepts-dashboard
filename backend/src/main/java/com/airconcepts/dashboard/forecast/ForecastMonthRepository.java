package com.airconcepts.dashboard.forecast;

import com.airconcepts.dashboard.forecast.calc.ForecastPlacement;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution;
import com.airconcepts.dashboard.forecast.calc.DateRule;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Frozen monthly forecast lines (FR-UC04-23/24). Lines are insert-only; this repository has no update or delete of lines. */
@Repository
public class ForecastMonthRepository {
  public record GeneratedMonth(YearMonth month,Instant generatedAt,int generationCount) {}
  private final JdbcTemplate jdbc;private final ObjectMapper mapper;
  public ForecastMonthRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

  @Transactional(propagation=Propagation.MANDATORY)
  public void lockEntity(UUID entity) {
    if(jdbc.queryForList("select id from public.entities where id=? and archived_at is null for update",entity).isEmpty()) throw ForecastValidation.missing();
  }

  public Map<YearMonth,GeneratedMonth> generated(UUID entity) {
    var rows=jdbc.query("select forecast_month,last_generated_at,generation_count from public.forecast_generated_months where entity_id=? order by forecast_month",
        (r,n)->new GeneratedMonth(YearMonth.from(r.getDate(1).toLocalDate()),r.getTimestamp(2).toInstant(),r.getInt(3)),entity);
    Map<YearMonth,GeneratedMonth> months=new LinkedHashMap<>();for(var row:rows) months.put(row.month(),row);return months;
  }

  /** Receipts minus payments for every generated month of the entity. */
  public Map<YearMonth,BigDecimal> netByMonth(UUID entity) {
    Map<YearMonth,BigDecimal> net=new HashMap<>();
    jdbc.query("select forecast_month,direction,sum(amount) from public.forecast_snapshot_lines where entity_id=? group by forecast_month,direction",r->{
      var month=YearMonth.from(r.getDate(1).toLocalDate());var value=r.getBigDecimal(3);
      net.merge(month,"IN".equals(r.getString(2))?value:value.negate(),BigDecimal::add);
    },entity);
    return net;
  }

  public Map<YearMonth,List<Contribution>> lines(UUID entity,YearMonth from,YearMonth to) {
    Map<YearMonth,List<Contribution>> lines=new HashMap<>();
    jdbc.query("select * from public.forecast_snapshot_lines where entity_id=? and forecast_month between ? and ? order by forecast_month,id",r->{
      var line=line(r);lines.computeIfAbsent(line.bucket(),m->new ArrayList<>()).add(line);
    },entity,from.atDay(1),to.atDay(1));
    return lines;
  }

  /** Every placed line of the entity, used to decide which calculated lines are still unplaced. */
  public ForecastPlacement.Placed placement(UUID entity) {
    var documents=new HashSet<UUID>();var keys=new HashSet<String>();
    jdbc.query("select source,source_id,forecast_month from public.forecast_snapshot_lines where entity_id=?",r->{
      var source=r.getString(1);var id=r.getObject(2,UUID.class);var month=YearMonth.from(r.getDate(3).toLocalDate());
      if("DOCUMENT".equals(source)) documents.add(id);else keys.add(ForecastPlacement.key(source,id,month));
    },entity);
    return new ForecastPlacement.Placed(documents,keys);
  }

  @Transactional(propagation=Propagation.MANDATORY)
  public void createGeneration(UUID entity,YearMonth month,UUID actor) {
    jdbc.update("insert into public.forecast_generated_months(entity_id,forecast_month,generated_by) values(?,?,?)",entity,month.atDay(1),actor);
  }

  @Transactional(propagation=Propagation.MANDATORY)
  public void recordRegeneration(UUID entity,YearMonth month,UUID actor) {
    int count=jdbc.update("update public.forecast_generated_months set generation_count=generation_count+1,last_generated_at=now(),generated_by=? "
        +"where entity_id=? and forecast_month=?",actor,entity,month.atDay(1));
    if(count!=1) throw ForecastValidation.missing();
  }

  /** Inserts the lines that are not already placed. Returns how many were inserted (a conflicting line is skipped, never replaced). */
  @Transactional(propagation=Propagation.MANDATORY)
  public int insertLines(UUID entity,YearMonth month,int generation,UUID actor,List<Contribution> rows) {
    int inserted=0;
    for(var line:rows) {
      inserted+=jdbc.update("insert into public.forecast_snapshot_lines(entity_id,forecast_month,generation_number,source,source_id,split_sequence,split_count,direction,"
          +"category,document_number,label,issue_date,due_date,cash_date,rule,overdue,amount,added_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict do nothing",
          entity,month.atDay(1),generation,line.source(),line.sourceId(),line.splitSequence(),line.splitCount(),line.direction(),line.category(),line.number(),
          line.label(),line.issueDate(),line.dueDate(),line.date(),line.rule().name(),line.overdue(),line.amount(),actor);
    }
    return inserted;
  }

  public void audit(UUID entity,UUID actor,YearMonth month,String action,Map<String,Object> before,Map<String,Object> after) {
    jdbc.update("insert into public.forecast_audit(entity_id,actor_id,record_table,record_key,action,before,after) values(?,?,?,?,?,?::jsonb,?::jsonb)",
        entity,actor,"forecast_generated_months",month.toString(),action,mapper.writeValueAsString(before),mapper.writeValueAsString(after));
  }

  private static Contribution line(ResultSet r) throws SQLException {
    return new Contribution(r.getObject("source_id",UUID.class),r.getString("source"),r.getString("direction"),r.getString("category"),
        r.getString("document_number"),r.getString("label"),r.getDate("cash_date").toLocalDate(),YearMonth.from(r.getDate("forecast_month").toLocalDate()),
        r.getBigDecimal("amount"),DateRule.valueOf(r.getString("rule")),r.getBoolean("overdue"),r.getObject("split_sequence",Integer.class),
        r.getObject("split_count",Integer.class),date(r,"issue_date"),date(r,"due_date"));
  }
  private static LocalDate date(ResultSet r,String key) throws SQLException {var d=r.getDate(key);return d==null?null:d.toLocalDate();}
}
