package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.ForecastResult;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
@Repository
public class ForecastExportRepository {
  private final JdbcTemplate jdbc;private final ObjectMapper mapper;
  public ForecastExportRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
  public String entityCode(UUID entity){
    var code=jdbc.query("select code from public.entities where id=? and archived_at is null",(r,n)->r.getString(1),entity).stream().findFirst().orElseThrow(ForecastValidation::missing);
    if(!code.matches("[a-z0-9][a-z0-9_-]{0,79}"))throw ForecastValidation.invalid("entity","Entity code is invalid for export.");return code;
  }
  public void record(UUID entity,UUID actor,YearMonth start,ForecastResult result,boolean reconciled,List<String> warnings,Instant generated){
    int inserted=jdbc.update("insert into public.forecast_exports(id,entity_id,start_month,generated_by,generated_at,template_version,receipts_total,payments_total,closing_total,reconciled,warnings) "
        +"select ?,?,?,?,?,'v1',?,?,?,?,?::jsonb where exists(select 1 from public.entities where id=? and archived_at is null) "
        +"and exists(select 1 from public.staff_users where id=? and active and role in ('DIRECTOR','FINANCIAL_ADMIN'))",UUID.randomUUID(),entity,start.atDay(1),actor,Timestamp.from(generated),
        result.totals().receipts(),result.totals().payments(),result.totals().closing(),reconciled,mapper.writeValueAsString(warnings),entity,actor);
    if(inserted!=1)throw ForecastValidation.missing();
  }
}
