package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.invoices.InvoiceStatus;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import java.util.*;
import org.springframework.http.HttpStatus;

/** A positive match is useful immediately; a negative requires independently verified search completeness. */
public final class InvoiceReferenceLookup implements QuoteSync.InvoiceLookup {
  private static final int MAX_PAGES = 100;
  private final XeroInvoiceClient client;
  private final boolean negativeEvidenceVerified;
  public InvoiceReferenceLookup(XeroInvoiceClient client, boolean negativeEvidenceVerified) {
    this.client = Objects.requireNonNull(client); this.negativeEvidenceVerified = negativeEvidenceVerified;
  }

  @Override public QuoteMirrorService.InvoiceMatch find(TenantAccess access, XeroQuoteClient.Quote quote) {
    var terms = new LinkedHashSet<String>();
    for (String term : new String[] {quote.reference(), quote.number()}) {
      if (term == null || term.isBlank()) continue;
      term = term.strip();
      if (term.length() > 200 || term.chars().anyMatch(Character::isISOControl)) return QuoteMirrorService.InvoiceMatch.UNCHECKED;
      terms.add(term);
    }
    if (terms.isEmpty()) return QuoteMirrorService.InvoiceMatch.UNCHECKED;
    for (String term : terms) {
      long total = -1; int pages = -1;
      Set<UUID> seen = new HashSet<>();
      for (int page = 1; page <= MAX_PAGES; page++) {
        var result = client.searchReference(access, term, page);
        if (total < 0) { total = result.itemCount(); pages = result.pageCount(); }
        else if (total != result.itemCount() || pages != result.pageCount()) throw incomplete();
        for (var invoice : result.items()) {
          if (!seen.add(invoice.id())) throw incomplete();
          if (invoice.status() != InvoiceStatus.VOIDED && invoice.status() != InvoiceStatus.DELETED
              && invoice.reference() != null && invoice.reference().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))
            return QuoteMirrorService.InvoiceMatch.FOUND;
        }
        if (page >= pages) break;
        if (page == MAX_PAGES) throw incomplete();
      }
    }
    return negativeEvidenceVerified ? QuoteMirrorService.InvoiceMatch.NOT_FOUND : QuoteMirrorService.InvoiceMatch.UNCHECKED;
  }

  private static ApiFailure incomplete() {
    return new ApiFailure(HttpStatus.CONFLICT, "INVOICE_LOOKUP_INCOMPLETE", "Invoice search could not be completed. Retry the sync.");
  }
}
