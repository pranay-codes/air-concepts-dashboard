package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionViewsTest {
  @Test
  void frUc15_04_maskedTenantShowsOnlyFirstAndLastFourCharacters() {
    var tenant = UUID.fromString("b41e1234-5678-9abc-def0-1122334c7c21");
    var masked = ConnectionViews.maskTenant(tenant);
    assertThat(masked).isEqualTo("b41e…7c21");
    assertThat(masked).doesNotContain("5678").doesNotContain(tenant.toString());
  }
}
