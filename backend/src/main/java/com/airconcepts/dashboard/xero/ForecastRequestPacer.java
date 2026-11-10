package com.airconcepts.dashboard.xero;

import java.util.*;
import org.springframework.stereotype.Component;

/** Shared across manual and scheduled runs. 1.25s spacing permits at most 49 starts per rolling minute. */
@Component
public class ForecastRequestPacer implements XeroForecastClient.Pacer {
  public static final long REQUEST_SPACING_NANOS = 1_250_000_000L;
  private final Map<UUID, Long> next = new HashMap<>();
  @Override public synchronized void await(UUID tenant) {
    long now = System.nanoTime(), wait = next.getOrDefault(tenant, now) - now;
    if (wait > 0) {
      try { Thread.sleep(java.time.Duration.ofNanos(wait)); }
      catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw XeroReadErrors.unavailable(); }
    }
    next.put(tenant, System.nanoTime() + REQUEST_SPACING_NANOS);
  }
}
