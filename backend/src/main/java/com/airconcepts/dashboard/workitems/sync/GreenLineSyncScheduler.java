package com.airconcepts.dashboard.workitems.sync;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically checks entity-specific, opt-in schedules stored in the database. */
@Component
@ConditionalOnProperty(name = "dashboard.green-line.sync.enabled", havingValue = "true", matchIfMissing = true)
public class GreenLineSyncScheduler {
  private final GreenLineSyncService service;
  public GreenLineSyncScheduler(GreenLineSyncService service) { this.service = service; }
  @Scheduled(fixedDelay = 60000)
  public void checkDue() { service.runScheduledDue(); }
}
