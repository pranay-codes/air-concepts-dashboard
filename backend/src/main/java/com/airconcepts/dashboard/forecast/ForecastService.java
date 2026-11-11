package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.*;
import com.airconcepts.dashboard.security.CurrentUser;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
@Service
public class ForecastService {
  private final ForecastAccess access;private final ForecastResolver resolver;private final ForecastCalendar calendar;
  public ForecastService(ForecastAccess access,ForecastResolver resolver,ForecastCalendar calendar){this.access=access;this.resolver=resolver;this.calendar=calendar;}
  public record LastRun(String status,Instant finishedAt,String errorCode){}
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"entityId","entityName","financialYearStart","financialYearLabel","months","totals","exceptions","beyondWindow","exclusions","syncStale"})
  public record ForecastView(UUID entityId,String entityName,YearMonth financialYearStart,String financialYearLabel,List<MonthRow> months,Totals totals,
      List<ForecastException> exceptions,BeyondWindow beyondWindow,List<Exclusion> exclusions,Instant lastSuccessfulSyncAt,LastRun lastRun,boolean syncStale,
      String openingBalanceSource){}
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"month","state","generatable","items"})
  public record MonthItems(YearMonth month,String state,boolean generatable,List<Contribution> items){}
  public record EntitySummary(UUID entityId,String entityName,List<MonthRow> months,List<String> exceptions,boolean complete){}
  public record CombinedView(YearMonth financialYearStart,String financialYearLabel,List<MonthRow> months,List<EntitySummary> entities,boolean complete){}

  public ForecastView view(CurrentUser user,UUID entity,String financialYear){
    return view(access.requireRead(user,entity),calendar.financialYearStart(financialYear));
  }
  private ForecastView view(com.airconcepts.dashboard.entities.Entity authorised,YearMonth start){
    var resolved=resolver.resolve(authorised,start);var result=resolved.result();var sync=resolved.data().sync();
    return new ForecastView(authorised.id(),authorised.displayName(),start,calendar.label(start),result.months(),result.totals(),result.exceptions(),
        result.beyondWindow(),result.exclusions(),sync==null?null:sync.lastSuccessfulSyncAt(),
        sync==null?null:new LastRun(sync.lastRunStatus(),sync.lastRunFinishedAt(),sync.lastErrorCode()),sync==null||sync.stale(),resolved.openingSource());
  }
  public MonthItems items(CurrentUser user,UUID entity,String monthValue,String direction){
    var authorised=access.requireRead(user,entity);var month=ForecastMonths.parse(monthValue);
    if(direction!=null&&!Set.of("IN","OUT").contains(direction))throw ForecastValidation.invalid("direction","Choose receipts or payments.");
    var resolved=resolver.resolve(authorised,calendar.financialYearContaining(month));
    var row=resolved.result().months().stream().filter(m->m.month().equals(month)).findFirst().orElseThrow(ForecastValidation::missing);
    var items=resolved.result().contributions().stream().filter(c->c.bucket().equals(month)&&(direction==null||c.direction().equals(direction))).toList();
    return new MonthItems(month,row.state(),row.generatable(),items);
  }
  public CombinedView combined(CurrentUser user,List<UUID> ids,String financialYear){
    // Recheck role before even validating the requested entity set.
    user.requireRole(com.airconcepts.dashboard.security.Role.DIRECTOR,com.airconcepts.dashboard.security.Role.FINANCIAL_ADMIN);
    if(ids==null||ids.isEmpty()||new HashSet<>(ids).size()!=ids.size())throw ForecastValidation.invalid("entityIds","Choose distinct authorised entities.");
    var start=calendar.financialYearStart(financialYear);
    List<ForecastView> views=ids.stream().map(id->view(access.requireRead(user,id),start)).toList();List<MonthRow> months=new ArrayList<>();
    for(int i=0;i<12;i++){
      final int index=i;var rows=views.stream().map(v->v.months().get(index)).toList();
      months.add(new MonthRow(rows.getFirst().month(),sum(rows,MonthRow::opening),sum(rows,MonthRow::receipts),sum(rows,MonthRow::payments),sum(rows,MonthRow::net),sum(rows,MonthRow::closing),
          rows.stream().mapToInt(MonthRow::receiptCount).sum(),rows.stream().mapToInt(MonthRow::paymentCount).sum(),rows.stream().anyMatch(MonthRow::containsOverdue),
          combinedState(rows),
          false,null,rows.stream().mapToInt(MonthRow::pendingNewItems).sum()));
    }
    var summaries=views.stream().map(v->new EntitySummary(v.entityId(),v.entityName(),v.months(),v.exceptions().stream().map(ForecastException::kind).distinct().toList(),
        v.lastSuccessfulSyncAt()!=null&&!v.syncStale()&&v.lastRun()!=null&&!"FAILED".equals(v.lastRun().status())&&v.months().getFirst().opening()!=null)).toList();
    return new CombinedView(start,calendar.label(start),List.copyOf(months),summaries,summaries.stream().allMatch(EntitySummary::complete));
  }
  /** Generated only when every entity has generated the month; not generated when any entity has not. */
  private static String combinedState(List<MonthRow> rows){
    if(rows.stream().allMatch(r->ForecastResolver.STATE_GENERATED.equals(r.state()))) return ForecastResolver.STATE_GENERATED;
    if(rows.stream().anyMatch(r->ForecastResolver.STATE_NOT_GENERATED.equals(r.state()))) return ForecastResolver.STATE_NOT_GENERATED;
    return ForecastResolver.STATE_PREVIEW;
  }
  private static BigDecimal sum(List<MonthRow> rows,Function<MonthRow,BigDecimal> field){
    if(rows.stream().anyMatch(r->field.apply(r)==null))return null;
    return rows.stream().map(field).reduce(new BigDecimal("0.00"),BigDecimal::add);
  }
}
