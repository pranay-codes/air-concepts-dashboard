package com.airconcepts.dashboard.web;

import org.springframework.http.HttpStatus;

/** Contains only safe business text and stable codes, never a raw provider response. */
public class ApiFailure extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public ApiFailure(HttpStatus status, String code, String detail) {
    super(detail);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() { return status; }
  public String code() { return code; }
}
