package com.airconcepts.dashboard.workitems.sync;

import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GreenLineSyncSchedulerTest {
  @Test void gl25_hourlyRunSkipsTheBusinessHoursAlreadyCoveredByTenMinuteCron() {
    var service = mock(GreenLineSyncService.class);
    // Monday 08:00 and 19:00 in Auckland (NZDT).
    var business = new GreenLineSyncScheduler(service, Clock.fixed(Instant.parse("2026-11-22T19:00:00Z"), ZoneOffset.UTC));
    business.offHours();
    verifyNoInteractions(service);
    business.businessHours();
    verify(service).runScheduled();
    var evening = new GreenLineSyncScheduler(service, Clock.fixed(Instant.parse("2026-11-23T06:00:00Z"), ZoneOffset.UTC));
    evening.offHours();
    verify(service, times(2)).runScheduled();
  }
}
