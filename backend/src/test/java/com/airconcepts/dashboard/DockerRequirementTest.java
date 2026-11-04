package com.airconcepts.dashboard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/** QA-001, AC-7. Docker-backed tests must fail in CI when Docker is missing, and skip locally. */
class DockerRequirementTest {

  @Test
  void dockerAvailableAllowsTestsToRun() {
    assertThatCode(() -> DockerRequirement.check(true, true)).doesNotThrowAnyException();
    assertThatCode(() -> DockerRequirement.check(false, true)).doesNotThrowAnyException();
  }

  @Test
  void missingDockerInCiFailsInsteadOfSkipping() {
    assertThatThrownBy(() -> DockerRequirement.check(true, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CI")
        .hasMessageContaining("Docker")
        .isNotInstanceOf(TestAbortedException.class);
  }

  @Test
  void missingDockerLocallySkipsTheTest() {
    assertThatThrownBy(() -> DockerRequirement.check(false, false))
        .isInstanceOf(TestAbortedException.class)
        .hasMessageContaining("Docker");
  }
}
