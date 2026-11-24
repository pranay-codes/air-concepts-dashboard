package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;

class ForecastGridBalancesTest {
  @Test void frUc04_05_totalsCarryClosingAndRespectAnExplicitOverride() {
    var nov=YearMonth.of(2026,11);var dec=nov.plusMonths(1);var jan=nov.plusMonths(2);
    var lines=List.of(new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"IN","A",new BigDecimal("125.50")),
        new ForecastGridRepository.Line(nov,UUID.randomUUID(),null,"OUT","B",new BigDecimal("25.00")),
        new ForecastGridRepository.Line(dec,UUID.randomUUID(),null,"OUT","B",new BigDecimal("200.00")));
    var rows=ForecastGridBalances.calculate(nov,jan,Set.of(nov,dec,jan),lines,Map.of(nov,new BigDecimal("50.00"),jan,new BigDecimal("8.00")));
    assertThat(rows.get(nov).moneyIn()).isEqualByComparingTo("125.50");
    assertThat(rows.get(nov).closing()).isEqualByComparingTo("150.50");
    assertThat(rows.get(dec).opening()).isEqualByComparingTo("150.50");
    assertThat(rows.get(dec).closing()).isEqualByComparingTo("-49.50");
    assertThat(rows.get(jan).opening()).isEqualByComparingTo("8.00");
  }
  @Test void frUc04_05_missingMonthBreaksCarryUntilAnotherAnchor() {
    var nov=YearMonth.of(2026,11);var dec=nov.plusMonths(1);var jan=nov.plusMonths(2);
    var rows=ForecastGridBalances.calculate(nov,jan,Set.of(nov,jan),List.of(),Map.of(nov,new BigDecimal("10.00")));
    assertThat(rows.get(dec).moneyIn()).isNull();assertThat(rows.get(dec).closing()).isNull();
    assertThat(rows.get(jan).opening()).isNull();assertThat(rows.get(jan).closing()).isNull();
  }
  @Test void frUc04_05_anchorInUncreatedMonthDoesNotInventAClosingBalance() {
    var nov=YearMonth.of(2026,11);var dec=nov.plusMonths(1);
    var rows=ForecastGridBalances.calculate(nov,dec,Set.of(dec),List.of(),Map.of(nov,new BigDecimal("20.00")));
    assertThat(rows.get(nov).opening()).isEqualByComparingTo("20.00");
    assertThat(rows.get(nov).closing()).isNull();assertThat(rows.get(dec).opening()).isNull();
  }
}
