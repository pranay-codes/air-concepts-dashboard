package com.airconcepts.dashboard.vwork;

/** Server-only credential. Never include its value in logs, DTOs or diagnostics. */
public final class VworkCredential {
  private final String value;
  public VworkCredential(String value) {
    if (value == null || value.isBlank() || value.strip().length() > 512) throw new IllegalArgumentException("Invalid vWork credential");
    this.value = value.strip();
  }
  String value() { return value; }
  @Override public String toString() { return "VworkCredential[redacted]"; }
}
