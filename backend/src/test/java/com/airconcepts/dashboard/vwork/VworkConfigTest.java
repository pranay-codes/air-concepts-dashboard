package com.airconcepts.dashboard.vwork;

import static org.assertj.core.api.Assertions.assertThat;

import com.airconcepts.dashboard.config.VworkConfig;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VworkConfigTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(VworkConfig.class).withBean(Clock.class, Clock::systemUTC)
      .withBean(VworkConnectionRepository.class, () -> org.mockito.Mockito.mock(VworkConnectionRepository.class));

  @Test void providerIsDisabledUnlessExplicitlySelected() {
    context.run(application -> assertThat(application).doesNotHaveBean(VworkClient.class));
  }
  @Test void mockAndLiveAreSeparateExplicitModes() {
    context.withPropertyValues("dashboard.vwork.mode=mock")
        .run(application -> assertThat(application.getBean(VworkClient.class)).isInstanceOf(MockVworkClient.class));
    context.withPropertyValues("dashboard.vwork.mode=live")
        .run(application -> assertThat(application.getBean(VworkClient.class)).isInstanceOf(VworkLiveClient.class));
  }
}
