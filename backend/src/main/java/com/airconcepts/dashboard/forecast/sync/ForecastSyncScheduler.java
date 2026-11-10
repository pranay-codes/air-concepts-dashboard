package com.airconcepts.dashboard.forecast.sync;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="dashboard.forecast.sync.enabled",havingValue="true",matchIfMissing=true)
public class ForecastSyncScheduler {
  private final ForecastSyncService service;
  public ForecastSyncScheduler(ForecastSyncService service) {this.service=service;}
  @Scheduled(cron="0 30 4 * * *",zone="Pacific/Auckland")
  public void sync() {service.runScheduled();}
}
