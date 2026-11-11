package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ForecastCalendarTest {
  private final Clock clock=Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC);
  private final ForecastCalendar calendar=new ForecastCalendar(4,clock);
  private static final YearMonth NOV=YearMonth.of(2026,11),OCT=YearMonth.of(2026,10),DEC=YearMonth.of(2026,12);

  @Test void frUc04_22_financialYearDefaultsToTheYearContainingToday(){
    assertThat(calendar.currentFinancialYearStart()).isEqualTo(YearMonth.of(2026,4));
    assertThat(calendar.financialYearContaining(YearMonth.of(2027,3))).isEqualTo(YearMonth.of(2026,4));
    assertThat(calendar.financialYearContaining(YearMonth.of(2027,4))).isEqualTo(YearMonth.of(2027,4));
    assertThat(calendar.financialYearStart(null)).isEqualTo(YearMonth.of(2026,4));
    assertThat(calendar.label(YearMonth.of(2026,4))).isEqualTo("2026-2027");
  }
  @Test void frUc04_22_rejectsMalformedAndOutOfRangeYears(){
    assertThat(calendar.financialYearStart("2025")).isEqualTo(YearMonth.of(2025,4));
    assertThatThrownBy(()->calendar.financialYearStart("26")).isInstanceOf(ApiFailure.class);
    assertThatThrownBy(()->calendar.financialYearStart("2000")).isInstanceOf(ApiFailure.class);
    assertThatThrownBy(()->calendar.financialYearStart("2040")).isInstanceOf(ApiFailure.class);
  }
  @Test void frUc04_23_onlyTheNextMonthOfAnUnbrokenChainCanBeGenerated(){
    assertThat(calendar.canGenerate(NOV,Set.of(OCT),NOV)).isTrue();
    assertThat(calendar.canGenerate(DEC,Set.of(OCT),NOV)).isFalse();
    assertThat(calendar.canGenerate(DEC,Set.of(NOV),NOV)).isTrue();
  }
  @Test void frUc04_24_aGeneratedMonthIsLockedOnceTheFollowingMonthIsGenerated(){
    assertThat(calendar.canGenerate(NOV,Set.of(NOV),NOV)).isTrue();
    assertThat(calendar.canGenerate(NOV,Set.of(NOV,DEC),NOV)).isFalse();
    assertThat(calendar.canGenerate(DEC,Set.of(NOV,DEC),NOV)).isTrue();
  }
  @Test void frUc04_23_pastAndFarFutureMonthsAreNotGeneratable(){
    assertThat(calendar.canGenerate(OCT,Set.of(),NOV)).isFalse();
    assertThat(calendar.canGenerate(NOV.plusMonths(12),Set.of(),NOV)).isFalse();
    assertThat(calendar.inGenerationWindow(NOV.plusMonths(11),NOV)).isTrue();
  }
}
