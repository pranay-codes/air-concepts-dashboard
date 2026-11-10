package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.SyncInfo;
import com.airconcepts.dashboard.security.CurrentUser;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
@Service
public class ForecastService {
  private final ForecastDatasetLoader loader;private final ForecastAccess access;private final Clock clock;
  private final ForecastCalculator calculator=new ForecastCalculator();
  public ForecastService(ForecastDatasetLoader loader,ForecastAccess access,Clock clock){this.loader=loader;this.access=access;this.clock=clock;}
  public record LastRun(String status,Instant finishedAt,String errorCode){}
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"entityId","entityName","startMonth","months","totals","exceptions","beyondWindow","exclusions","syncStale"})
  public record ForecastView(UUID entityId,String entityName,YearMonth startMonth,List<MonthRow> months,Totals totals,List<ForecastException> exceptions,
      BeyondWindow beyondWindow,List<Exclusion> exclusions,Instant lastSuccessfulSyncAt,LastRun lastRun,boolean syncStale,String openingBalanceSource){}
  public record MonthItems(YearMonth month,List<Contribution> items){}
  public record EntitySummary(UUID entityId,String entityName,List<MonthRow> months,List<String> exceptions,boolean complete){}
  public record CombinedView(YearMonth startMonth,List<MonthRow> months,List<EntitySummary> entities,boolean complete){}
  public ForecastView view(CurrentUser user,UUID entity,String startMonth){
    var authorised=access.requireRead(user,entity);var month=ForecastMonths.window(startMonth,clock);var data=loader.load(authorised,month);var result=calculator.calculate(data);var sync=data.sync();
    return new ForecastView(entity,authorised.displayName(),month,result.months(),result.totals(),result.exceptions(),result.beyondWindow(),result.exclusions(),
        sync==null?null:sync.lastSuccessfulSyncAt(),sync==null?null:new LastRun(sync.lastRunStatus(),sync.lastRunFinishedAt(),sync.lastErrorCode()),sync==null||sync.stale(),data.openingBalanceSource());
  }
  public MonthItems items(CurrentUser user,UUID entity,String monthValue,String direction,String startValue){
    var authorised=access.requireRead(user,entity);var start=ForecastMonths.window(startValue,clock);var month=ForecastMonths.parse(monthValue);
    if(month.isBefore(start)||month.isAfter(start.plusMonths(11)))throw ForecastValidation.invalid("month","Choose a month inside this forecast period.");
    if(direction!=null&&!Set.of("IN","OUT").contains(direction))throw ForecastValidation.invalid("direction","Choose receipts or payments.");
    var data=loader.load(authorised,start);var result=calculator.calculate(data);
    return new MonthItems(month,result.contributions().stream().filter(c->c.bucket().equals(month)&&(direction==null||c.direction().equals(direction))).toList());
  }
  public CombinedView combined(CurrentUser user,List<UUID> ids,String start){
    // Recheck role before even validating the requested entity set.
    user.requireRole(com.airconcepts.dashboard.security.Role.DIRECTOR,com.airconcepts.dashboard.security.Role.FINANCIAL_ADMIN);
    if(ids==null||ids.isEmpty()||new HashSet<>(ids).size()!=ids.size())throw ForecastValidation.invalid("entityIds","Choose distinct authorised entities.");
    List<ForecastView> views=ids.stream().map(id->view(user,id,start)).toList();List<MonthRow> months=new ArrayList<>();
    for(int i=0;i<12;i++){
      final int index=i;var rows=views.stream().map(v->v.months().get(index)).toList();
      months.add(new MonthRow(rows.getFirst().month(),sum(rows,MonthRow::opening),sum(rows,MonthRow::receipts),sum(rows,MonthRow::payments),sum(rows,MonthRow::net),sum(rows,MonthRow::closing),
          rows.stream().mapToInt(MonthRow::receiptCount).sum(),rows.stream().mapToInt(MonthRow::paymentCount).sum(),rows.stream().anyMatch(MonthRow::containsOverdue)));
    }
    var summaries=views.stream().map(v->new EntitySummary(v.entityId(),v.entityName(),v.months(),v.exceptions().stream().map(ForecastException::kind).distinct().toList(),
        v.lastSuccessfulSyncAt()!=null&&!v.syncStale()&&v.lastRun()!=null&&!"FAILED".equals(v.lastRun().status())&&v.months().getFirst().opening()!=null)).toList();
    return new CombinedView(views.getFirst().startMonth(),List.copyOf(months),summaries,summaries.stream().allMatch(EntitySummary::complete));
  }
  private static BigDecimal sum(List<MonthRow> rows,Function<MonthRow,BigDecimal> field){
    if(rows.stream().anyMatch(r->field.apply(r)==null))return null;
    return rows.stream().map(field).reduce(new BigDecimal("0.00"),BigDecimal::add);
  }
}
