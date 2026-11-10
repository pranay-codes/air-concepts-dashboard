package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.forecast.calc.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ForecastCalculatorTest {
  private final ForecastCalculator calc=new ForecastCalculator();
  private final LocalDate today=LocalDate.of(2026,11,10);
  private final YearMonth month=YearMonth.of(2026,11);
  private Document doc(String type,String due,String expected,String override,boolean excluded,List<Split> splits) {
    return new Document(UUID.randomUUID(),type,"INV-1","Synthetic",today.minusMonths(1),
        due==null?null:LocalDate.parse(due),expected==null?null:LocalDate.parse(expected),
        override==null?null:LocalDate.parse(override),new BigDecimal("115.00"),excluded,excluded?"Disputed":null,splits,false);
  }
  private ForecastDataset data(BigDecimal balance,List<Document> documents,List<ManualItem> manual,List<StaffingLine> staff) {
    return new ForecastDataset(UUID.randomUUID(),"Synthetic",month,today,balance,"Statement",documents,manual,staff,
        new SyncInfo(Instant.parse("2026-11-09T23:00:00Z"),"SUCCEEDED",false));
  }
  @Test void frUc04_05_closingEqualsOpeningPlusNet() {
    var result=calc.calculate(data(new BigDecimal("1000.00"),List.of(doc("ACCREC","2026-11-15",null,null,false,List.of())),List.of(),List.of()));
    assertThat(result.months()).hasSize(12);
    assertThat(result.months().getFirst().closing()).isEqualByComparingTo("1115");
    assertThat(result.months().get(1).opening()).isEqualByComparingTo("1115");
  }
  @Test void frUc04_05_missingOpeningBalanceYieldsNullNotZero() {
    var result=calc.calculate(data(null,List.of(),List.of(),List.of()));
    assertThat(result.months()).allSatisfy(m->{assertThat(m.opening()).isNull();assertThat(m.closing()).isNull();});
    assertThat(result.exceptions()).anySatisfy(e->assertThat(e.kind()).isEqualTo("NO_OPENING_BALANCE"));
  }
  @Test void frUc04_14_expectedDateBeatsDueDate() {
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(doc("ACCREC","2026-11-15","2026-12-15",null,false,List.of())),List.of(),List.of()));
    assertThat(result.months().get(1).receipts()).isEqualByComparingTo("115");
    assertThat(result.contributions().getFirst().rule()).isEqualTo(DateRule.XERO_EXPECTED_DATE);
  }
  @Test void frUc04_14_overrideBeatsXeroDate() {
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(doc("ACCREC","2026-11-15","2026-12-15","2027-01-01",false,List.of())),List.of(),List.of()));
    assertThat(result.months().get(2).receipts()).isEqualByComparingTo("115");
    assertThat(result.contributions().getFirst().rule()).isEqualTo(DateRule.OVERRIDE);
  }
  @Test void frUc04_14_plannedPaymentDateForBills() {
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(doc("ACCPAY","2026-11-15","2026-12-15",null,false,List.of())),List.of(),List.of()));
    assertThat(result.months().get(1).payments()).isEqualByComparingTo("115");
  }
  @Test void frUc04_15_excludedContributesZero() {
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(doc("ACCREC","2026-11-15",null,null,true,List.of())),List.of(),List.of()));
    assertThat(result.contributions()).isEmpty();assertThat(result.exclusions()).hasSize(1);
    assertThat(result.months().getFirst().receipts()).isEqualByComparingTo("0");
  }
  @Test void frUc04_15_splitLinesSumOnce() {
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(doc("ACCREC","2026-11-15",null,null,false,
        List.of(new Split(1,LocalDate.of(2026,11,20),new BigDecimal("15")),new Split(2,LocalDate.of(2026,12,20),new BigDecimal("100"))))),List.of(),List.of()));
    assertThat(result.contributions()).hasSize(2);assertThat(result.months().getFirst().receipts()).isEqualByComparingTo("15");
    assertThat(result.months().get(1).receipts()).isEqualByComparingTo("100");
  }
  @Test void frUc04_15_overdueGoesToCurrentMonth() {
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(doc("ACCREC","2026-10-15",null,null,false,List.of())),List.of(),List.of()));
    assertThat(result.months().getFirst().containsOverdue()).isTrue();assertThat(result.months().getFirst().receipts()).isEqualByComparingTo("115");
  }
  @Test void frUc04_05_twelveRowsAndBeyondWindow() {
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(doc("ACCREC","2027-12-15",null,null,false,List.of())),List.of(),List.of()));
    assertThat(result.months()).hasSize(12);assertThat(result.beyondWindow().receipts()).isEqualByComparingTo("115");
  }
  @Test void frUc04_18_doesNotAddTaxOnTopOfInclusiveAmounts() {
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(doc("ACCREC","2026-11-15",null,null,false,List.of())),List.of(),List.of()));
    assertThat(result.months().getFirst().receipts()).isEqualByComparingTo("115");
  }
  @Test void frUc04_20_groupsByForecastDateNotIssueMonth() { frUc04_14_expectedDateBeatsDueDate(); }
  @Test void frUc04_03_monthlyItemLandsEveryMonthInclusive() {
    var manual=new ManualItem(UUID.randomUUID(),"OUT","RENT","Rent",new BigDecimal("200"),"MONTHLY",31,month,month.plusMonths(1),true);
    var staff=new StaffingLine(UUID.randomUUID(),"Role",new BigDecimal("100"),15,month,month,true);
    var result=calc.calculate(data(BigDecimal.ZERO,List.of(),List.of(manual),List.of(staff)));
    assertThat(result.months().getFirst().payments()).isEqualByComparingTo("300");
    assertThat(result.months().get(1).payments()).isEqualByComparingTo("200");
    assertThat(result.months().get(2).payments()).isEqualByComparingTo("0");
    assertThat(result.contributions()).anySatisfy(c->assertThat(c.date()).isEqualTo(LocalDate.of(2026,11,30)));
  }
}
