package com.airconcepts.dashboard.config;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.workitems.sync.*;
import com.airconcepts.dashboard.xero.*;
import com.airconcepts.dashboard.vwork.VworkConnectionService;
import com.airconcepts.dashboard.workitems.operations.InvoiceGateway;
import com.airconcepts.dashboard.workitems.operations.MockQuoteAcceptGateway;
import com.airconcepts.dashboard.workitems.operations.QuoteAcceptGateway;
import com.airconcepts.dashboard.workitems.operations.MockInvoiceGateway;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Sync wiring; scheduled execution is separately opt-in. */
@Configuration
public class GreenLineSyncConfig {
  @Bean InvoiceGateway invoiceGateway(@Value("${dashboard.green-line.xero.invoice-mode:disabled}") String mode, Environment environment) {
    if ("mock".equals(mode) && environment.acceptsProfiles(Profiles.of("local","test"))) return new MockInvoiceGateway();
    if (!"disabled".equals(mode)) throw new IllegalArgumentException("Unsupported invoice provider mode");
    return new InvoiceGateway() {
      public boolean available() { return false; }
      public Search find(TenantAccess access, String jobId, String quoteNumber) { throw new IllegalStateException("Invoice provider disabled"); }
      public XeroInvoiceClient.WorkflowInvoice create(TenantAccess access, DraftInvoice draft, java.util.UUID operation) {
        throw new IllegalStateException("Invoice provider disabled");
      }
    };
  }
  /** Quote acceptance is off unless a mock mode is chosen for local or test profiles; live Xero writes stay disabled. */
  @Bean QuoteAcceptGateway quoteAcceptGateway(@Value("${dashboard.green-line.xero.quote-accept-mode:disabled}") String mode, Environment environment) {
    if ("mock".equals(mode) && environment.acceptsProfiles(Profiles.of("local","test"))) return new MockQuoteAcceptGateway();
    if (!"disabled".equals(mode)) throw new IllegalArgumentException("Unsupported quote accept mode");
    return new QuoteAcceptGateway() {
      public boolean available() { return false; }
      public com.airconcepts.dashboard.xero.XeroQuoteWriteClient.Receipt accept(TenantAccess access, java.util.UUID quoteId, java.util.UUID operation) {
        throw new IllegalStateException("Quote accept provider disabled");
      }
    };
  }
  @Bean QuoteSync greenLineQuoteSync(QuoteMirrorService mirror, XeroQuoteClient quotes, XeroInvoiceClient invoices, InvoiceGateway gateway) {
    if (gateway instanceof MockInvoiceGateway mock && quotes instanceof MockXeroQuoteClient) {
      QuoteSync.Source source=(access,status,page,modified) -> {
        var rows=quotes.page(access,page,status,modified);
        return new QuoteSync.Page(rows,rows.size()==100?QuoteSync.Continuation.MORE:QuoteSync.Continuation.COMPLETE);
      };
      QuoteSync.InvoiceLookup lookup=(access,quote) -> mock.find(access,quote.reference(),quote.number()).matches().isEmpty()
          ? QuoteMirrorService.InvoiceMatch.NOT_FOUND : QuoteMirrorService.InvoiceMatch.FOUND;
      return new QuoteSync(mirror,source,lookup);
    }
    return new QuoteSync(mirror, new DocumentedQuoteSource(quotes), new InvoiceReferenceLookup(invoices, false));
  }

  @Bean InvoiceSync greenLineInvoiceSync(InvoiceMirrorRepository repository, InvoiceMirrorService mirror, XeroInvoiceClient invoices,
      InvoiceGateway gateway, @Value("${dashboard.green-line.xero.early-invoice-lookup:false}") boolean earlyInvoiceLookup) {
    if (gateway instanceof MockInvoiceGateway mock) return new InvoiceSync(repository,mirror,mock::read,true,
        (access,jobId,quoteNumber) -> mock.find(access,jobId,quoteNumber).matches().stream()
            .map(XeroInvoiceClient.WorkflowInvoice::id).collect(java.util.stream.Collectors.toUnmodifiableSet()),true);
    // Xero documents SentToContact as the indicator shown as sent in its interface.
    return new InvoiceSync(repository,mirror,invoices::workflowInvoice,true,new ManualInvoiceLookup(invoices)::find,earlyInvoiceLookup,false,true);
  }

  @Bean GreenLineSyncService greenLineSyncService(GreenLineSyncRunRepository repository, QuoteSync quotes,
      XeroConnectionService connections, EntityService entities, Clock clock, VworkSync vwork, VworkConnectionService vworkConnections,
      InvoiceSync invoices) {
    return new GreenLineSyncService(repository, quotes, connections, entities, clock, vwork, vworkConnections,invoices);
  }
}
