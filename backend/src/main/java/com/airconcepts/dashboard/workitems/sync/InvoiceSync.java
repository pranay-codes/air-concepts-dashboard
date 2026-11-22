package com.airconcepts.dashboard.workitems.sync;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroInvoiceClient;
import com.airconcepts.dashboard.xero.XeroScopes;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/** Refreshes linked sales invoices using a server-resolved Xero mapping. */
public final class InvoiceSync {
  public interface Source { XeroInvoiceClient.WorkflowInvoice read(TenantAccess access, UUID invoice); }
  public interface Lookup { Set<UUID> find(TenantAccess access, String jobId, String quoteNumber); }
  private final InvoiceMirrorRepository repository;
  private final InvoiceMirrorService mirror;
  private final Source source;
  private final boolean sentVerified;
  private final Lookup lookup;
  public InvoiceSync(InvoiceMirrorRepository repository, InvoiceMirrorService mirror, Source source, boolean sentVerified) {
    this(repository,mirror,source,sentVerified,null);
  }
  public InvoiceSync(InvoiceMirrorRepository repository, InvoiceMirrorService mirror, Source source, boolean sentVerified, Lookup lookup) {
    this.repository=repository; this.mirror=mirror; this.source=source; this.sentVerified=sentVerified; this.lookup=lookup;
  }
  public int run(UUID entity, Supplier<TenantAccess> resolver) {
    var original=check(entity,resolver.get(),null);
    int seen=0;
    for (var candidate : repository.candidates(entity)) {
      var current=check(entity,resolver.get(),original);
      var invoice=source.read(current,candidate.invoiceId());
      if (invoice==null || !candidate.invoiceId().equals(invoice.id())) throw changed();
      current=check(entity,resolver.get(),original);
      mirror.apply(new QuoteMirrorService.Mapping(entity,current.tenantId(),current.grantId()),candidate.itemId(),invoice,sentVerified);
      seen++;
    }
    if (lookup!=null) for (var candidate : repository.unlinked(entity)) {
      var current=check(entity,resolver.get(),original);
      var matches=lookup.find(current,candidate.jobId(),candidate.quoteNumber());
      current=check(entity,resolver.get(),original);
      if (matches==null || matches.stream().anyMatch(Objects::isNull)) throw changed();
      var mapping=new QuoteMirrorService.Mapping(entity,current.tenantId(),current.grantId());
      if (matches.size()>1) { mirror.multiple(mapping,candidate); continue; }
      if (matches.isEmpty()) continue;
      var id=matches.iterator().next();
      var invoice=source.read(current,id);
      current=check(entity,resolver.get(),original);
      if (invoice==null || !id.equals(invoice.id()) || !matchesReference(invoice.reference(),candidate)) throw changed();
      mirror.link(new QuoteMirrorService.Mapping(entity,current.tenantId(),current.grantId()),candidate,invoice,sentVerified);
      seen++;
    }
    check(entity,resolver.get(),original);
    return seen;
  }
  private static boolean matchesReference(String reference, InvoiceMirrorRepository.Unlinked candidate) {
    if (reference==null) return false;
    String text=reference.toLowerCase(Locale.ROOT);
    for (var term : new String[]{candidate.jobId(),candidate.quoteNumber()})
      if (term!=null && !term.isBlank() && text.contains(term.strip().toLowerCase(Locale.ROOT))) return true;
    return false;
  }
  private static TenantAccess check(UUID entity, TenantAccess current, TenantAccess original) {
    if (current==null || !entity.equals(current.entityId()) || current.tenantId()==null || current.grantId()==null
        || (original!=null && (!original.tenantId().equals(current.tenantId()) || !original.grantId().equals(current.grantId())))) throw changed();
    XeroScopes.require(current.grantedScopes(),XeroScopes.QUOTES_READ);
    return current;
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"INVOICE_SYNC_CONNECTION_CHANGED","The Xero invoice or connection changed. Retry sync."); }
}
