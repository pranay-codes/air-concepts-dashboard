package com.airconcepts.dashboard.forecast;

import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.forecast.ForecastAssumptionRepository.OpeningBalance;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.SyncInfo;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Builds one financial year from the same rules for the dashboard, month items, generation and Excel export (FR-UC04-21).
 * Generated months show their frozen lines; the one month that can be generated shows a preview; other months show none.
 */
@Component
public class ForecastResolver {
  public static final String STATE_GENERATED="GENERATED",STATE_PREVIEW="PREVIEW",STATE_NOT_GENERATED="NOT_GENERATED";
  public record Resolved(ForecastDataset data,ForecastResult result,String openingSource) {}

  private final ForecastDatasetLoader loader;private final ForecastMonthRepository months;private final ForecastAssumptionRepository assumptions;
  private final ForecastCalendar calendar;private final ForecastCalculator calculator=new ForecastCalculator();

  public ForecastResolver(ForecastDatasetLoader loader,ForecastMonthRepository months,ForecastAssumptionRepository assumptions,ForecastCalendar calendar) {
    this.loader=loader;this.months=months;this.assumptions=assumptions;this.calendar=calendar;
  }

  public Resolved resolve(Entity entity,YearMonth start) {
    var end=start.plusMonths(11);var current=calendar.currentMonth();
    var generated=months.generated(entity.id());var generatedSet=generated.keySet();
    var anchors=assumptions.openingAnchors(entity.id());
    var openings=ForecastChain.openings(start,end,generatedSet,months.netByMonth(entity.id()),anchorValues(anchors));
    var base=loader.load(entity,start);
    var calculated=calculator.calculate(withOpening(base,openings.get(start),null));
    var stored=months.lines(entity.id(),start,end);var placed=months.placement(entity.id());

    List<MonthRow> rows=new ArrayList<>();List<Contribution> contributions=new ArrayList<>();
    List<ForecastException> exceptions=new ArrayList<>();
    for(var exception:calculated.exceptions()) if(!"OVERDUE".equals(exception.kind())) exceptions.add(exception);
    List<YearMonth> notGenerated=new ArrayList<>();List<String> pendingNotes=new ArrayList<>();
    for(int i=0;i<12;i++) {
      var month=start.plusMonths(i);var opening=openings.get(month);var generatedMonth=generated.get(month);
      boolean generatable=calendar.canGenerate(month,generatedSet,current);
      var pending=ForecastPlacement.unplaced(calculated.contributions(),month,placed);
      int pendingCount=generatedMonth==null?0:pending.size();
      if(generatedMonth!=null&&pendingCount>0) pendingNotes.add(month+": "+pendingCount+(pendingCount==1?" item":" items")
          +(generatable?" to add by regenerating." : " not placed; this month is locked."));
      String state;List<Contribution> lines;
      if(generatedMonth!=null){state=STATE_GENERATED;lines=stored.getOrDefault(month,List.of());}
      else if(generatable){state=STATE_PREVIEW;lines=pending;}
      else {state=STATE_NOT_GENERATED;lines=null;notGenerated.add(month);}
      if(lines==null) {
        rows.add(new MonthRow(month,opening,null,null,null,null,0,0,false,state,generatable,null,pendingCount));
        continue;
      }
      contributions.addAll(lines);
      var receipts=sum(lines,"IN");var payments=sum(lines,"OUT");var net=receipts.subtract(payments);
      rows.add(new MonthRow(month,opening,receipts,payments,net,opening==null?null:opening.add(net),
          (int)lines.stream().filter(c->c.direction().equals("IN")).count(),(int)lines.stream().filter(c->c.direction().equals("OUT")).count(),
          lines.stream().anyMatch(Contribution::overdue),state,generatable,generatedMonth==null?null:generatedMonth.generatedAt(),pendingCount));
    }
    if(!notGenerated.isEmpty()) exceptions.add(new ForecastException("NOT_GENERATED",null,"Not generated: "+notGenerated.stream()
        .map(m->m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH)+" "+m.getYear()).reduce((a,b)->a+", "+b).orElse("")+
        ". Totals are shown once every month is generated."));
    for(var note:pendingNotes) exceptions.add(new ForecastException("PENDING_ITEMS",null,note));

    var beyond=beyondAfter(calculated.contributions(),end,placed);
    String source=generated.containsKey(start.minusMonths(1))?"Previous month forecast":(anchors.get(start)==null?null:anchors.get(start).source());
    var totals=new Totals(sumNullable(rows,MonthRow::receipts),sumNullable(rows,MonthRow::payments),rows.getLast().closing());
    return new Resolved(withOpening(base,openings.get(start),source),new ForecastResult(List.copyOf(rows),totals,List.copyOf(exceptions),beyond,
        List.copyOf(contributions),calculated.exclusions()),source);
  }

  static Map<YearMonth,BigDecimal> anchorValues(Map<YearMonth,OpeningBalance> anchors) {
    Map<YearMonth,BigDecimal> values=new HashMap<>();
    anchors.forEach((month,balance)->{if(balance.openingBalance()!=null) values.put(month,balance.openingBalance());});
    return values;
  }

  /** Same dataset with the chain's opening balance for the first month. */
  public static ForecastDataset withOpening(ForecastDataset data,BigDecimal opening,String source) {
    return new ForecastDataset(data.entityId(),data.entityName(),data.startMonth(),data.today(),opening,source,data.documents(),data.manualItems(),
        data.staffing(),data.sync());
  }

  private static BeyondWindow beyondAfter(List<Contribution> calculated,YearMonth end,ForecastPlacement.Placed placed) {
    BigDecimal in=BigDecimal.ZERO,out=BigDecimal.ZERO;
    for(var line:calculated) if(line.bucket().isAfter(end)&&ForecastPlacement.isUnplaced(line,placed)) {
      if(line.direction().equals("IN")) in=in.add(line.amount());else out=out.add(line.amount());
    }
    return new BeyondWindow(in.setScale(2),out.setScale(2));
  }

  private static BigDecimal sum(List<Contribution> lines,String direction) {
    return lines.stream().filter(c->c.direction().equals(direction)).map(Contribution::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);
  }

  /** Null when any month has no figures: a missing month is never counted as zero. */
  private static BigDecimal sumNullable(List<MonthRow> rows,java.util.function.Function<MonthRow,BigDecimal> field) {
    if(rows.stream().anyMatch(r->field.apply(r)==null)) return null;
    return rows.stream().map(field).reduce(new BigDecimal("0.00"),BigDecimal::add);
  }
}
