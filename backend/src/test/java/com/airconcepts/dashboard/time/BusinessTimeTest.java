package com.airconcepts.dashboard.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BusinessTimeTest {
  @Test void businessZoneIsAuckland() {
    assertThat(BusinessTime.ZONE.getId()).isEqualTo("Pacific/Auckland");
  }

  @Test void todayIsTheAucklandDateNotTheUtcDate() {
    // 09:00 UTC on 9 November is 22:00 on 9 November in Auckland (NZDT, +13:00): the same day.
    // 12:00 UTC on 9 November is 01:00 on 10 November in Auckland: the date has moved on.
    var clock = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    assertThat(BusinessTime.today(clock)).isEqualTo(LocalDate.of(2026, 11, 10));
  }

  @Test void dateOfUsesTheAucklandCalendar() {
    // Local midnight on 10 November in Auckland is 9 November 11:00 UTC.
    assertThat(BusinessTime.dateOf(Instant.parse("2026-11-09T11:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 10));
    // UTC midnight on 10 November is 13:00 on 10 November in Auckland.
    assertThat(BusinessTime.dateOf(Instant.parse("2026-11-10T00:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 10));
  }
}
