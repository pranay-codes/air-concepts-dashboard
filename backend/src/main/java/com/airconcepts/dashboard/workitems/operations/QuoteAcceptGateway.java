package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroQuoteWriteClient;
import java.util.UUID;

/** Provider boundary for accepting a quote. Unavailable unless a provider mode is explicitly enabled. */
public interface QuoteAcceptGateway {
  boolean available();
  XeroQuoteWriteClient.Receipt accept(TenantAccess access, UUID quoteId, UUID operation);
}
