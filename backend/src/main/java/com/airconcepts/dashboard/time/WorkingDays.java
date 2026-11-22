package com.airconcepts.dashboard.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Counts business days after the starting date, excluding configured holidays. */
public final class WorkingDays {
  private final Set<LocalDate> holidays;

  public WorkingDays(Set<LocalDate> holidays) {
    this.holidays = Set.copyOf(holidays);
  }

  public int between(LocalDate start, LocalDate end) {
    if (end.isBefore(start)) throw new IllegalArgumentException("end must not precede start");
    int count = 0;
    for (var day = start; day.isBefore(end); ) {
      day = day.plusDays(1);
      if (isWorkingDay(day)) count++;
    }
    return count;
  }

  public LocalDate addWorkingDays(LocalDate start, int days) {
    if (days < 0) throw new IllegalArgumentException("days must not be negative");
    var result = start;
    while (days > 0) {
      result = result.plusDays(1);
      if (isWorkingDay(result)) days--;
    }
    return result;
  }

  private boolean isWorkingDay(LocalDate date) {
    return date.getDayOfWeek() != DayOfWeek.SATURDAY
        && date.getDayOfWeek() != DayOfWeek.SUNDAY
        && !holidays.contains(date);
  }
}
