package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.invoices.InvoiceStatus;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Searches documented invoice reference results; an empty result never authorises a write. */
public final class ManualInvoiceLookup {
  private static final int MAX_PAGES=100;
  private static final int EARLY_MAX_PAGES=2;
  private final XeroInvoiceClient client;
  private final boolean budgeted;
  public ManualInvoiceLookup(XeroInvoiceClient client) { this(client,false); }
  public ManualInvoiceLookup(XeroInvoiceClient client, boolean budgeted) { this.client=Objects.requireNonNull(client); this.budgeted=budgeted; }
  public Set<UUID> find(TenantAccess access, String jobId, String quoteNumber) {
    var terms=new LinkedHashSet<String>();
    for (String term : new String[]{jobId,quoteNumber}) {
      if (term==null || term.isBlank()) continue;
      term=term.strip();
      if (term.length()>200 || term.chars().anyMatch(Character::isISOControl)) throw incomplete();
      terms.add(term);
    }
    var matches=new LinkedHashSet<UUID>();
    for (String term : terms) {
      long count=-1; int pages=-1; var seen=new HashSet<UUID>();
      int maxPages=budgeted ? EARLY_MAX_PAGES : MAX_PAGES;
      for (int page=1; page<=maxPages; page++) {
        var result=budgeted ? client.searchReference(access,term,page,true) : client.searchReference(access,term,page);
        if (result==null) throw incomplete();
        if (count<0) { count=result.itemCount(); pages=result.pageCount(); }
        else if (count!=result.itemCount() || pages!=result.pageCount()) throw incomplete();
        for (var invoice : result.items()) {
          if (invoice.id()==null || !seen.add(invoice.id())) throw incomplete();
          if (invoice.status()!=InvoiceStatus.VOIDED && invoice.status()!=InvoiceStatus.DELETED
              && invoice.reference()!=null && invoice.reference().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))
            matches.add(invoice.id());
        }
        if (page>=pages) break;
        if (page==maxPages) throw incomplete();
      }
    }
    return Set.copyOf(matches);
  }
  private static ApiFailure incomplete() { return new ApiFailure(HttpStatus.CONFLICT,"INVOICE_LOOKUP_INCOMPLETE","Invoice search could not be completed. Retry the sync."); }
}
