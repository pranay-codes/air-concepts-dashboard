package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ProviderFailure;

public class XeroProviderFailure extends ProviderFailure {
  private final boolean reconnect, uncertain;
  public XeroProviderFailure(String code, String detail, int status, boolean reconnect, boolean uncertain, Integer retryAfter) {
    super(code, detail, status, retryAfter);
    this.reconnect = reconnect;
    this.uncertain = uncertain;
  }
  public boolean requiresReconnect() { return reconnect; }
  public boolean uncertain() { return uncertain; }
}
