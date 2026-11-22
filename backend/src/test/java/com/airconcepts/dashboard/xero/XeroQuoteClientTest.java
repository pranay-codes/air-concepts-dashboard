package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.airconcepts.dashboard.web.ProviderFailure;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroQuoteClientTest {
  private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.xero.com");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);
  private final TenantAccess access = new TenantAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "synthetic-token");
  private final XeroQuoteClient client = new XeroQuoteClient(builder.build(), clock, tenant -> {});
  private final UUID quoteId = UUID.randomUUID(), contactId = UUID.randomUUID();

  @Test void gl01_pageUsesDocumentedSingleStatusAndRetainsQuoteLines() {
    server.expect(requestTo("https://api.xero.com/api.xro/2.0/Quotes?page=2&Status=ACCEPTED"))
        .andExpect(header("xero-tenant-id", access.tenantId().toString()))
        .andExpect(header("If-Modified-Since", "2026-11-18T00:00:00"))
        .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
    var result = client.page(access, 2, XeroQuoteClient.Status.ACCEPTED, Instant.parse("2026-11-18T00:00:00Z"));
    assertThat(result).singleElement().satisfies(q -> {
      assertThat(q.id()).isEqualTo(quoteId);
      assertThat(q.reference()).isEqualTo("J-123");
      assertThat(q.contactId()).isEqualTo(contactId);
      assertThat(q.status()).isEqualTo(XeroQuoteClient.Status.ACCEPTED);
      assertThat(q.total()).isEqualByComparingTo("115.00");
      assertThat(q.lines()).singleElement().satisfies(line -> {
        assertThat(line.description()).isEqualTo("Synthetic work");
        assertThat(line.quantity()).isEqualByComparingTo("2.00");
        assertThat(line.unitAmount()).isEqualByComparingTo("50.00");
        assertThat(line.tracking()).singleElement().satisfies(track -> assertThat(track.option()).isEqualTo("North"));
      });
    });
    server.verify();
  }

  @Test void gl03_blankReferenceIsNullAndLongReferenceIsNotTruncated() {
    server.expect(anything()).andRespond(withSuccess(body().replace(" J-123 ", "   "), MediaType.APPLICATION_JSON));
    assertThat(client.page(access, 1, null, null).getFirst().reference()).isNull();
    server.reset();
    var longReference = "x".repeat(300);
    server.expect(anything()).andRespond(withSuccess(body().replace(" J-123 ", longReference), MediaType.APPLICATION_JSON));
    assertThat(client.page(access, 1, null, null).getFirst().reference()).isEqualTo(longReference);
  }

  @Test void gl01_pageWithoutInvoiceStylePaginationAndEmptyPageAreAccepted() {
    server.expect(anything()).andRespond(withSuccess("{\"Quotes\":[]}", MediaType.APPLICATION_JSON));
    assertThat(client.page(access, 3, null, null)).isEmpty();
  }

  @Test void gl01_detailMustReturnRequestedQuote() {
    server.expect(requestTo("https://api.xero.com/api.xro/2.0/Quotes/" + quoteId))
        .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
    assertThat(client.detail(access, quoteId).id()).isEqualTo(quoteId);
    server.reset(); server.expect(anything()).andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.detail(access, UUID.randomUUID())).isInstanceOf(ProviderFailure.class);
  }
  @Test void gl17_quoteDetailRetainsDocumentedBrandingTheme() {
    var theme=UUID.randomUUID();
    server.expect(anything()).andRespond(withSuccess(body().replace("\"QuoteNumber\":\"Q-123\"",
        "\"QuoteNumber\":\"Q-123\",\"BrandingThemeID\":\""+theme+"\""),MediaType.APPLICATION_JSON));
    assertThat(client.detail(access,quoteId).brandingThemeId()).isEqualTo(theme);
  }

  @Test void gl01_malformedAndProviderFailuresAreSafe() {
    server.expect(anything()).andRespond(withSuccess("{\"Quotes\":[{}]}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.page(access, 1, null, null)).isInstanceOfSatisfying(ProviderFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("XERO_INVALID_RESPONSE"));
    server.reset(); server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", "60").body("private synthetic body"));
    assertThatThrownBy(() -> client.page(access, 1, null, null)).isInstanceOfSatisfying(ProviderFailure.class, failure -> {
      assertThat(failure.code()).isEqualTo("XERO_RATE_LIMITED");
      assertThat(failure.retryAfterSeconds()).isEqualTo(60);
      assertThat(failure.getMessage()).doesNotContain("private", "synthetic");
      assertThat(failure.getCause()).isNull();
    });
  }

  @Test void gl01_authAndProviderErrorsUseSafeExistingMappings() {
    for (var entry : java.util.Map.of(HttpStatus.UNAUTHORIZED, "XERO_RECONNECT_REQUIRED",
        HttpStatus.FORBIDDEN, "XERO_ORGANISATION_ACCESS_DENIED", HttpStatus.BAD_GATEWAY, "XERO_PROVIDER_FAILED").entrySet()) {
      server.reset();
      server.expect(anything()).andRespond(withStatus(entry.getKey()).body("private synthetic body"));
      assertThatThrownBy(() -> client.page(access, 1, null, null)).isInstanceOfSatisfying(ProviderFailure.class, failure -> {
        assertThat(failure.code()).isEqualTo(entry.getValue());
        assertThat(failure.getMessage()).doesNotContain("private", "synthetic");
        assertThat(failure.getCause()).isNull();
      });
    }
  }

  @Test void gl01_duplicateQuoteIdsAndWrongStatusAreRejected() {
    var row = body().substring(body().indexOf('[') + 1, body().lastIndexOf(']'));
    server.expect(anything()).andRespond(withSuccess("{\"Quotes\":[" + row + "," + row + "]}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.page(access, 1, null, null)).isInstanceOfSatisfying(ProviderFailure.class,
        failure -> assertThat(failure.getMessage()).contains("duplicate QuoteID"));
    server.reset(); server.expect(anything()).andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.page(access, 1, XeroQuoteClient.Status.SENT, null))
        .isInstanceOfSatisfying(ProviderFailure.class, failure -> assertThat(failure.code()).isEqualTo("XERO_INVALID_RESPONSE"));
  }

  @Test void gl17_missingLineItemsAreNotMisrepresentedAsAnEmptyQuote() {
    var result = "{\"Quotes\":[{\"QuoteID\":\"" + quoteId + "\",\"QuoteNumber\":\"Q-1\","
        + "\"Status\":\"SENT\",\"UpdatedDateUTC\":\"2026-11-18T00:00:00\"}]}";
    server.expect(anything()).andRespond(withSuccess(result, MediaType.APPLICATION_JSON));
    assertThat(client.page(access, 1, null, null).getFirst().lines()).isNull();
  }

  private String body() {
    return """
        {"Quotes":[{"QuoteID":"%s","QuoteNumber":"Q-123","Reference":" J-123 ","Status":"ACCEPTED",
          "Contact":{"ContactID":"%s","Name":"Synthetic customer"},"DateString":"2026-11-10T00:00:00",
          "ExpiryDateString":"2026-12-10T00:00:00","UpdatedDateUTC":"/Date(1794960000000)/",
          "CurrencyCode":"NZD","Total":115.00,"LineAmountTypes":"EXCLUSIVE",
          "LineItems":[{"Description":"Synthetic work","Quantity":2.00,"UnitAmount":50.00,
             "AccountCode":"200","TaxType":"OUTPUT2","DiscountRate":0,
             "Tracking":[{"Name":"Region","Option":"North"}]}]}]}
        """.formatted(quoteId, contactId);
  }
}
