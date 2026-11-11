package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.ForecastResult;
import com.airconcepts.dashboard.forecast.excel.*;
import com.airconcepts.dashboard.security.CurrentUser;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class ForecastExportService {
  public record Export(byte[] bytes,String filename,boolean reconciled,List<String> warnings,Instant generatedAt){}
  private final ForecastAccess access;private final ForecastResolver resolver;private final ForecastCalendar calendar;private final WorkbookValidator validator;
  private final WorkbookPopulator populator;private final ForecastReconciler reconciler;private final ForecastExportRepository repository;private final Clock clock;
  public ForecastExportService(ForecastAccess access,ForecastResolver resolver,ForecastCalendar calendar,WorkbookValidator validator,WorkbookPopulator populator,
      ForecastReconciler reconciler,ForecastExportRepository repository,Clock clock){
    this.access=access;this.resolver=resolver;this.calendar=calendar;this.validator=validator;this.populator=populator;this.reconciler=reconciler;this.repository=repository;this.clock=clock;
  }
  /** Months without a frozen forecast are named, so an export is never taken for a finished forecast. */
  static List<String> coverageWarnings(ForecastResult result){
    List<String> warnings=new ArrayList<>();
    var notGenerated=result.months().stream().filter(m->ForecastResolver.STATE_NOT_GENERATED.equals(m.state())).map(m->m.month().toString()).toList();
    var preview=result.months().stream().filter(m->ForecastResolver.STATE_PREVIEW.equals(m.state())).map(m->m.month().toString()).toList();
    if(!notGenerated.isEmpty()) warnings.add("Not generated, so no figures are shown: "+String.join(", ",notGenerated)+".");
    if(!preview.isEmpty()) warnings.add("Not yet generated (preview, not frozen): "+String.join(", ",preview)+".");
    return warnings;
  }
  public Export export(CurrentUser user,UUID entity,String financialYear,String filename,byte[] bytes){
    var authorised=access.requireRead(user,entity);var month=calendar.financialYearStart(financialYear);
    try(var workbook=validator.validate(filename,bytes);var output=new ByteArrayOutputStream()){
      var resolved=resolver.resolve(authorised,month);var data=resolved.data();var result=resolved.result();var generated=clock.instant();
      var warnings=new ArrayList<>(populator.populate(workbook,data,result,generated));
      warnings.addAll(coverageWarnings(result));
      var reconciliation=reconciler.reconcile(workbook,result);warnings.addAll(reconciliation.warnings());
      workbook.write(output);
      String code=repository.entityCode(entity);String name="forecast-"+code+"-"+month+"-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ForecastMonths.ZONE).format(generated)+".xlsx";
      repository.record(entity,UUID.fromString(user.subject()),month,result,reconciliation.reconciled(),warnings,generated);
      return new Export(output.toByteArray(),name,reconciliation.reconciled(),List.copyOf(warnings),generated);
    }catch(IOException failure){throw WorkbookValidator.invalid("Workbook","","The workbook could not be read or generated. Use the standard v1 template.");}
  }
}
