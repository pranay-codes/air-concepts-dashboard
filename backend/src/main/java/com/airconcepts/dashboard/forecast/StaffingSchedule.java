package com.airconcepts.dashboard.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Estimated gross cash payments, not a payroll calculation. */
public final class StaffingSchedule {
  private StaffingSchedule() {}

  public static BigDecimal monthly(BigDecimal rate, BigDecimal hours, String frequency,
      LocalDate firstPayday, LocalDate employmentStart, LocalDate employmentEnd, YearMonth month) {
    BigDecimal weekly=rate.multiply(hours).setScale(2,RoundingMode.HALF_UP);
    BigDecimal payment=switch (frequency) {
      case "WEEKLY" -> weekly;
      case "FORTNIGHTLY" -> weekly.multiply(BigDecimal.TWO);
      case "MONTHLY" -> weekly.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12),2,RoundingMode.HALF_UP);
      default -> throw new IllegalArgumentException("Unknown payment frequency");
    };
    long count=0;
    for (LocalDate date=month.atDay(1); !date.isAfter(month.atEndOfMonth()); date=date.plusDays(1)) {
      if (date.isBefore(firstPayday) || (employmentStart!=null && date.isBefore(employmentStart)) || (employmentEnd!=null && date.isAfter(employmentEnd))) continue;
      boolean payday=frequency.equals("MONTHLY")
          ? date.getDayOfMonth()==Math.min(firstPayday.getDayOfMonth(),month.lengthOfMonth())
          : ChronoUnit.DAYS.between(firstPayday,date)%(frequency.equals("WEEKLY")?7:14)==0;
      if (payday) count++;
    }
    return payment.multiply(BigDecimal.valueOf(count)).setScale(2,RoundingMode.HALF_UP);
  }
}
