package com.airconcepts.dashboard.forecast;

import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates or regenerates one calendar month (FR-UC04-23/24). Generation only appends lines that are not yet placed;
 * existing lines are never changed, so a regeneration can add new items but cannot overwrite a forecast already made.
 */
@Service
public class ForecastGenerationService {
  public record GenerationResult(YearMonth month,String state,boolean generatable,int generationCount,int addedLines,
      BigDecimal addedReceipts,BigDecimal addedPayments) {}
  private final ForecastAccess access;private final ForecastDatasetLoader loader;private final ForecastAssumptionRepository assumptions;
  private final ForecastMonthRepository months;private final ForecastCalendar calendar;
  private final ForecastCalculator calculator=new ForecastCalculator();

  public ForecastGenerationService(ForecastAccess access,ForecastDatasetLoader loader,ForecastAssumptionRepository assumptions,
      ForecastMonthRepository months,ForecastCalendar calendar) {
    this.access=access;this.loader=loader;this.assumptions=assumptions;this.months=months;this.calendar=calendar;
  }

  @Transactional
  public GenerationResult generate(CurrentUser user,UUID entityId,String monthValue) {
    var entity=access.requireWrite(user,entityId);var month=ForecastMonths.parse(monthValue);
    months.lockEntity(entity.id());
    var current=calendar.currentMonth();
    if(!calendar.inGenerationWindow(month,current))
      throw new ApiFailure(HttpStatus.BAD_REQUEST,"FORECAST_PERIOD_INVALID","Choose the current month or one of the next "+ForecastCalendar.GENERATION_WINDOW_MONTHS+" months.");
    var generated=months.generated(entity.id());
    if(generated.containsKey(month.plusMonths(1)))
      throw new ApiFailure(HttpStatus.CONFLICT,"FORECAST_MONTH_LOCKED","This month is locked because the following month has been generated.");
    for(var earlier=current;earlier.isBefore(month);earlier=earlier.plusMonths(1))
      if(!generated.containsKey(earlier))
        throw new ApiFailure(HttpStatus.CONFLICT,"FORECAST_MONTH_NOT_GENERATABLE","Generate "+earlier+" before "+month+".");

    var anchors=ForecastResolver.anchorValues(assumptions.openingAnchors(entity.id()));
    var opening=ForecastChain.openings(month,month,generated.keySet(),months.netByMonth(entity.id()),anchors).get(month);
    if(opening==null)
      throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"FORECAST_OPENING_REQUIRED","Enter the opening balance for "+month+" before generating it.");

    var base=loader.load(entity,month);
    var calculated=calculator.calculate(ForecastResolver.withOpening(base,opening,null));
    var candidates=ForecastPlacement.unplaced(calculated.contributions(),month,months.placement(entity.id()));
    var actor=UUID.fromString(user.subject());var existing=generated.get(month);
    if(existing==null) months.createGeneration(entity.id(),month,actor);
    int generation=existing==null?1:existing.generationCount()+1;
    int added=candidates.isEmpty()?0:months.insertLines(entity.id(),month,generation,actor,candidates);
    if(added!=candidates.size()) throw new IllegalStateException("Forecast placement conflict for "+month+"; no lines were kept.");
    assumptions.captureManualMonth(entity.id(),month);
    int countAfter=existing==null?1:existing.generationCount();
    if(existing!=null&&added>0) {months.recordRegeneration(entity.id(),month,actor);countAfter=existing.generationCount()+1;}
    BigDecimal receipts=sum(candidates,"IN"),payments=sum(candidates,"OUT");
    if(existing==null||added>0) {
      Map<String,Object> before=new LinkedHashMap<>();before.put("generation_count",existing==null?null:existing.generationCount());
      Map<String,Object> after=new LinkedHashMap<>();after.put("generation_count",countAfter);after.put("added_lines",added);
      after.put("added_receipts",receipts.toPlainString());after.put("added_payments",payments.toPlainString());
      months.audit(entity.id(),actor,month,existing==null?"INSERT":"UPDATE",before,after);
    }
    var afterSet=new HashSet<>(generated.keySet());afterSet.add(month);
    return new GenerationResult(month,ForecastResolver.STATE_GENERATED,calendar.canGenerate(month,afterSet,current),countAfter,added,receipts,payments);
  }

  private static BigDecimal sum(List<Contribution> lines,String direction) {
    return lines.stream().filter(c->c.direction().equals(direction)).map(Contribution::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);
  }
}
