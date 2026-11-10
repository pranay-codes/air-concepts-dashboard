package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.calc.ForecastCalculator;
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
  private final ForecastAccess access;private final ForecastDatasetLoader loader;private final WorkbookValidator validator;
  private final WorkbookPopulator populator;private final ForecastReconciler reconciler;private final ForecastExportRepository repository;private final Clock clock;
  public ForecastExportService(ForecastAccess access,ForecastDatasetLoader loader,WorkbookValidator validator,WorkbookPopulator populator,ForecastReconciler reconciler,ForecastExportRepository repository,Clock clock){
    this.access=access;this.loader=loader;this.validator=validator;this.populator=populator;this.reconciler=reconciler;this.repository=repository;this.clock=clock;
  }
  public Export export(CurrentUser user,UUID entity,String start,String filename,byte[] bytes){
    var authorised=access.requireRead(user,entity);var month=ForecastMonths.window(start,clock);
    try(var workbook=validator.validate(filename,bytes);var output=new ByteArrayOutputStream()){
      var data=loader.load(authorised,month);var result=new ForecastCalculator().calculate(data);var generated=clock.instant();
      var warnings=new ArrayList<>(populator.populate(workbook,data,result,generated));var reconciliation=reconciler.reconcile(workbook,result);warnings.addAll(reconciliation.warnings());
      workbook.write(output);
      String code=repository.entityCode(entity);String name="forecast-"+code+"-"+month+"-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ForecastMonths.ZONE).format(generated)+".xlsx";
      repository.record(entity,UUID.fromString(user.subject()),month,result,reconciliation.reconciled(),warnings,generated);
      return new Export(output.toByteArray(),name,reconciliation.reconciled(),List.copyOf(warnings),generated);
    }catch(IOException failure){throw WorkbookValidator.invalid("Workbook","","The workbook could not be read or generated. Use the standard v1 template.");}
  }
}
