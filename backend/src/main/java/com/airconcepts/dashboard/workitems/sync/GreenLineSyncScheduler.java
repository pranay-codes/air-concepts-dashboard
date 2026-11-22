package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.time.BusinessTime;
import java.time.Clock;
import java.time.DayOfWeek;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Opt-in only until provider verification is complete. */
@Component
@ConditionalOnProperty(name = "dashboard.green-line.sync.enabled", havingValue = "true")
public class GreenLineSyncScheduler {
  private final GreenLineSyncService service;
  private final Clock clock;
  public GreenLineSyncScheduler(GreenLineSyncService service, Clock clock) { this.service = service; this.clock = clock; }
  @Scheduled(cron = "${dashboard.green-line.sync.business-cron:0 */10 7-18 * * MON-FRI}", zone = "Pacific/Auckland")
  public void businessHours() { service.runScheduled(); }
  @Scheduled(cron = "${dashboard.green-line.sync.off-hours-cron:0 0 * * * *}", zone = "Pacific/Auckland")
  public void offHours() {
    var local = clock.instant().atZone(BusinessTime.ZONE);
    boolean weekday = local.getDayOfWeek() != DayOfWeek.SATURDAY && local.getDayOfWeek() != DayOfWeek.SUNDAY;
    if (!weekday || local.getHour() < 7 || local.getHour() >= 19) service.runScheduled();
  }
}
