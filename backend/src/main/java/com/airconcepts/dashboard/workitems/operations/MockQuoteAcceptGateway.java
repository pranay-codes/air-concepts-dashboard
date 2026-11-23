package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import com.airconcepts.dashboard.xero.XeroQuoteWriteClient;
import java.util.Objects;
import java.util.UUID;

/** Explicit development-only acceptance provider. It confirms the requested quote without contacting Xero. */
public final class MockQuoteAcceptGateway implements QuoteAcceptGateway {
  @Override public boolean available() { return true; }
  @Override public XeroQuoteWriteClient.Receipt accept(TenantAccess access, UUID quoteId, UUID operation) {
    Objects.requireNonNull(access);
    Objects.requireNonNull(quoteId);
    return new XeroQuoteWriteClient.Receipt(quoteId, XeroQuoteClient.Status.ACCEPTED);
  }
}
