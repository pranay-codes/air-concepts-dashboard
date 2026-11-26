package com.airconcepts.dashboard.workitems.sync;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

class GreenLineSyncSchedulerTest {
  @Test void glAutoSync01_schedulerChecksDatabaseForDueEntities() {
    var service = mock(GreenLineSyncService.class);
    var scheduler = new GreenLineSyncScheduler(service);
    scheduler.checkDue();
    verify(service).runScheduledDue();
  }
}
