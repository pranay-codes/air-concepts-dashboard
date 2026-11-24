package com.airconcepts.dashboard.config;

import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.invoices.InvoiceStatus;
import com.airconcepts.dashboard.workitems.operations.InvoiceGateway;
import com.airconcepts.dashboard.workitems.sync.*;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class GreenLineDocumentedInvoiceSyncTest {
  @Test void gl_status_02_documentedSentIndicatorIsPassedToTheMirror() {
    var entity=UUID.randomUUID(); var tenant=UUID.randomUUID(); var grant=UUID.randomUUID();
    var item=UUID.randomUUID(); var invoiceId=UUID.randomUUID();
    var access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read"));
    var repository=mock(InvoiceMirrorRepository.class); var mirror=mock(InvoiceMirrorService.class);
    var client=mock(XeroInvoiceClient.class); var gateway=mock(InvoiceGateway.class);
    var invoice=new XeroInvoiceClient.WorkflowInvoice(invoiceId,"INV-1",InvoiceStatus.AUTHORISED,
        BigDecimal.TEN,BigDecimal.TEN,null,true,"QU-42");
    when(repository.candidates(entity)).thenReturn(List.of(new InvoiceMirrorRepository.Candidate(item,invoiceId)));
    when(client.workflowInvoice(access,invoiceId)).thenReturn(invoice);
    new GreenLineSyncConfig().greenLineInvoiceSync(repository,mirror,client,gateway).run(entity,() -> access);
    verify(mirror).apply(new QuoteMirrorService.Mapping(entity,tenant,grant),item,invoice,true);
    verify(repository).unlinkedAllOpen(entity);
    verify(repository,never()).paidCandidates(entity);
  }
  /** GL-STATUS-02/04: invoice matching runs for open items at all times; it is not behind a switch. */
  @Test void gl_status_02_invoiceMatchingRunsForOpenItemsAtAllTimes() {
    var entity=UUID.randomUUID(); var tenant=UUID.randomUUID(); var grant=UUID.randomUUID();
    var access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read"));
    var repository=mock(InvoiceMirrorRepository.class); var mirror=mock(InvoiceMirrorService.class);
    var client=mock(XeroInvoiceClient.class); var gateway=mock(InvoiceGateway.class);
    new GreenLineSyncConfig().greenLineInvoiceSync(repository,mirror,client,gateway).run(entity,() -> access);
    verify(repository).unlinkedAllOpen(entity);
    verify(repository,never()).unlinked(entity);
  }
}
