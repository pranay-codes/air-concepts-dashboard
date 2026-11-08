package com.airconcepts.dashboard.invoices;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Business calendar rules independent of server time zone and transport. */
public final class InvoiceDates {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Pacific/Auckland");
  private InvoiceDates() {}

  public enum Preset { ALL_DATES, THIS_MONTH, LAST_THREE_MONTHS, THIS_YEAR }
  public record Range(LocalDate from, LocalDate to) {}

  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(BUSINESS_ZONE));
  }

  public static boolean isOverdue(InvoiceStatus status, LocalDate dueDate,
      BigDecimal amountDue, LocalDate today) {
    return status == InvoiceStatus.AUTHORISED && dueDate != null && dueDate.isBefore(today)
        && amountDue != null && amountDue.signum() > 0;
  }

  public static Range range(Preset preset, LocalDate today) {
    return switch (preset) {
      case ALL_DATES -> new Range(null, null);
      case THIS_MONTH -> new Range(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()));
      case LAST_THREE_MONTHS -> new Range(today.minusMonths(2).withDayOfMonth(1),
          today.with(TemporalAdjusters.lastDayOfMonth()));
      case THIS_YEAR -> new Range(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()));
    };
  }
}
