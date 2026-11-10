package com.airconcepts.dashboard.forecast.excel;
import com.airconcepts.dashboard.forecast.calc.ForecastResult;
import com.airconcepts.dashboard.forecast.excel.WorkbookFailure.Problem;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;
@Component
public class ForecastReconciler {
  public record Reconciliation(boolean reconciled,List<String> warnings){}
  public Reconciliation reconcile(XSSFWorkbook workbook,ForecastResult result){
    try{XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);}
    catch(RuntimeException failure){throw new WorkbookFailure("FORECAST_FORMULA_UNSUPPORTED","The workbook formula evaluation did not complete.",List.of(new Problem("Workbook","","Use the supported v1 formula set.")));}
    var sheet=WorkbookValidator.forecast(workbook);List<String> warnings=new ArrayList<>();
    for(int m=0;m<12;m++){
      var expected=result.months().get(m);compare(sheet,25,m+1,expected.receipts(),expected.month()+" receipts",warnings);
      compare(sheet,66,m+1,expected.payments(),expected.month()+" payments",warnings);compare(sheet,67,m+1,expected.closing(),expected.month()+" closing",warnings);
    }
    compare(sheet,4,1,result.months().getFirst().opening(),"Opening balance",warnings);
    return new Reconciliation(warnings.isEmpty(),List.copyOf(warnings));
  }
  private static void compare(Sheet sheet,int row,int column,BigDecimal expected,String label,List<String> warnings){
    var cell=sheet.getRow(row).getCell(column);boolean numeric=cell.getCellType()==CellType.NUMERIC||(cell.getCellType()==CellType.FORMULA&&cell.getCachedFormulaResultType()==CellType.NUMERIC);
    BigDecimal actual=numeric?BigDecimal.valueOf(cell.getNumericCellValue()):null;
    if(expected==null||actual==null||actual.subtract(expected).abs().compareTo(new BigDecimal("0.005"))>0)
      warnings.add(label+": workbook "+Objects.toString(actual,"Unavailable")+", dashboard "+Objects.toString(expected,"Not recorded")+".");
  }
}
