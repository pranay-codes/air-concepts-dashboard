package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.web.ApiFailure;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
@Component
public class ForecastDatasetLoader {
  public static final String CASH_CURRENCY="NZD";
  public static final Duration STALE_AFTER=Duration.ofHours(36);
  private final JdbcTemplate jdbc;private final ForecastAssumptionRepository assumptions;private final Clock clock;
  public ForecastDatasetLoader(JdbcTemplate jdbc,ForecastAssumptionRepository assumptions,Clock clock){this.jdbc=jdbc;this.assumptions=assumptions;this.clock=clock;}
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public ForecastDataset load(Entity entity,YearMonth start){
    var splits=new HashMap<UUID,List<Split>>();
    jdbc.query("select s.* from public.forecast_split_lines s join public.forecast_documents d on d.entity_id=s.entity_id and d.xero_document_id=s.xero_document_id "
        +"where s.entity_id=? and d.outstanding order by s.xero_document_id,s.sequence",r->{
          splits.computeIfAbsent(r.getObject("xero_document_id",UUID.class),id->new ArrayList<>()).add(new Split(r.getInt("sequence"),r.getDate("expected_date").toLocalDate(),r.getBigDecimal("amount")));
        },entity.id());
    var documents=jdbc.query("select d.*,a.expected_date_override,a.excluded,a.exclusion_reason,a.date_conflict_since from public.forecast_documents d "
        +"left join public.forecast_document_adjustments a on a.entity_id=d.entity_id and a.xero_document_id=d.xero_document_id where d.entity_id=? and d.outstanding order by d.xero_document_id",(r,n)->{
          if(!CASH_CURRENCY.equals(r.getString("currency")))throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"FORECAST_CURRENCY_UNSUPPORTED","Forecast requires NZD cash amounts; a currency conversion rule must be approved before including foreign-currency documents.");
          UUID id=r.getObject("xero_document_id",UUID.class);
          return new Document(id,r.getString("document_type"),r.getString("number"),r.getString("contact_name"),date(r,"issue_date"),date(r,"due_date"),date(r,"xero_expected_date"),
              date(r,"expected_date_override"),r.getBigDecimal("amount_due"),r.getBoolean("excluded"),r.getString("exclusion_reason"),splits.getOrDefault(id,List.of()),r.getTimestamp("date_conflict_since")!=null);
        },entity.id());
    var success=jdbc.query("select started_at from public.forecast_sync_runs where entity_id=? and status='SUCCEEDED' order by started_at desc,id desc limit 1",(r,n)->r.getTimestamp(1).toInstant(),entity.id());
    Instant last=success.isEmpty()?null:success.getFirst();
    var runs=jdbc.query("select status,finished_at,error_code from public.forecast_sync_runs where entity_id=? order by started_at desc,id desc limit 1",(r,n)->
        new SyncInfo(last,r.getString(1),last==null||last.isBefore(clock.instant().minus(STALE_AFTER)),r.getTimestamp(2)==null?null:r.getTimestamp(2).toInstant(),r.getString(3)),entity.id());
    var sync=runs.isEmpty()?new SyncInfo(null,null,true):runs.getFirst();var opening=assumptions.opening(entity.id(),start.atDay(1));
    return new ForecastDataset(entity.id(),entity.displayName(),start,LocalDate.now(clock.withZone(ForecastMonths.ZONE)),opening.openingBalance(),opening.source(),documents,
        assumptions.manualItems(entity.id(),true),assumptions.staffing(entity.id(),true),sync);
  }
  private static LocalDate date(ResultSet r,String key)throws SQLException{var d=r.getDate(key);return d==null?null:d.toLocalDate();}
}
