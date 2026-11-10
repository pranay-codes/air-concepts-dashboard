package com.airconcepts.dashboard.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Business calendar rule: every calendar date and displayed time uses Pacific/Auckland, whatever the server
 * or host zone. Stored instants and API timestamps stay UTC; only calendar dates are converted here.
 */
public final class BusinessTime {
  public static final ZoneId ZONE = ZoneId.of("Pacific/Auckland");

  private BusinessTime() {}

  /** Auckland calendar date for the clock's current instant. */
  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE));
  }

  /** Auckland calendar date for an instant, such as a Xero timestamp in its UTC form. */
  public static LocalDate dateOf(Instant instant) {
    return instant.atZone(ZONE).toLocalDate();
  }
}
