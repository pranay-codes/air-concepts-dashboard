package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.web.ProviderFailure;
import java.time.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroQuoteWriteClientTest {
  private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.xero.com");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC);
  private final UUID quoteId = UUID.randomUUID(), operation = UUID.randomUUID();
  private final TenantAccess access = new TenantAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
      "synthetic-token", Set.of("accounting.invoices"));
  private final XeroQuoteWriteClient client = new XeroQuoteWriteClient(builder.build(), clock, tenant -> {}, false);

  private String accepted(String status) {
    return "{\"Quotes\":[{\"QuoteID\":\"" + quoteId + "\",\"Status\":\"" + status + "\"}]}";
  }

  @Test void gl08_postsAcceptedStatusToTheQuoteInTheItemsTenantAndReturnsConfirmation() {
    server.expect(requestTo("https://api.xero.com/api.xro/2.0/Quotes/" + quoteId))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("xero-tenant-id", access.tenantId().toString()))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-token"))
        .andExpect(content().json("{\"Quotes\":[{\"QuoteID\":\"" + quoteId + "\",\"Status\":\"ACCEPTED\"}]}"))
        .andRespond(withSuccess(accepted("ACCEPTED"), MediaType.APPLICATION_JSON));
    var receipt = client.accept(access, quoteId, operation);
    assertThat(receipt.id()).isEqualTo(quoteId);
    assertThat(receipt.status()).isEqualTo(XeroQuoteClient.Status.ACCEPTED);
    server.verify();
  }

  @Test void gl22_idempotencyHeaderIsNotSentUntilTheWriteIsVerified() {
    server.expect(requestTo("https://api.xero.com/api.xro/2.0/Quotes/" + quoteId))
        .andExpect(headerDoesNotExist("Idempotency-Key"))
        .andRespond(withSuccess(accepted("ACCEPTED"), MediaType.APPLICATION_JSON));
    client.accept(access, quoteId, operation);
    server.verify();
  }

  @Test void gl08_responseThatDoesNotConfirmAcceptanceIsTreatedAsUnconfirmed() {
    server.expect(anything()).andRespond(withSuccess(accepted("SENT"), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.accept(access, quoteId, operation))
        .isInstanceOfSatisfying(ProviderFailure.class, failure -> {
          assertThat(failure.code()).isEqualTo("XERO_INVALID_RESPONSE");
          assertThat(failure.upstreamStatus()).isEqualTo(200);
        });
  }

  @Test void gl22_responseForAnotherQuoteIsRejectedAsUnconfirmed() {
    server.expect(anything()).andRespond(withSuccess(
        "{\"Quotes\":[{\"QuoteID\":\"" + UUID.randomUUID() + "\",\"Status\":\"ACCEPTED\"}]}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.accept(access, quoteId, operation)).isInstanceOf(ProviderFailure.class);
  }

  @Test void gl22_providerRejectionKeepsItsStatusForAConfirmedNonWrite() {
    server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> client.accept(access, quoteId, operation))
        .isInstanceOfSatisfying(ProviderFailure.class, failure -> assertThat(failure.upstreamStatus()).isEqualTo(400));
  }

  @Test void gl08_missingWriteScopeFailsBeforeAnyRequest() {
    var readOnly = new TenantAccess(access.entityId(), access.tenantId(), access.grantId(), "synthetic-token",
        Set.of("accounting.invoices.read"));
    assertThatThrownBy(() -> client.accept(readOnly, quoteId, operation))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
    server.verify();
  }
}
