package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.airconcepts.dashboard.invoices.InvoiceType;
import com.airconcepts.dashboard.web.ProviderFailure;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroForecastClientTest {
  private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.xero.com");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);
  private final XeroForecastClient client = new XeroForecastClient(builder.build(), clock, tenant -> {});
  private final TenantAccess access = new TenantAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "synthetic");
  private final UUID id = UUID.randomUUID();

  @Test void frUc04_01_expectedPaymentDateAndExactOutstandingQuery() {
    server.expect(r -> assertThat(r.getURI().getQuery()).contains("Type==\"ACCREC\"", "Status==\"AUTHORISED\"",
        "AmountDue>0", "UpdatedDateUTC ASC,InvoiceID ASC", "summaryOnly=true", "pageSize=100"))
        .andExpect(header("xero-tenant-id", access.tenantId().toString()))
        .andRespond(withSuccess(page(invoice("ACCREC"), 1), MediaType.APPLICATION_JSON)
            .header("X-DayLimit-Remaining", "999"));
    var result = client.page(access, InvoiceType.ACCREC, 1, null);
    assertThat(result.documents()).singleElement().satisfies(d -> {
      assertThat(d.expectedDate()).isEqualTo(LocalDate.of(2026, 11, 15));
      assertThat(d.amountDue()).isEqualByComparingTo("115.00");
    });
    assertThat(result.limits().day()).isEqualTo(999);
    server.verify();
  }

  @Test void frUc04_02_plannedPaymentDateForBillsAndIncrementalHeader() {
    server.expect(anything()).andExpect(header("If-Modified-Since", "2026-11-08T00:00:00"))
        .andRespond(withSuccess(page(invoice("ACCPAY"), 1), MediaType.APPLICATION_JSON));
    assertThat(client.page(access, InvoiceType.ACCPAY, 1, Instant.parse("2026-11-08T00:00:00Z"))
        .documents().getFirst().expectedDate()).isEqualTo(LocalDate.of(2026, 11, 20));
  }

  @Test void frUc04_01_xeroDatesUseTheAucklandCalendarDate() {
    // Local midnight on 16 November in Auckland (NZDT, +13:00) is 15 November 11:00 UTC. The business date is 16 November.
    var localMidnight = Instant.parse("2026-11-15T11:00:00Z").toEpochMilli();
    var body = invoice("ACCREC").replace("/Date(1794700800000+0000)/", "/Date(" + localMidnight + "+1300)/");
    server.expect(anything()).andRespond(withSuccess(page(body, 1), MediaType.APPLICATION_JSON));
    assertThat(client.page(access, InvoiceType.ACCREC, 1, null).documents().getFirst().expectedDate())
        .isEqualTo(LocalDate.of(2026, 11, 16));
  }

  @Test void frUc04_01_rejectsMalformedPaginationDuplicateIdsAndWrongType() {
    var cases = Map.of("{\"Invoices\":[]}", "pagination missing",
        page(invoice("ACCREC"), 2), "row count differs from pagination itemCount",
        page(invoice("ACCPAY"), 1), "Type differs from the requested type",
        page(invoice("ACCREC") + "," + invoice("ACCREC"), 2), "duplicate InvoiceID in page",
        page(invoice("ACCREC").replace("\"Status\":\"AUTHORISED\"", "\"Status\":\"PAID\""), 1), "document is not outstanding",
        page(invoice("ACCREC").replace("\"AmountDue\":115.00", "\"AmountDue\":115.005"), 1), "AmountDue has more than two decimals",
        page(invoice("ACCREC").replace("/Date(1794268800000+0000)/", "private-synthetic"), 1), "UpdatedDateUTC unparseable",
        page(invoice("ACCREC").replace("115.00", "\"bad\""), 1), "body unreadable at $.Invoices[0].Total");
    cases.forEach((body, reason) -> {
      server.reset(); server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertThatThrownBy(() -> client.page(access, InvoiceType.ACCREC, 1, null))
          .isInstanceOfSatisfying(ProviderFailure.class, f -> {
            assertThat(f.code()).isEqualTo("XERO_INVALID_RESPONSE");
            // The reason says which check failed and never repeats a provider value.
            assertThat(f.getMessage()).contains(reason).doesNotContain("bad", "private-synthetic", "115.005", "Synthetic");
          });
    });
  }

  @Test void frUc04_01_updatedDateUtcAcceptsIsoTextAsUtc() {
    var body = invoice("ACCREC").replace("/Date(1794268800000+0000)/", "2026-11-09T11:00:00");
    server.expect(anything()).andRespond(withSuccess(page(body, 1), MediaType.APPLICATION_JSON));
    assertThat(client.page(access, InvoiceType.ACCREC, 1, null).documents().getFirst().updatedAt())
        .isEqualTo(Instant.parse("2026-11-09T11:00:00Z"));
  }

  @Test void frUc04_17_settlementDetailChecksIdAndType() {
    server.expect(anything()).andRespond(withSuccess("{\"Invoices\":[" + invoice("ACCREC") + "]}", MediaType.APPLICATION_JSON));
    assertThat(client.detail(access, InvoiceType.ACCREC, id).id()).isEqualTo(id);
    server.reset(); server.expect(anything()).andRespond(withSuccess("{\"Invoices\":[" + invoice("ACCREC") + "]}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.detail(access, InvoiceType.ACCREC, UUID.randomUUID())).isInstanceOf(ProviderFailure.class);
  }

  @Test void frUc04_17_rateLimitAndFailureNeverExposeProviderBodyOrToken() {
    for (HttpStatus status : List.of(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR)) {
      server.reset(); server.expect(anything()).andRespond(withStatus(status).header("Retry-After", "60").body("private synthetic"));
      assertThatThrownBy(() -> client.page(access, InvoiceType.ACCREC, 1, null))
          .isInstanceOfSatisfying(ProviderFailure.class, f -> {
            assertThat(f.upstreamStatus()).isEqualTo(status.value());
            assertThat(f.getMessage()).doesNotContain("private", "synthetic"); assertThat(f.getCause()).isNull();
            if (status.value() == 429) assertThat(f.retryAfterSeconds()).isEqualTo(60);
          });
    }
  }

  private String page(String rows, int count) {
    return "{\"pagination\":{\"page\":1,\"pageSize\":100,\"pageCount\":1,\"itemCount\":" + count + "},\"Invoices\":[" + rows + "]}";
  }
  private String invoice(String type) {
    return """
        {"InvoiceID":"%s","Type":"%s","InvoiceNumber":"INV-1","Contact":{"Name":"Synthetic"},
        "DateString":"2026-11-01","DueDateString":"2026-11-10","CurrencyCode":"NZD","Status":"AUTHORISED",
        "Total":115.00,"TotalTax":15,"AmountDue":115.00,"AmountPaid":0,"AmountCredited":0,
        "UpdatedDateUTC":"/Date(1794268800000+0000)/",
        "ExpectedPaymentDate":"/Date(1794700800000+0000)/","PlannedPaymentDate":"/Date(1795132800000+0000)/"}
        """.formatted(id, type);
  }
}
