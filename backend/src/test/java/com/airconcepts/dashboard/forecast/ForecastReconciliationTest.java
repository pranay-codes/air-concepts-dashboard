package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.airconcepts.dashboard.forecast.excel.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ForecastReconciliationTest {
  private static BigDecimal cash(String value){return new BigDecimal(value);}
  @Test void frUc04_21_populatedFixtureMatchesCalculatorForSplitsOverridesOverdueAndExclusions()throws Exception{
    var base=WorkbookPopulatorTest.data(0,0,1);var docs=new ArrayList<Document>();
    docs.add(new Document(UUID.randomUUID(),"ACCREC","SPLIT","Synthetic",LocalDate.of(2026,10,1),LocalDate.of(2026,11,15),null,null,cash("115.00"),false,null,
        List.of(new Split(1,LocalDate.of(2026,11,15),cash("15.00")),new Split(2,LocalDate.of(2026,12,15),cash("100.00"))),false));
    docs.add(new Document(UUID.randomUUID(),"ACCREC","OVERRIDE","Synthetic",LocalDate.of(2026,10,1),LocalDate.of(2026,11,15),LocalDate.of(2026,12,15),LocalDate.of(2027,1,15),cash("115.00"),false,null,List.of(),false));
    docs.add(new Document(UUID.randomUUID(),"ACCREC","OVERDUE","Synthetic",LocalDate.of(2026,10,1),LocalDate.of(2026,10,15),null,null,cash("115.00"),false,null,List.of(),false));
    docs.add(new Document(UUID.randomUUID(),"ACCREC","EXCLUDED","Synthetic",LocalDate.of(2026,10,1),LocalDate.of(2026,11,15),null,null,cash("999.00"),true,"Disputed",List.of(),false));
    docs.add(new Document(UUID.randomUUID(),"ACCPAY","BILL","Supplier",LocalDate.of(2026,10,1),LocalDate.of(2026,12,15),null,null,cash("75.00"),false,null,List.of(),false));
    var manual=List.of(new ManualItem(UUID.randomUUID(),"IN","OTHER_RECEIPT","Receipt",cash("13.00"),"ONE_OFF",1,base.startMonth(),null,true),
        new ManualItem(UUID.randomUUID(),"OUT","RENT","Rent",cash("10.00"),"MONTHLY",1,base.startMonth(),null,true));
    var data=new ForecastDataset(base.entityId(),base.entityName(),base.startMonth(),base.today(),base.openingBalance(),base.openingBalanceSource(),docs,manual,base.staffing(),base.sync());
    var result=new ForecastCalculator().calculate(data);
    try(var workbook=new WorkbookValidator().validate("template.xlsx",WorkbookValidatorTest.fixture())){
      new WorkbookPopulator().populate(workbook,data,result,Instant.EPOCH);var check=new ForecastReconciler().reconcile(workbook,result);
      assertThat(check.reconciled()).as(check.warnings().toString()).isTrue();assertThat(result.months().getFirst().receipts()).isEqualByComparingTo("143.00");
      assertThat(workbook.getSheet("Xero_Debtors").getRow(4).getCell(1).getStringCellValue()).isEqualTo("SPLIT (1/2)");
      for(var row:workbook.getSheet("Xero_Debtors"))if(row.getRowNum()>=4&&row.getCell(1)!=null)assertThat(row.getCell(1).toString()).doesNotContain("EXCLUDED");
    }
  }
}
