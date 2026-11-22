package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ApiFailure;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Documented scopes only. Persisted grant evidence is checked separately before provider reads or writes. */
public final class XeroScopes {
  public static final String QUOTES_READ = "accounting.invoices.read";
  public static final String CONTACTS_READ = "accounting.contacts.read";
  public static final String INVOICES_WRITE = "accounting.invoices";
  public static final String ATTACHMENTS_WRITE = "accounting.attachments";
  private XeroScopes() {}

  public static Set<String> requested(boolean quoteWorkflow, boolean invoiceWrite, boolean attachmentWrite) {
    var scopes = new LinkedHashSet<String>();
    scopes.add("offline_access");
    scopes.add(QUOTES_READ);
    scopes.add("accounting.attachments.read");
    if (quoteWorkflow) scopes.add(CONTACTS_READ);
    if (invoiceWrite) scopes.add(INVOICES_WRITE);
    if (attachmentWrite) scopes.add(ATTACHMENTS_WRITE);
    return Collections.unmodifiableSet(scopes);
  }

  public static void require(Set<String> granted, String required) {
    if (required == null || granted == null || !granted.contains(required))
      throw new ApiFailure(HttpStatus.CONFLICT, "XERO_SCOPE_REQUIRED", "Ask a Director to reconnect Xero with the required permissions.");
  }
}
