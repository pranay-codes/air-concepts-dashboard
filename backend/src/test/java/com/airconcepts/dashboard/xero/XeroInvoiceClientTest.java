package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.web.ProviderFailure;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroInvoiceClientTest {
  private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.xero.com");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC);
  private final XeroInvoiceClient client = new XeroInvoiceClient(builder.build(), clock);
  private final TenantAccess access = new TenantAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "synthetic-access", java.util.Set.of("accounting.invoices.read"));
  private final UUID id = UUID.randomUUID();

  @Test void frUc15_08_26_sendsFullResultFiltersAndBoundedPageWithServerTenant() {
    server.expect(request -> {
      String uri = request.getURI().toString();
      assertThat(uri).startsWith("https://api.xero.com/api.xro/2.0/Invoices?")
          .contains("where=", "Contact.Name.Contains", "page=1", "pageSize=25", "order=");
    }).andExpect(header("xero-tenant-id", access.tenantId().toString()))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-access"))
        .andExpect(header(HttpHeaders.ACCEPT, "application/json"))
        .andRespond(withSuccess(page(invoice("ACCREC")), MediaType.APPLICATION_JSON));
    var q = new InvoiceQuery(InvoiceType.ACCREC, "Acme", null, false, InvoiceQuery.DateField.INVOICE_DATE,
        null, null, InvoiceQuery.Sort.DATE, InvoiceQuery.Direction.DESC, 1, 25);
    var result = client.list(access, q);
    assertThat(result.itemCount()).isEqualTo(1);
    assertThat(result.items()).singleElement().satisfies(i -> {
      assertThat(i.id()).isEqualTo(id);
      assertThat(i.total()).isEqualByComparingTo("115.50");
      assertThat(i.overdue()).isTrue();
      assertThat(i.status()).isEqualTo(InvoiceStatus.AUTHORISED);
    });
    server.verify();
  }

  @Test void gl19_searchesReferencesWithServerTenantAndValidatedPagination() {
    server.expect(request -> {
      assertThat(request.getURI().getQuery()).contains("SearchTerm=J-123", "page=1", "pageSize=100", "where=");
    }).andExpect(header("xero-tenant-id", access.tenantId().toString()))
        .andRespond(withSuccess(page(invoice("ACCREC")).replace("\"pageSize\":25", "\"pageSize\":100"), MediaType.APPLICATION_JSON));
    assertThat(client.searchReference(access, "J-123", 1).items()).singleElement()
        .satisfies(row -> assertThat(row.id()).isEqualTo(id));
    server.verify();
  }

  @Test void gl19_searchRejectsMissingPaginationRatherThanAssumingNoInvoice() {
    server.expect(anything()).andRespond(withSuccess("{\"Invoices\":[]}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.searchReference(access, "J-123", 1)).isInstanceOf(ProviderFailure.class);
  }

  @Test void gl19_searchRequiresScopeAndUsesSharedTenantPacer() {
    var pacedTenants = new java.util.ArrayList<UUID>();
    var pacedClient = new XeroInvoiceClient(builder.build(), clock, pacedTenants::add);
    var noScope = new TenantAccess(access.entityId(), access.tenantId(), access.grantId(), "synthetic", null);
    assertThatThrownBy(() -> pacedClient.searchReference(noScope, "J-123", 1))
        .isInstanceOfSatisfying(com.airconcepts.dashboard.web.ApiFailure.class,
            error -> assertThat(error.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
    assertThat(pacedTenants).isEmpty();
    server.expect(anything()).andRespond(withSuccess(page(invoice("ACCREC")).replace("\"pageSize\":25", "\"pageSize\":100"), MediaType.APPLICATION_JSON));
    pacedClient.searchReference(access, "J-123", 1);
    assertThat(pacedTenants).containsExactly(access.tenantId());
  }

  @Test void gl21_readsLinkedSalesInvoiceWithServerTenantAndPreservesSendFlag() {
    server.expect(request -> assertThat(request.getURI().getPath()).endsWith("/Invoices/"+id))
        .andExpect(header("xero-tenant-id",access.tenantId().toString()))
        .andRespond(withSuccess("{\"Invoices\":["+invoice("ACCREC").replace("\"Type\":\"ACCREC\"","\"Type\":\"ACCREC\",\"SentToContact\":true")+"]}",MediaType.APPLICATION_JSON));
    var result=client.workflowInvoice(access,id);
    assertThat(result.id()).isEqualTo(id);
    assertThat(result.sentToContact()).isTrue();
    assertThat(result.amountDue()).isNotNull();
    server.verify();
  }

  @Test void gl19_searchTermCannotInjectAdditionalQueryParameters() {
    server.expect(request -> {
      var uri = request.getURI().toString();
      assertThat(uri).contains("%26").doesNotContain("&page=999");
    }).andRespond(withSuccess(page(invoice("ACCREC")).replace("\"pageSize\":25", "\"pageSize\":100"), MediaType.APPLICATION_JSON));
    client.searchReference(access, "J-1&page=999", 1);
    server.verify();
  }

  @Test void frUc15_26_rejectsMissingOrInconsistentPagingMetadata() {
    for (String body : new String[] {"{\"Invoices\":[]}", page("").replace("\"itemCount\":1", "\"itemCount\":2")}) {
      server.reset(); server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertThatThrownBy(() -> client.list(access, InvoiceQuery.defaults(InvoiceType.ACCREC)))
          .isInstanceOf(ProviderFailure.class).hasMessageContaining("incomplete");
    }
  }

  @Test void frUc15_13_rejectsWrongDocumentTypeAndMismatchedDetailId() {
    server.expect(anything()).andRespond(withSuccess("{\"Invoices\":[" + invoice("ACCPAY") + "]}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.detail(access, InvoiceType.ACCREC, id)).isInstanceOf(ProviderFailure.class);
    server.reset(); server.expect(anything()).andRespond(withSuccess("{\"Invoices\":[" + invoice("ACCREC") + "]}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.detail(access, InvoiceType.ACCREC, UUID.randomUUID())).isInstanceOf(ProviderFailure.class);
  }

  @Test void frUc15_14_preservesLinesPaymentsAndMissingFinancialValues() {
    server.expect(anything()).andRespond(withSuccess("{\"Invoices\":[" + invoice("ACCREC") + "]}", MediaType.APPLICATION_JSON));
    var result = client.detail(access, InvoiceType.ACCREC, id);
    assertThat(result.lines()).singleElement().satisfies(line -> {
      assertThat(line.description()).isEqualTo("Service");
      assertThat(line.quantity()).isEqualByComparingTo("2");
    });
    assertThat(result.payments()).singleElement().satisfies(p -> assertThat(p.amount()).isEqualByComparingTo("10"));
    assertThat(result.amountPaid()).isNull();
    assertThat(result.taxBasis()).isEqualTo(InvoiceViews.TaxBasis.INCLUSIVE);
  }

  @Test void frUc15_14_xeroDatesUseTheAucklandCalendarDate() {
    // Local midnight on 16 November in Auckland (NZDT, +13:00) is 15 November 11:00 UTC. The business date is 16 November.
    var localMidnight = java.time.Instant.parse("2026-11-15T11:00:00Z").toEpochMilli();
    var body = invoice("ACCREC").replace("/Date(1793491200000+0000)/", "/Date(" + localMidnight + "+1300)/");
    server.expect(anything()).andRespond(withSuccess("{\"Invoices\":[" + body + "]}", MediaType.APPLICATION_JSON));
    assertThat(client.detail(access, InvoiceType.ACCREC, id).payments()).singleElement()
        .satisfies(p -> assertThat(p.date()).isEqualTo(java.time.LocalDate.of(2026, 11, 16)));
  }

  @Test void frUc15_11_requestsFourDecimalUnitPricesForDetail() {
    server.expect(request -> assertThat(request.getURI().getQuery()).isEqualTo("unitdp=4"))
        .andRespond(withSuccess("{\"Invoices\":[" + invoice("ACCREC").replace("\"UnitAmount\":50", "\"UnitAmount\":50.1234") + "]}", MediaType.APPLICATION_JSON));
    var result = client.detail(access, InvoiceType.ACCREC, id);
    assertThat(result.lines()).singleElement().satisfies(line ->
        assertThat(line.unitAmount()).isEqualByComparingTo("50.1234"));
    server.verify();
  }

  @Test void frUc15_30_providerFailuresAreSafeAndPreserveStatusAndRetryAfter() {
    server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, "60").body("private provider body synthetic-access"));
    assertThatThrownBy(() -> client.list(access, InvoiceQuery.defaults(InvoiceType.ACCREC)))
        .isInstanceOfSatisfying(ProviderFailure.class, e -> {
          assertThat(e.upstreamStatus()).isEqualTo(429);
          assertThat(e.retryAfterSeconds()).isEqualTo(60);
          assertThat(e.getMessage()).doesNotContain("private", "synthetic-access");
          assertThat(e.getCause()).isNull();
        });
  }

  @Test void frUc15_30_malformedMoneyDatesAndJsonNeverBecomeSuccessfulEmptyResults() {
    for (String body : new String[] {"not json", page(invoice("ACCREC").replace("115.50", "\"n/a\"")),
        page(invoice("ACCREC").replace("2026-11-01", "invalid"))}) {
      server.reset(); server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertThatThrownBy(() -> client.list(access, InvoiceQuery.defaults(InvoiceType.ACCREC))).isInstanceOf(ProviderFailure.class);
    }
  }

  private String page(String rows) {
    return "{\"pagination\":{\"page\":1,\"pageSize\":25,\"pageCount\":1,\"itemCount\":1},\"Invoices\":[" + rows + "]}";
  }

  @Test void frUc15_08_pageTwoRetainsFullResultCountWithoutFetchingOtherPages() {
    server.expect(request -> assertThat(request.getURI().toString()).contains("page=2"))
        .andRespond(withSuccess(page(invoice("ACCREC")).replace("\"page\":1", "\"page\":2")
            .replace("\"pageCount\":1", "\"pageCount\":2").replace("\"itemCount\":1", "\"itemCount\":26"), MediaType.APPLICATION_JSON));
    var q = new InvoiceQuery(InvoiceType.ACCREC, "", null, false, InvoiceQuery.DateField.INVOICE_DATE,
        null, null, InvoiceQuery.Sort.DATE, InvoiceQuery.Direction.DESC, 2, 25);
    var result = client.list(access, q);
    assertThat(result.page()).isEqualTo(2);
    assertThat(result.itemCount()).isEqualTo(26);
    assertThat(result.items()).hasSize(1);
    server.verify();
  }

  @Test void frUc15_30_permissionServerAndNetworkErrorsNeverReturnSuccessOrRawDiagnostics() {
    for (HttpStatus status : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.INTERNAL_SERVER_ERROR}) {
      server.reset(); server.expect(anything()).andRespond(withStatus(status).body("private provider detail"));
      assertThatThrownBy(() -> client.list(access, InvoiceQuery.defaults(InvoiceType.ACCREC)))
          .isInstanceOfSatisfying(ProviderFailure.class, e -> {
            assertThat(e.upstreamStatus()).isEqualTo(status.value());
            assertThat(e.getMessage()).doesNotContain("private provider detail");
            assertThat(e.getCause()).isNull();
          });
    }
    server.reset(); server.expect(anything()).andRespond(request -> { throw new java.net.SocketTimeoutException("private diagnostic"); });
    assertThatThrownBy(() -> client.list(access, InvoiceQuery.defaults(InvoiceType.ACCREC)))
        .isInstanceOfSatisfying(ProviderFailure.class, e -> {
          assertThat(e.upstreamStatus()).isZero();
          assertThat(e.getCause()).isNull();
        });
  }
  private String invoice(String type) {
    return """
        {"InvoiceID":"%s","Type":"%s","InvoiceNumber":"INV-1","Reference":"ref",
         "Contact":{"ContactID":"%s","Name":"Acme"},"Status":"AUTHORISED",
         "DateString":"2026-11-01T00:00:00","DueDateString":"2026-11-01T00:00:00",
         "Total":115.50,"AmountDue":105.50,"SubTotal":100,"TotalTax":15.50,"CurrencyCode":"NZD",
         "LineAmountTypes":"Inclusive","LineItems":[{"Description":"Service","Quantity":2,"UnitAmount":50,"LineAmount":100}],
         "Payments":[{"PaymentID":"%s","Date":"/Date(1793491200000+0000)/","Amount":10,"Reference":"Part payment"}]}
        """.formatted(id, type, UUID.randomUUID(), UUID.randomUUID());
  }
}
