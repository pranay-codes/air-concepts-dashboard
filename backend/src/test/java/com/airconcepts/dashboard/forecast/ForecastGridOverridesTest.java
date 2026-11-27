package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;

class ForecastGridOverridesTest {
  @Test void frUc04_companyOverrideReplacesIndividualAmountsInTotal() {
    var month=YearMonth.of(2026,11);var first=UUID.randomUUID();var second=UUID.randomUUID();
    var lines=List.of(new ForecastGridRepository.Line(month,first,null,"IN","Acme",new BigDecimal("10")),
        new ForecastGridRepository.Line(month,second,null,"IN","Acme",new BigDecimal("20")));
    var overrides=List.of(new ForecastGridOverrides.Override(month,"IN","DOCUMENT",first.toString(),new BigDecimal("12")),
        new ForecastGridOverrides.Override(month,"IN","COMPANY","Acme",new BigDecimal("50")));
    var result=ForecastGridOverrides.apply(lines,overrides);
    assertThat(result.rows().stream().map(ForecastGridRepository.Line::amount).toList()).containsExactly(new BigDecimal("12"),new BigDecimal("20"));
    assertThat(result.balances().stream().map(ForecastGridRepository.Line::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("50");
  }
  @Test void frUc04_documentOverridePopulatesAnEmptyFutureMonth() {
    var month=YearMonth.of(2026,11);var next=month.plusMonths(1);var document=UUID.randomUUID();
    var original=new ForecastGridRepository.Line(month,document,null,"OUT","Supplier",new BigDecimal("10.00"),"BILL-1");
    var result=ForecastGridOverrides.apply(List.of(original),List.of(new ForecastGridOverrides.Override(next,"OUT","DOCUMENT",document.toString(),new BigDecimal("35.00"))));
    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0).amount()).isEqualByComparingTo("10.00");
    assertThat(result.rows().get(1).month()).isEqualTo(next);
    assertThat(result.rows().get(1).amount()).isEqualByComparingTo("35.00");
  }
  @Test void frUc04_documentOverrideRetainsItsCompanyAfterSourceRename() {
    var month=YearMonth.of(2026,11);var document=UUID.randomUUID();
    var current=new ForecastGridRepository.Line(month,document,null,"IN","New name",new BigDecimal("10.00"),"INV-1");
    var change=new ForecastGridOverrides.Override(month,"IN","DOCUMENT",document.toString(),new BigDecimal("35.00"),"Old name","INV-1");
    assertThat(ForecastGridOverrides.apply(List.of(current),List.of(change)).rows().getFirst().contact()).isEqualTo("Old name");
  }
  @Test void frUc04_overrideReplacesCarriedAmountOnlyInItsMonth() {
    var now=YearMonth.of(2026,11);var next=now.plusMonths(1);var document=UUID.randomUUID();
    var lines=List.of(new ForecastGridRepository.Line(now,document,null,"IN","Acme",new BigDecimal("100.00")),new ForecastGridRepository.Line(next,document,null,"IN","Acme",new BigDecimal("100.00")));
    var result=ForecastGridOverrides.apply(lines,List.of(new ForecastGridOverrides.Override(next,"IN","DOCUMENT",document.toString(),new BigDecimal("40.00"))));
    assertThat(result.balances()).filteredOn(line->line.month().equals(now)).singleElement().satisfies(line->assertThat(line.amount()).isEqualByComparingTo("100.00"));
    assertThat(result.balances()).filteredOn(line->line.month().equals(next)).singleElement().satisfies(line->assertThat(line.amount()).isEqualByComparingTo("40.00"));
  }
}
