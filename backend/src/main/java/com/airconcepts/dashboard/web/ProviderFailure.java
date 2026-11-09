package com.airconcepts.dashboard.web;

import org.springframework.http.HttpStatus;

/** Safe provider diagnostic. A zero status means no response was confirmed. */
public class ProviderFailure extends ApiFailure {
  private final int upstreamStatus;
  private final Integer retryAfterSeconds;

  public ProviderFailure(String code, String detail, int upstreamStatus) {
    this(code, detail, upstreamStatus, null);
  }

  public ProviderFailure(String code, String detail, int upstreamStatus, Integer retryAfterSeconds) {
    super(HttpStatus.BAD_GATEWAY, code, detail);
    this.upstreamStatus = upstreamStatus;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int upstreamStatus() { return upstreamStatus; }
  public Integer retryAfterSeconds() { return retryAfterSeconds; }
}
