package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class StaffingScheduleTest {
  @Test void frUc04_03_countsWeeklyAndFortnightlyPaydays() {
    var start=LocalDate.of(2026,1,2);
    assertThat(StaffingSchedule.monthly(new BigDecimal("25"),new BigDecimal("40"),"WEEKLY",start,null,null,YearMonth.of(2026,1))).isEqualByComparingTo("5000.00");
    assertThat(StaffingSchedule.monthly(new BigDecimal("25"),new BigDecimal("40"),"FORTNIGHTLY",start,null,null,YearMonth.of(2026,2))).isEqualByComparingTo("4000.00");
  }
  @Test void frUc04_03_clampsMonthlyPaydayAndEnforcesEmploymentDates() {
    assertThat(StaffingSchedule.monthly(new BigDecimal("30"),new BigDecimal("40"),"MONTHLY",LocalDate.of(2026,1,31),LocalDate.of(2026,2,1),LocalDate.of(2026,2,28),YearMonth.of(2026,2))).isEqualByComparingTo("5200.00");
    assertThat(StaffingSchedule.monthly(new BigDecimal("30"),new BigDecimal("40"),"MONTHLY",LocalDate.of(2026,1,31),null,LocalDate.of(2026,2,27),YearMonth.of(2026,2))).isEqualByComparingTo("0.00");
  }
  @Test void frUc04_03_roundsEachWeeksWagesBeforeCountingPayments() {
    assertThat(StaffingSchedule.monthly(new BigDecimal("1.25"),new BigDecimal("1.25"),"WEEKLY",LocalDate.of(2026,1,2),null,null,YearMonth.of(2026,1))).isEqualByComparingTo("7.80");
  }
}
