package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import java.time.Instant;
import java.util.Objects;

/** Documented reads only. A short page is not proof that the provider returned every quote. */
public final class DocumentedQuoteSource implements QuoteSync.Source {
  private final XeroQuoteClient client;
  public DocumentedQuoteSource(XeroQuoteClient client) { this.client = Objects.requireNonNull(client); }
  @Override public QuoteSync.Page page(TenantAccess access, XeroQuoteClient.Status status, int page, Instant modifiedSince) {
    var rows = client.page(access, page, status, modifiedSince);
    return new QuoteSync.Page(rows, rows.size() == 100 ? QuoteSync.Continuation.MORE : QuoteSync.Continuation.UNKNOWN);
  }
}
