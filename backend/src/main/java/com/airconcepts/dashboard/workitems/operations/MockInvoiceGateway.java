package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.invoices.InvoiceStatus;
import com.airconcepts.dashboard.xero.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit development-only in-memory invoice provider, isolated by synthetic tenant. */
public final class MockInvoiceGateway implements InvoiceGateway {
  private final Map<UUID,LinkedHashMap<UUID,XeroInvoiceClient.WorkflowInvoice>> invoices=new HashMap<>();
  @Override public boolean available() { return true; }
  @Override public synchronized Search find(TenantAccess access, String jobId, String quoteNumber) {
    var matches=new ArrayList<XeroInvoiceClient.WorkflowInvoice>();
    for (var invoice : invoices.getOrDefault(access.tenantId(),new LinkedHashMap<>()).values()) {
      var ref=invoice.reference()==null?"":invoice.reference().toLowerCase(Locale.ROOT);
      boolean job=jobId!=null && !jobId.isBlank() && ref.contains(jobId.strip().toLowerCase(Locale.ROOT));
      boolean quote=quoteNumber!=null && !quoteNumber.isBlank() && ref.contains(quoteNumber.strip().toLowerCase(Locale.ROOT));
      if (job || quote) matches.add(invoice);
    }
    return new Search(matches,true);
  }
  @Override public synchronized XeroInvoiceClient.WorkflowInvoice create(TenantAccess access, DraftInvoice draft, UUID operation) {
    var rows=invoices.computeIfAbsent(access.tenantId(),ignored -> new LinkedHashMap<>());
    if (rows.values().stream().anyMatch(invoice -> draft.reference().equalsIgnoreCase(invoice.reference())))
      throw new com.airconcepts.dashboard.web.ApiFailure(org.springframework.http.HttpStatus.CONFLICT,
          "INVOICE_ALREADY_EXISTS","A matching test invoice already exists.");
    int number=rows.size()+1;
    var id=UUID.nameUUIDFromBytes((access.tenantId()+":"+number).getBytes(StandardCharsets.UTF_8));
    var invoice=new XeroInvoiceClient.WorkflowInvoice(id,"MOCK-"+String.format(Locale.ROOT,"%06d",number),InvoiceStatus.DRAFT,
        draft.quoteTotal(),draft.quoteTotal(),null,false,draft.reference());
    rows.put(id,invoice);
    return invoice;
  }
  public synchronized XeroInvoiceClient.WorkflowInvoice read(TenantAccess access, UUID invoiceId) {
    var invoice=invoices.getOrDefault(access.tenantId(),new LinkedHashMap<>()).get(invoiceId);
    if (invoice==null) throw new com.airconcepts.dashboard.web.ApiFailure(org.springframework.http.HttpStatus.NOT_FOUND,
        "INVOICE_NOT_FOUND","The test invoice is unavailable.");
    return invoice;
  }
}
