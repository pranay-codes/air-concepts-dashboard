package com.airconcepts.dashboard;

import org.opentest4j.TestAbortedException;

/**
 * Docker-backed tests (Testcontainers) run in GitHub Actions, where CI=true and Docker is available.
 * Without Docker: CI fails loudly so a missing database test can never pass silently; a developer
 * machine skips the test with a reason.
 */
public final class DockerRequirement {

  private DockerRequirement() {
  }

  public static void check(boolean ci, boolean dockerAvailable) {
    if (dockerAvailable) {
      return;
    }
    if (ci) {
      throw new IllegalStateException(
          "Docker is not available in CI. Testcontainers tests must run here, not be skipped.");
    }
    throw new TestAbortedException("Docker is not available locally, so the Testcontainers test is skipped.");
  }
}
