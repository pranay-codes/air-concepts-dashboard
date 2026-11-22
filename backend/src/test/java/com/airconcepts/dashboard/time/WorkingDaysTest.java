package com.airconcepts.dashboard.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkingDaysTest {
  private final WorkingDays workingDays = new WorkingDays(Set.of(LocalDate.of(2026, 11, 23)));

  @Test void gl11_skipsWeekendsAndConfiguredHolidays() {
    var friday = LocalDate.of(2026, 11, 20);
    assertThat(workingDays.addWorkingDays(friday, 2)).isEqualTo(LocalDate.of(2026, 11, 25));
    assertThat(workingDays.between(friday, LocalDate.of(2026, 11, 25))).isEqualTo(2);
  }

  @Test void gl11_thresholdIsLateOnlyAfterTheThreshold() {
    var entered = LocalDate.of(2026, 11, 20);
    assertThat(workingDays.between(entered, LocalDate.of(2026, 11, 25))).isEqualTo(2);
    assertThat(workingDays.between(entered, LocalDate.of(2026, 11, 26))).isEqualTo(3);
  }

  @Test void gl11_zeroDaysKeepsTheDateAndReverseIntervalsAreRejected() {
    var date = LocalDate.of(2026, 11, 20);
    assertThat(workingDays.addWorkingDays(date, 0)).isEqualTo(date);
    assertThatIllegalArgumentException().isThrownBy(() -> workingDays.between(date, date.minusDays(1)));
    assertThatIllegalArgumentException().isThrownBy(() -> workingDays.addWorkingDays(date, -1));
  }
}
