package com.airconcepts.dashboard.workitems;

import java.util.Objects;

/** The quoted-stage subset of the workflow; callers persist changes and owner hand-offs atomically. */
public final class QuoteTransition {
  public enum Status { SENT, ACCEPTED, DECLINED, DELETED }

  public record Result(WorkItemStage stage, String closeReason, String closingNote,
      boolean assignServiceManager) {}

  private QuoteTransition() {}

  public static Result observe(WorkItemStage stage, Status status) {
    requireQuoted(stage);
    return switch (Objects.requireNonNull(status, "status")) {
      case SENT -> new Result(WorkItemStage.QUOTED, null, null, false);
      case ACCEPTED -> new Result(WorkItemStage.TO_SCHEDULE, null, null, true);
      case DECLINED -> new Result(WorkItemStage.CLOSED, "LOST", "Declined in Xero", false);
      case DELETED -> new Result(WorkItemStage.CLOSED, "LOST", "Deleted in Xero", false);
    };
  }

  public static Result closeLost(WorkItemStage stage, String reason) {
    requireQuoted(stage);
    if (reason == null || reason.isBlank() || reason.length() > 500) {
      throw new IllegalArgumentException("A closing reason of 1 to 500 characters is required");
    }
    return new Result(WorkItemStage.CLOSED, "LOST", reason, false);
  }

  private static void requireQuoted(WorkItemStage stage) {
    if (stage != WorkItemStage.QUOTED) throw new IllegalArgumentException("Item is not quoted");
  }
}
