package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.xero.*;
import java.util.*;

/** Provider boundary: an empty search only permits creation when complete is proven. */
public interface InvoiceGateway {
  record Search(List<XeroInvoiceClient.WorkflowInvoice> matches, boolean complete) {
    public Search { matches=List.copyOf(matches); }
  }
  boolean available();
  Search find(TenantAccess access, String jobId, String quoteNumber);
  XeroInvoiceClient.WorkflowInvoice create(TenantAccess access, DraftInvoice draft, UUID operation);
}
