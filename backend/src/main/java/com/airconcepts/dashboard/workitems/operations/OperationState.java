package com.airconcepts.dashboard.workitems.operations;

/** Conservative transition rules. Callers must persist each change under the entity and item locks. */
public final class OperationState {
  public enum Status { PENDING, UNKNOWN, FAILED, SUCCEEDED }
  public enum Result { CREATED, LINKED_EXISTING, NOT_FOUND }
  private OperationState() {}

  public static Status unknown(Status previous) {
    require(previous == Status.PENDING);
    return Status.UNKNOWN;
  }
  /** Only a provider response known not to have applied the write can be a definite failure. */
  public static Status failed(Status previous) {
    require(previous == Status.PENDING);
    return Status.FAILED;
  }
  public static Result created(Status previous, String externalId) {
    require(previous == Status.PENDING);
    requireId(externalId);
    return Result.CREATED;
  }
  public static Result linked(Status previous, String externalId) {
    require(previous == Status.PENDING || previous == Status.UNKNOWN);
    requireId(externalId);
    return Result.LINKED_EXISTING;
  }
  /** A partial, ambiguous or unavailable lookup never proves a write did not happen. */
  public static Result notFound(Status previous, boolean lookupComplete) {
    require(previous == Status.UNKNOWN && lookupComplete);
    return Result.NOT_FOUND;
  }
  public static int retryAttempt(Status previous, int attempt) {
    require(previous == Status.FAILED && attempt >= 1 && attempt < 20);
    return attempt + 1;
  }
  private static void require(boolean condition) {
    if (!condition) throw new IllegalStateException("Operation transition is not allowed.");
  }
  private static void requireId(String value) {
    if (value == null || value.isBlank() || value.length() > 255) throw new IllegalArgumentException("A confirmed external ID is required.");
  }
}
