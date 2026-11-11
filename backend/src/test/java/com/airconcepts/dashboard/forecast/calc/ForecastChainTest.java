package com.airconcepts.dashboard.forecast.calc;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
class ForecastChainTest {
  private static final YearMonth APR=YearMonth.of(2026,4),MAY=APR.plusMonths(1),JUN=APR.plusMonths(2);
  @Test void frUc04_23_closingOfAGeneratedMonthBecomesTheNextOpening(){
    var openings=ForecastChain.openings(APR,JUN,Set.of(APR),Map.of(APR,new BigDecimal("50.00")),Map.of(APR,new BigDecimal("100.00")));
    assertThat(openings.get(APR)).isEqualByComparingTo("100");
    assertThat(openings.get(MAY)).isEqualByComparingTo("150.00");
  }
  @Test void frUc04_23_anUngeneratedMonthPassesItsAnchorForward(){
    var openings=ForecastChain.openings(APR,JUN,Set.of(APR),Map.of(APR,new BigDecimal("50.00")),Map.of(APR,new BigDecimal("100.00"),JUN,new BigDecimal("999.00")));
    assertThat(openings.get(JUN)).isEqualByComparingTo("999");
    var gap=ForecastChain.openings(APR,JUN,Set.of(),Map.of(),Map.of(APR,new BigDecimal("100.00")));
    assertThat(gap.get(MAY)).isNull();
  }
  @Test void frUc04_23_chainWalksBackThroughEarlierGeneratedMonths(){
    var openings=ForecastChain.openings(JUN,JUN,Set.of(APR,MAY),Map.of(APR,new BigDecimal("10.00"),MAY,new BigDecimal("-5.00")),Map.of(APR,new BigDecimal("100.00")));
    assertThat(openings.get(JUN)).isEqualByComparingTo("105.00");
  }
}
