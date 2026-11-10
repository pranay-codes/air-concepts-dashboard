package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.airconcepts.dashboard.forecast.excel.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class WorkbookPopulatorTest {
  static ForecastDataset data(int count,int manual,int staff){
    var docs=new ArrayList<Document>();var items=new ArrayList<ManualItem>();var employees=new ArrayList<StaffingLine>();var month=YearMonth.of(2026,11);
    for(int i=0;i<count;i++)docs.add(new Document(UUID.randomUUID(),"ACCREC","INV-"+i,"Synthetic",LocalDate.of(2026,10,1),LocalDate.of(2026,11,1),null,null,new BigDecimal("115.00"),false,null,List.of(),false));
    for(int i=0;i<manual;i++)items.add(new ManualItem(UUID.randomUUID(),"OUT","RENT","Rent "+i,new BigDecimal("10.00"),"MONTHLY",1,month,null,true));
    for(int i=0;i<staff;i++)employees.add(new StaffingLine(UUID.randomUUID(),"Role "+i,new BigDecimal("100.00"),1,month,null,true));
    return new ForecastDataset(UUID.randomUUID(),"Synthetic Entity",month,LocalDate.of(2026,11,10),new BigDecimal("1000.00"),"Statement",docs,items,employees,new SyncInfo(Instant.EPOCH,"SUCCEEDED",false));
  }
  @Test void frUc04_21_populatedFixtureMatchesCalculator()throws Exception{
    var data=data(2,1,1);var result=new ForecastCalculator().calculate(data);
    try(var w=new WorkbookValidator().validate("template.xlsx",WorkbookValidatorTest.fixture())){
      var warnings=new WorkbookPopulator().populate(w,data,result,Instant.parse("2026-11-10T00:00:00Z"));
      var check=new ForecastReconciler().reconcile(w,result);
      assertThat(check.reconciled()).as(check.warnings().toString()).isTrue();assertThat(warnings).isEmpty();
      assertThat(w.getSheet("Staffing").getRow(22).getCell(3).getNumericCellValue()).isEqualTo(100);
      assertThat(w.getSheet("Assumptions").getRow(5).getCell(1).getNumericCellValue()).isZero();
      assertThat(w.getSheetAt(1).getSheetName()).isEqualTo("Forecast 2026-2027");
    }
  }
  @Test void frUc04_11_201ContributionsFailWithCapacity()throws Exception{
    try(var w=new WorkbookValidator().validate("template.xlsx",WorkbookValidatorTest.fixture())){
      var data=data(201,0,0);assertThatThrownBy(()->new WorkbookPopulator().populate(w,data,new ForecastCalculator().calculate(data),Instant.EPOCH))
          .isInstanceOfSatisfying(WorkbookFailure.class,f->assertThat(f.code()).isEqualTo("FORECAST_TEMPLATE_CAPACITY"));
    }
  }
  @Test void frUc04_11_11ManualPaymentsAnd7StaffingLinesAggregateWithWarning()throws Exception{
    try(var w=new WorkbookValidator().validate("template.xlsx",WorkbookValidatorTest.fixture())){
      var data=data(0,11,7);var result=new ForecastCalculator().calculate(data);
      var warnings=new WorkbookPopulator().populate(w,data,result,Instant.EPOCH);
      assertThat(warnings).hasSize(2);assertThat(new ForecastReconciler().reconcile(w,result).reconciled()).isTrue();
      assertThat(w.getSheetAt(1).getRow(64).getCell(0).getStringCellValue()).contains("Other payments");
      assertThat(w.getSheet("Staffing").getRow(27).getCell(2).getStringCellValue()).contains("Other staff");
    }
  }
  @Test void frUc04_21_injectedMismatchSetsFlag()throws Exception{
    try(var w=new WorkbookValidator().validate("template.xlsx",WorkbookValidatorTest.fixture())){
      var data=data(1,0,0);var result=new ForecastCalculator().calculate(data);new WorkbookPopulator().populate(w,data,result,Instant.EPOCH);
      w.getSheetAt(1).getRow(25).getCell(1).setCellFormula("SUM(B8:B25)+1");
      var check=new ForecastReconciler().reconcile(w,result);assertThat(check.reconciled()).isFalse();assertThat(check.warnings()).isNotEmpty();
    }
  }
  @Test void hasNoRepositoryDependency(){assertThat(WorkbookPopulator.class.getDeclaredFields()).noneSatisfy(f->assertThat(f.getType().getName()).contains("Repository"));}
  @Test void frUc04_11_preservesFormatsAndEveryUnmappedFormulaAndClearsUnusedStaffing()throws Exception{
    record Original(int sheet,int row,int column,String formula,String format){}
    try(var w=new WorkbookValidator().validate("template.xlsx",WorkbookValidatorTest.fixture())){
      var original=new ArrayList<Original>();
      for(var sheet:w)for(var row:sheet)for(var cell:row)original.add(new Original(w.getSheetIndex(sheet),cell.getRowIndex(),cell.getColumnIndex(),cell.getCellType()==org.apache.poi.ss.usermodel.CellType.FORMULA?cell.getCellFormula():null,cell.getCellStyle().getDataFormatString()));
      var data=data(200,0,0);new WorkbookPopulator().populate(w,data,new ForecastCalculator().calculate(data),Instant.EPOCH);
      int preserved=0;
      for(var old:original){
        var cell=w.getSheetAt(old.sheet()).getRow(old.row()).getCell(old.column());assertThat(cell.getCellStyle().getDataFormatString()).isEqualTo(old.format());
        boolean staffingWrite=old.sheet()==4&&((old.row()>=9&&old.row()<=14&&old.column()<=8)||(old.row()>=22&&old.row()<=28)||(old.row()>=18&&old.row()<=29&&old.column()>=15));
        if(old.formula()!=null&&!staffingWrite){assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);assertThat(cell.getCellFormula()).isEqualTo(old.formula());preserved++;}
      }
      assertThat(preserved).isGreaterThan(500);
      var staff=w.getSheet("Staffing");for(int r=22;r<=28;r++)for(int c=3;c<=14;c++)assertThat(staff.getRow(r).getCell(c).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
      for(int r=18;r<=29;r++)for(int c=15;c<=65;c++)assertThat(staff.getRow(r).getCell(c).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
    }
  }
}
