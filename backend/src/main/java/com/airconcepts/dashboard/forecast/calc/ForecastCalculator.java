package com.airconcepts.dashboard.forecast.calc;

import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Cash values are already GST-inclusive. The same result feeds dashboard and workbook. */
public final class ForecastCalculator {
  public static final OverduePolicy DEFAULT_OVERDUE_POLICY=OverduePolicy.CURRENT_MONTH;
  public static final int WINDOW_MONTHS=12;
  private static final BigDecimal ZERO=new BigDecimal("0.00");
  private final OverduePolicy overduePolicy;
  public ForecastCalculator() {this(DEFAULT_OVERDUE_POLICY);}
  public ForecastCalculator(OverduePolicy overduePolicy) {this.overduePolicy=Objects.requireNonNull(overduePolicy);}

  public ForecastResult calculate(ForecastDataset data) {
    List<Contribution> contributions=new ArrayList<>();List<ForecastException> exceptions=new ArrayList<>();List<Exclusion> exclusions=new ArrayList<>();
    for(var d:data.documents()) {
      if(d.dateConflict()) exceptions.add(new ForecastException("DATE_CONFLICT",d.id(),"Review the split schedule after a Xero change."));
      if(d.excluded()) {exclusions.add(new Exclusion(d.id(),d.number(),d.exclusionReason()));continue;}
      if(!d.splits().isEmpty()) {
        for(var line:d.splits()) addDocument(data,d,line.expectedDate(),line.amount(),DateRule.SPLIT,line.sequence(),d.splits().size(),contributions);
      } else {
        LocalDate date=d.overrideDate()!=null?d.overrideDate():d.xeroExpectedDate()!=null?d.xeroExpectedDate():d.dueDate()!=null?d.dueDate():d.issueDate();
        DateRule rule=d.overrideDate()!=null?DateRule.OVERRIDE:d.xeroExpectedDate()!=null?DateRule.XERO_EXPECTED_DATE:d.dueDate()!=null?DateRule.DUE_DATE:DateRule.ISSUE_DATE;
        if(rule==DateRule.ISSUE_DATE) exceptions.add(new ForecastException("MISSING_DUE_DATE",d.id(),"No due date recorded; invoice date used."));
        addDocument(data,d,date,d.amountDue(),rule,null,null,contributions);
      }
    }
    addOverdueSummaries(data,contributions,exceptions);
    for(var item:data.manualItems()) {
      if(!item.active()) continue;
      if(item.recurrence().equals("ONE_OFF")) {
        addManual(item,item.startMonth(),contributions);
      } else for(int i=0;i<WINDOW_MONTHS;i++) {
        var month=data.startMonth().plusMonths(i);
        if(active(month,item.startMonth(),item.endMonth())) addManual(item,month,contributions);
      }
    }
    for(var staff:data.staffing()) if(staff.active()) for(int i=0;i<WINDOW_MONTHS;i++) {
      var month=data.startMonth().plusMonths(i);
      if(active(month,staff.startMonth(),staff.endMonth())) contributions.add(new Contribution(staff.id(),"STAFFING","OUT","STAFFING",null,
          staff.label(),day(month,staff.payDayOfMonth()),month,money(staff.monthlyCost()),DateRule.STAFFING,false,null,null));
    }
    if(data.openingBalance()==null) exceptions.add(new ForecastException("NO_OPENING_BALANCE",null,"Enter an opening balance for this forecast period."));
    if(data.sync()==null||data.sync().lastSuccessfulSyncAt()==null) exceptions.add(new ForecastException("NO_SYNC_YET",null,"Run a sync to load Xero documents."));
    else if(data.sync().stale()) exceptions.add(new ForecastException("SYNC_STALE",null,"Xero data is older than 36 hours."));
    List<MonthRow> months=new ArrayList<>();BigDecimal opening=data.openingBalance()==null?null:money(data.openingBalance());
    BigDecimal totalIn=ZERO,totalOut=ZERO,beyondIn=ZERO,beyondOut=ZERO;
    for(int i=0;i<WINDOW_MONTHS;i++) {
      var month=data.startMonth().plusMonths(i);
      var rows=contributions.stream().filter(c->c.bucket().equals(month)).toList();
      var receipts=sum(rows,"IN");var payments=sum(rows,"OUT");var net=receipts.subtract(payments);
      var closing=opening==null?null:opening.add(net);
      months.add(new MonthRow(month,opening,receipts,payments,net,closing,(int)rows.stream().filter(c->c.direction().equals("IN")).count(),
          (int)rows.stream().filter(c->c.direction().equals("OUT")).count(),rows.stream().anyMatch(Contribution::overdue)));
      opening=closing;totalIn=totalIn.add(receipts);totalOut=totalOut.add(payments);
    }
    for(var c:contributions) if(c.bucket().isAfter(data.startMonth().plusMonths(WINDOW_MONTHS-1))) {
      if(c.direction().equals("IN")) beyondIn=beyondIn.add(c.amount());else beyondOut=beyondOut.add(c.amount());
    }
    return new ForecastResult(List.copyOf(months),new Totals(totalIn,totalOut,opening),List.copyOf(exceptions),new BeyondWindow(beyondIn,beyondOut),
        List.copyOf(contributions),List.copyOf(exclusions));
  }
  private void addDocument(ForecastDataset data,Document d,LocalDate date,BigDecimal amount,DateRule rule,Integer sequence,Integer count,
      List<Contribution> rows) {
    boolean overdue=date.isBefore(data.today());
    YearMonth bucket=overdue&&overduePolicy==OverduePolicy.CURRENT_MONTH?YearMonth.from(data.today()):YearMonth.from(date);
    rows.add(new Contribution(d.id(),"DOCUMENT",d.type().equals("ACCREC")?"IN":"OUT",d.type(),d.number(),d.contact(),date,bucket,money(amount),rule,overdue,sequence,count));
  }
  /** One planning summary per direction: how much overdue cash is in the forecast, and where it sits. */
  private void addOverdueSummaries(ForecastDataset data,List<Contribution> rows,List<ForecastException> exceptions) {
    for(var direction:List.of("IN","OUT")) {
      var overdue=rows.stream().filter(c->c.overdue()&&c.direction().equals(direction)).toList();
      if(overdue.isEmpty()) continue;
      long invoices=overdue.stream().map(Contribution::sourceId).distinct().count();
      BigDecimal total=overdue.stream().map(Contribution::amount).reduce(ZERO,BigDecimal::add);
      String noun=direction.equals("IN")?(invoices==1?"receipt":"receipts"):(invoices==1?"payment":"payments");
      String verb=invoices==1?"is":"are";
      String placement=overduePolicy==OverduePolicy.CURRENT_MONTH
          ?"included in "+YearMonth.from(data.today()).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ENGLISH))
          :(invoices==1?"shown at its original forecast date":"shown at their original forecast dates");
      exceptions.add(new ForecastException("OVERDUE",null,
          invoices+" overdue "+noun+" (NZD "+String.format(Locale.ENGLISH,"%,.2f",total)+") "+verb+" "+placement+"."));
    }
  }
  private static void addManual(ManualItem item,YearMonth month,List<Contribution> rows) {
    rows.add(new Contribution(item.id(),"MANUAL",item.direction(),item.category(),null,item.description(),day(month,item.dayOfMonth()),month,
        money(item.amount()),DateRule.MANUAL,false,null,null));
  }
  public static boolean active(YearMonth month,YearMonth start,YearMonth end) {return !month.isBefore(start)&&(end==null||!month.isAfter(end));}
  private static LocalDate day(YearMonth month,Integer day) {return month.atDay(Math.min(day==null?1:day,month.lengthOfMonth()));}
  private static BigDecimal money(BigDecimal value) {return value.setScale(2,java.math.RoundingMode.UNNECESSARY);}
  private static BigDecimal sum(List<Contribution> rows,String direction) {return rows.stream().filter(c->c.direction().equals(direction)).map(Contribution::amount).reduce(ZERO,BigDecimal::add);}
}
