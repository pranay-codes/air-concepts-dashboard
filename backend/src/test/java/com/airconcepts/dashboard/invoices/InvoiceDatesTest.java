package com.airconcepts.dashboard.invoices;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

class InvoiceDatesTest {
  private final LocalDate today = LocalDate.of(2026, 11, 6);

  @Test
  void frUc15_29_onlyAuthorisedOutstandingPastDueIsOverdue() {
    assertThat(InvoiceDates.isOverdue(InvoiceStatus.AUTHORISED, today.minusDays(1), new BigDecimal("0.01"), today)).isTrue();
    for (InvoiceStatus status : InvoiceStatus.values()) {
      if (status != InvoiceStatus.AUTHORISED) {
        assertThat(InvoiceDates.isOverdue(status, today.minusDays(1), BigDecimal.TEN, today)).isFalse();
      }
    }
    assertThat(InvoiceDates.isOverdue(InvoiceStatus.AUTHORISED, today, BigDecimal.TEN, today)).isFalse();
    assertThat(InvoiceDates.isOverdue(InvoiceStatus.AUTHORISED, today.plusDays(1), BigDecimal.TEN, today)).isFalse();
    assertThat(InvoiceDates.isOverdue(InvoiceStatus.AUTHORISED, today.minusDays(1), BigDecimal.ZERO, today)).isFalse();
    assertThat(InvoiceDates.isOverdue(InvoiceStatus.AUTHORISED, today.minusDays(1), BigDecimal.TEN.negate(), today)).isFalse();
    assertThat(InvoiceDates.isOverdue(InvoiceStatus.AUTHORISED, null, BigDecimal.TEN, today)).isFalse();
    assertThat(InvoiceDates.isOverdue(InvoiceStatus.AUTHORISED, today.minusDays(1), null, today)).isFalse();
  }

  @Test
  void frUc15_27_presetsUseInclusiveCalendarBoundariesAcrossYearAndLeapDay() {
    var january = LocalDate.of(2028, 1, 15);
    assertThat(InvoiceDates.range(InvoiceDates.Preset.LAST_THREE_MONTHS, january))
        .isEqualTo(new InvoiceDates.Range(LocalDate.of(2027, 11, 1), LocalDate.of(2028, 1, 31)));
    assertThat(InvoiceDates.range(InvoiceDates.Preset.THIS_MONTH, LocalDate.of(2028, 2, 15)))
        .isEqualTo(new InvoiceDates.Range(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)));
    assertThat(InvoiceDates.range(InvoiceDates.Preset.THIS_YEAR, january))
        .isEqualTo(new InvoiceDates.Range(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31)));
    assertThat(InvoiceDates.range(InvoiceDates.Preset.ALL_DATES, january))
        .isEqualTo(new InvoiceDates.Range(null, null));
  }

  @Test
  void frUc15_29_businessDateUsesAucklandRatherThanServerTimezone() {
    var clock = Clock.fixed(Instant.parse("2026-11-05T11:30:00Z"), ZoneOffset.UTC);
    assertThat(InvoiceDates.today(clock)).isEqualTo(today);
  }
}
