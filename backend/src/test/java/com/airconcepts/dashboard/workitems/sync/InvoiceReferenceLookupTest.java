package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.xero.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class InvoiceReferenceLookupTest {
  private final XeroInvoiceClient client = mock(XeroInvoiceClient.class);
  private final TenantAccess access = new TenantAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
      "synthetic", Set.of("accounting.invoices.read"));
  private final XeroQuoteClient.Quote quote = new XeroQuoteClient.Quote(UUID.randomUUID(), "Q-1", "J-1",
      XeroQuoteClient.Status.ACCEPTED, null, null, null, null, Instant.EPOCH, null, null, null, List.of());
  private InvoiceViews.Summary invoice(String reference, InvoiceStatus status) {
    return new InvoiceViews.Summary(UUID.randomUUID(), InvoiceType.ACCREC, "INV-1", reference, "Synthetic",
        LocalDate.of(2026, 1, 1), null, null, null, null, status, false, null);
  }
  private InvoiceViews.Page page(InvoiceViews.Summary... rows) {
    return new InvoiceViews.Page(List.of(rows), 1, 100, 1, rows.length);
  }

  @Test void gl19_matchByReferencePreventsBackfill() {
    when(client.searchReference(access, "J-1", 1)).thenReturn(page(invoice("J-1 / Q-1", InvoiceStatus.AUTHORISED)));
    assertThat(new InvoiceReferenceLookup(client, false).find(access, quote)).isEqualTo(QuoteMirrorService.InvoiceMatch.FOUND);
  }

  @Test void gl19_emptyOrVoidedResultsAreNotProofOfAbsenceWithoutProviderVerification() {
    when(client.searchReference(any(), anyString(), eq(1))).thenReturn(page(invoice("J-1", InvoiceStatus.VOIDED)));
    assertThat(new InvoiceReferenceLookup(client, false).find(access, quote)).isEqualTo(QuoteMirrorService.InvoiceMatch.UNCHECKED);
    assertThat(new InvoiceReferenceLookup(client, true).find(access, quote)).isEqualTo(QuoteMirrorService.InvoiceMatch.NOT_FOUND);
    verify(client, times(2)).searchReference(access, "Q-1", 1);
  }

  @Test void gl19_invoiceNumberOnlyMatchesAreNotReferenceMatches() {
    when(client.searchReference(any(), anyString(), eq(1))).thenReturn(page(invoice("unrelated", InvoiceStatus.AUTHORISED)));
    assertThat(new InvoiceReferenceLookup(client, true).find(access, quote)).isEqualTo(QuoteMirrorService.InvoiceMatch.NOT_FOUND);
  }

  @Test void gl19_readsLaterPagesAndFailsIfTheirCountsChange() {
    var first = invoice("unrelated", InvoiceStatus.AUTHORISED);
    var match = invoice("job J-1", InvoiceStatus.DRAFT);
    when(client.searchReference(access, "J-1", 1)).thenReturn(new InvoiceViews.Page(List.of(first), 1, 100, 2, 101));
    when(client.searchReference(access, "J-1", 2)).thenReturn(new InvoiceViews.Page(List.of(match), 2, 100, 2, 101));
    assertThat(new InvoiceReferenceLookup(client, false).find(access, quote)).isEqualTo(QuoteMirrorService.InvoiceMatch.FOUND);
    when(client.searchReference(access, "J-1", 2)).thenReturn(new InvoiceViews.Page(List.of(match), 2, 100, 3, 201));
    assertThatThrownBy(() -> new InvoiceReferenceLookup(client, false).find(access, quote))
        .isInstanceOfSatisfying(com.airconcepts.dashboard.web.ApiFailure.class,
            failure -> assertThat(failure.code()).isEqualTo("INVOICE_LOOKUP_INCOMPLETE"));
  }
}
