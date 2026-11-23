package com.airconcepts.dashboard.xero;

import com.airconcepts.dashboard.web.ProviderFailure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Clock;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;

/**
 * Documented quote status update boundary: sets an existing quote to ACCEPTED. Callers must reserve an
 * operation before invoking it and must treat any failure that is not a confirmed rejection as unknown.
 * Not wired to a live bean until the Xero write is verified in a demo organisation.
 */
public final class XeroQuoteWriteClient {
  public record Receipt(UUID id, XeroQuoteClient.Status status) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Envelope(@JsonProperty("Quotes") List<Quote> quotes) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Quote(@JsonProperty("QuoteID") UUID id, @JsonProperty("Status") String status) {}
  private record Payload(@JsonValue Map<String, Object> value) {
    @Override public String toString() { return "QuotePayload[redacted]"; }
  }

  private final RestClient api;
  private final Clock clock;
  private final XeroForecastClient.Pacer pacer;
  private final boolean idempotencyVerified;

  public XeroQuoteWriteClient(RestClient api, Clock clock, XeroForecastClient.Pacer pacer, boolean idempotencyVerified) {
    this.api = Objects.requireNonNull(api);
    this.clock = Objects.requireNonNull(clock);
    this.pacer = Objects.requireNonNull(pacer);
    this.idempotencyVerified = idempotencyVerified;
  }

  public Receipt accept(TenantAccess access, UUID quoteId, UUID operation) {
    Objects.requireNonNull(access);
    Objects.requireNonNull(quoteId);
    Objects.requireNonNull(operation);
    XeroScopes.require(access.grantedScopes(), XeroScopes.INVOICES_WRITE);
    pacer.await(access.tenantId());
    try {
      var request = api.post().uri("/api.xro/2.0/Quotes/{id}", quoteId)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + access.accessToken())
          .header("xero-tenant-id", access.tenantId().toString())
          .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
      if (idempotencyVerified) request.header("Idempotency-Key", operation.toString());
      var payload = Map.<String, Object>of("Quotes", List.of(Map.of("QuoteID", quoteId, "Status", "ACCEPTED")));
      var response = XeroReadErrors.call(() -> request.body(new Payload(payload)).retrieve().body(Envelope.class), clock);
      if (response == null || response.quotes() == null || response.quotes().size() != 1) throw XeroReadErrors.malformed();
      var quote = response.quotes().getFirst();
      if (quote == null || !quoteId.equals(quote.id()) || !"ACCEPTED".equals(quote.status())) throw XeroReadErrors.malformed();
      return new Receipt(quoteId, XeroQuoteClient.Status.ACCEPTED);
    } catch (ProviderFailure failure) {
      throw new ProviderFailure(failure.code(), "The quote outcome could not be confirmed. Review it in Xero before retrying.",
          failure.upstreamStatus(), failure.retryAfterSeconds());
    }
  }
}
