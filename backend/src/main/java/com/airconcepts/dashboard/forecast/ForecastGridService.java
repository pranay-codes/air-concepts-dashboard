package com.airconcepts.dashboard.forecast;

import com.airconcepts.dashboard.forecast.calc.ForecastCalculator;
import com.airconcepts.dashboard.forecast.calc.ForecastResult.Contribution;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForecastGridService {
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"month","available","generatedAt","moneyIn","moneyOut","staffingExpenses","opening","closing","openingSource","openingNote"})
  public record Month(String month,boolean available,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="date-time") Instant generatedAt,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String moneyIn,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String moneyOut,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String staffingExpenses,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String opening,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String closing,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String openingSource,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String openingNote) {}
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"direction","contact","company","amounts","companyAmounts","documentId","note","source","positionBefore"})
  public record Row(@io.swagger.v3.oas.annotations.media.Schema(allowableValues={"IN","OUT"}) String direction,String contact,String company,Map<String,String> amounts,Map<String,String> companyAmounts,UUID documentId,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String note,String source,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String positionBefore) {}
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"entityId","months","rows","companyNotes"})
  public record Grid(UUID entityId,List<Month> months,List<Row> rows,Map<String,String> companyNotes) {}
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"created","refreshed","preserved","omittedCurrentItems","changedProtectedSchedules"})
  public record Generation(List<String> created,List<String> refreshed,List<String> preserved,int omittedCurrentItems,int changedProtectedSchedules) {}
  private final ForecastAccess access; private final ForecastDatasetLoader loader; private final ForecastGridRepository store;
  private final ForecastMonthRepository legacy; private final ForecastCalendar calendar; private final ForecastAssumptionRepository assumptions; private final ForecastNoteRepository notes;
  public ForecastGridService(ForecastAccess access,ForecastDatasetLoader loader,ForecastGridRepository store,ForecastMonthRepository legacy,ForecastCalendar calendar,ForecastAssumptionRepository assumptions,ForecastNoteRepository notes){this.access=access;this.loader=loader;this.store=store;this.legacy=legacy;this.calendar=calendar;this.assumptions=assumptions;this.notes=notes;}

  @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Grid grid(CurrentUser user,UUID id,String startValue,int count){
    access.requireRead(user,id); var start=ForecastMonths.parse(startValue);
    if(count<1||count>24) throw ForecastValidation.invalid("monthCount","Choose between 1 and 24 months.");
    var end=start.plusMonths(count-1);var records=assumptions.openingAnchors(id);var anchors=ForecastResolver.anchorValues(records);
    var chainStart=anchors.keySet().stream().filter(m->!m.isAfter(start)).max(Comparator.naturalOrder()).orElse(start);
    var timestamps=store.months(id,chainStart,end); var old=legacy.generated(id); var oldLines=legacy.lines(id,chainStart,end);
    var all=new ArrayList<>(store.lines(id,chainStart,end));
    for(var entry:oldLines.entrySet()) if(!timestamps.containsKey(entry.getKey())) for(var c:entry.getValue()) if("DOCUMENT".equals(c.source())) all.add(new ForecastGridRepository.Line(c.bucket(),c.sourceId(),c.splitSequence(),c.direction(),c.label()==null?"Unknown contact":c.label(),c.amount(),c.number()));
    var available=new HashSet<>(timestamps.keySet());available.addAll(old.keySet());
    var staff=assumptions.staffing(id,false);Map<YearMonth,BigDecimal> staffingTotals=new HashMap<>();
    for(var month=chainStart;!month.isAfter(end);month=month.plusMonths(1)) {
      BigDecimal total=BigDecimal.ZERO.setScale(2);
      for(var item:staff) if(item.active()&&ForecastCalculator.active(month,item.startMonth(),item.endMonth()))
        total=total.add(item.paymentFrequency()==null?item.monthlyCost():StaffingSchedule.monthly(item.hourlyRate(),item.weeklyHours(),item.paymentFrequency(),item.firstPayday(),item.employmentStart(),item.employmentEnd(),month));
      staffingTotals.put(month,total);
    }
    var manualItems=assumptions.manualItems(id,false);
    var positions=assumptions.manualPositions(id);
    var currentMonth=calendar.currentMonth();
    var savedManual=store.manualLines(id,chainStart,end).stream().filter(line->line.month().isBefore(currentMonth)).toList();
    var balanceLines=new ArrayList<>(all);
    for(var line:savedManual) balanceLines.add(new ForecastGridRepository.Line(line.month(),line.itemId(),null,line.direction(),line.description(),line.amount()));
    for(var item:manualItems) if(item.active()) for(var month=chainStart;!month.isAfter(end);month=month.plusMonths(1)) {
      if(!month.isBefore(currentMonth) && ForecastCalculator.active(month,item.startMonth(),item.endMonth()) && (!"ONE_OFF".equals(item.recurrence()) || month.equals(item.startMonth())))
        balanceLines.add(new ForecastGridRepository.Line(month,item.id(),null,item.direction(),item.description(),item.amount()));
    }
    staffingTotals.forEach((month,total)->balanceLines.add(new ForecastGridRepository.Line(month,null,null,"OUT","Staffing Expenses",total)));
    var balances=ForecastGridBalances.calculate(chainStart,end,available,balanceLines,anchors);
    List<Month> months=new ArrayList<>(); for(int i=0;i<count;i++){var m=start.plusMonths(i);var ts=timestamps.get(m);var prior=old.get(m);var b=balances.get(m);var anchor=records.get(m);months.add(new Month(m.toString(),ts!=null||prior!=null,ts!=null?ts:prior==null?null:prior.generatedAt(),decimal(b.moneyIn()),decimal(b.moneyOut()),b.moneyOut()==null?null:decimal(staffingTotals.get(m)),decimal(b.opening()),decimal(b.closing()),anchor!=null?"ENTERED":b.opening()!=null?"CARRIED_FORWARD":null,anchor==null?null:anchor.source()));}
    record RowKey(String direction,String contact,String number,UUID documentId) {}
    Map<RowKey,Map<String,BigDecimal>> groups=new HashMap<>();
    for(var line:all) if(!line.month().isBefore(start)) {
      var key=new RowKey(line.direction(),line.contact(),line.number(),line.documentId());
      groups.computeIfAbsent(key,k->new TreeMap<>()).merge(line.month().toString(),line.amount(),BigDecimal::add);
    }
    Map<UUID,String> documentNotes=new HashMap<>(), manualNotes=new HashMap<>();
    for(var note:notes.notes(id)) {
      if("DOCUMENT".equals(note.source())) documentNotes.put(note.sourceId(),note.note());
      if("MANUAL".equals(note.source())) manualNotes.put(note.sourceId(),note.note());
    }
    record CompanyKey(String direction,String contact) {}
    Map<CompanyKey,Map<String,BigDecimal>> companyTotals=new HashMap<>();
    groups.forEach((key,values)->values.forEach((month,amount)->companyTotals.computeIfAbsent(new CompanyKey(key.direction(),key.contact()),k->new TreeMap<>()).merge(month,amount,BigDecimal::add)));
    List<Row> rows=new ArrayList<>();
    groups.entrySet().stream().sorted(Comparator.comparing((Map.Entry<RowKey,Map<String,BigDecimal>> e)->e.getKey().direction())
        .thenComparing(e->e.getKey().contact()).thenComparing(e->Objects.toString(e.getKey().number(),""))
        .thenComparing(e->Objects.toString(e.getKey().documentId(),""))).forEach(entry->{
      var key=entry.getKey();Map<String,String> values=new TreeMap<>();entry.getValue().forEach((m,amount)->values.put(m,amount.toPlainString()));
      Map<String,String> totals=new TreeMap<>();companyTotals.getOrDefault(new CompanyKey(key.direction(),key.contact()),Map.of()).forEach((m,amount)->totals.put(m,amount.toPlainString()));
      rows.add(new Row(key.direction(),key.contact()+" - "+(key.number()==null||key.number().isBlank()?"Number unavailable":key.number()),key.contact(),values,totals,key.documentId(),documentNotes.get(key.documentId()),"DOCUMENT",null));
    });
    record ManualKey(UUID id,String direction,String description) {}
    Map<ManualKey,Map<String,String>> manualGroups=new LinkedHashMap<>();
    for(var line:savedManual) if(!line.month().isBefore(start)) manualGroups.computeIfAbsent(new ManualKey(line.itemId(),line.direction(),line.description()),k->new TreeMap<>()).put(line.month().toString(),line.amount().toPlainString());
    for(var item:manualItems) if(item.active()) for(var m=start;!m.isAfter(end);m=m.plusMonths(1))
      if(!m.isBefore(currentMonth) && ForecastCalculator.active(m,item.startMonth(),item.endMonth()) && (!"ONE_OFF".equals(item.recurrence()) || m.equals(item.startMonth())))
        manualGroups.computeIfAbsent(new ManualKey(item.id(),item.direction(),item.description()),k->new TreeMap<>()).put(m.toString(),item.amount().toPlainString());
    manualGroups.forEach((key,values)->rows.add(new Row(key.direction(),key.description(),key.description(),values,Map.of(),key.id(),manualNotes.get(key.id()),"MANUAL",positions.get(key.id()))));
    Map<String,String> companyNotes=new HashMap<>(); for(var note:notes.companyNotes(id)) companyNotes.put(note.company(),note.note());
    return new Grid(id,List.copyOf(months),List.copyOf(rows),Map.copyOf(companyNotes));
  }
  private static String decimal(BigDecimal amount){return amount==null?null:amount.toPlainString();}
  /** The current month may be written only on its first days; overdue items move forward in its last days. */
  private static final int MONTH_START_DAYS=10, MONTH_END_DAYS=10;
  private static Contribution contribution(ForecastGridRepository.Line line){
    return new Contribution(line.documentId(),"DOCUMENT",line.direction(),null,line.number(),line.contact(),null,line.month(),line.amount(),null,false,line.splitSequence(),null);
  }
  /** Whether a saved line already covers a calculated one. An unsplit line covers every split of its document, and the reverse. */
  private static boolean covers(Contribution saved,Contribution line){
    return saved.sourceId().equals(line.sourceId())&&(saved.splitSequence()==null||line.splitSequence()==null||saved.splitSequence().equals(line.splitSequence()));
  }

  @Transactional
  public Generation generate(CurrentUser user,UUID id){
    var entity=access.requireWrite(user,id);legacy.lockEntity(id);var now=calendar.currentMonth();var existing=store.months(id,now,now.plusMonths(6));var old=legacy.generated(id);
    var data=loader.load(entity,now);
    if(data.sync()==null||data.sync().lastSuccessfulSyncAt()==null) throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY,"FORECAST_SYNC_REQUIRED","Sync Xero before creating a forecast.");
    // A saved document/split in a protected month must not be placed again in a future revision.
    Map<UUID,Map<String,BigDecimal>> protectedLines=new HashMap<>();var first=YearMonth.of(1,1);
    var savedLines=store.lines(id,first,now);
    for(var line:savedLines) protectedLines.computeIfAbsent(line.documentId(),k->new HashMap<>()).put(String.valueOf(line.splitSequence()),line.amount());
    var gridMonths=store.months(id,first,now);var legacyLines=legacy.lines(id,first,now);
    for(var entry:legacyLines.entrySet()) if(!gridMonths.containsKey(entry.getKey()))
      for(var line:entry.getValue()) if("DOCUMENT".equals(line.source())) protectedLines.computeIfAbsent(line.sourceId(),k->new HashMap<>()).put(String.valueOf(line.splitSequence()),line.amount());
    var all=new ForecastCalculator().calculate(data).contributions().stream().filter(c->"DOCUMENT".equals(c.source())).toList();
    Set<UUID> changed=new HashSet<>();
    for(var c:all) {var protectedDoc=protectedLines.get(c.sourceId());if(protectedDoc==null)continue;
      if((c.splitSequence()==null&&!protectedDoc.containsKey("null"))||(c.splitSequence()!=null&&protectedDoc.containsKey("null"))) changed.add(c.sourceId());
      var prior=protectedDoc.get(String.valueOf(c.splitSequence()));if(prior!=null&&prior.compareTo(c.amount())!=0)changed.add(c.sourceId());
    }
    for(var entry:protectedLines.entrySet()) if(all.stream().anyMatch(c->c.sourceId().equals(entry.getKey())))
      for(var seq:entry.getValue().keySet()) if(all.stream().noneMatch(c->c.sourceId().equals(entry.getKey())&&String.valueOf(c.splitSequence()).equals(seq))) changed.add(entry.getKey());
    var calculated=all.stream().filter(c->!changed.contains(c.sourceId())).filter(c->!protectedLines.getOrDefault(c.sourceId(),Map.of()).containsKey(String.valueOf(c.splitSequence()))).toList();
    int day=data.today().getDayOfMonth();
    boolean monthStart=day<=MONTH_START_DAYS, monthEnd=day>data.today().lengthOfMonth()-MONTH_END_DAYS;
    boolean currentSaved=existing.containsKey(now)||old.containsKey(now);
    // The current month is only added to, and only on its first days: saved lines stay as they are, and unpaid
    // documents due this month or overdue that it does not list yet are added (FR-UC04-15).
    List<Contribution> kept=!monthStart?List.of()
        :existing.containsKey(now)?savedLines.stream().filter(line->line.month().equals(now)).map(ForecastGridService::contribution).toList()
        :legacyLines.getOrDefault(now,List.of()).stream().filter(c->"DOCUMENT".equals(c.source())).toList();
    List<Contribution> additions=!monthStart?List.of():all.stream()
        .filter(c->c.bucket().equals(now)&&(c.overdue()||calculated.contains(c))&&kept.stream().noneMatch(line->covers(line,c))).toList();
    // At month end everything still overdue is planned into next month, even if an earlier month already lists it.
    List<Contribution> carried=monthEnd?all.stream().filter(Contribution::overdue).toList():List.of();
    var placedOverdue=monthEnd?carried:additions.stream().filter(Contribution::overdue).toList();
    int omitted=monthStart?0:(int)calculated.stream().filter(c->c.bucket().equals(now)&&!carried.contains(c)).map(Contribution::sourceId).distinct().count();
    int excludedChanged=(int)changed.stream().filter(doc->all.stream().anyMatch(c->c.sourceId().equals(doc)&&!placedOverdue.contains(c))).count();
    var actor=UUID.fromString(user.subject());List<String> created=new ArrayList<>(),refreshed=new ArrayList<>(),preserved=new ArrayList<>();
    if(monthStart&&!(existing.containsKey(now)&&additions.isEmpty())) {
      var lines=new ArrayList<>(kept);lines.addAll(additions);store.append(id,now,actor,lines);assumptions.captureManualMonth(id,now);
      Map<String,Object> detail=new HashMap<>(Map.of("grid_snapshot",true,"line_count",lines.size(),"added_count",additions.size()));
      if(!existing.containsKey(now)&&old.containsKey(now)) detail.put("legacy_copy",true);
      legacy.audit(id,actor,now,"INSERT",Map.of(),detail);
      (!currentSaved?created:additions.isEmpty()?preserved:refreshed).add(now.toString());
    } else if(currentSaved) preserved.add(now.toString());
    for(int i=1;i<=6;i++) {var month=now.plusMonths(i);
      var lines=new ArrayList<>(calculated.stream().filter(c->c.bucket().equals(month)).toList());
      if(i==1) lines.addAll(carried);
      store.append(id,month,actor,lines);assumptions.captureManualMonth(id,month);
      legacy.audit(id,actor,month,"INSERT",Map.of(),Map.of("grid_snapshot",true,"line_count",lines.size()));
      if(existing.containsKey(month))refreshed.add(month.toString());else created.add(month.toString());
    }
    return new Generation(List.copyOf(created),List.copyOf(refreshed),List.copyOf(preserved),omitted,excludedChanged);
  }
}
