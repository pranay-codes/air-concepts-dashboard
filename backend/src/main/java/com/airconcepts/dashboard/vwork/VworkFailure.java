package com.airconcepts.dashboard.vwork;

/** Safe failure without a provider body, request URI, credential or underlying exception. */
public final class VworkFailure extends RuntimeException {
  private final String code;
  private final int upstreamStatus;
  private final Integer retryAfterSeconds;
  public VworkFailure(String code, String message, int upstreamStatus, Integer retryAfterSeconds) {
    super(message); this.code = code; this.upstreamStatus = upstreamStatus; this.retryAfterSeconds = retryAfterSeconds;
  }
  public String code() { return code; }
  public int upstreamStatus() { return upstreamStatus; }
  public Integer retryAfterSeconds() { return retryAfterSeconds; }
}
