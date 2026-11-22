package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.airconcepts.dashboard.web.ProviderFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroInvoiceWriteClientTest {
  private final RestClient.Builder builder=RestClient.builder().baseUrl("https://api.xero.com");
  private final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
  private final Clock clock=Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"),ZoneOffset.UTC);
  private final TenantAccess access=new TenantAccess(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"synthetic",Set.of("accounting.invoices"));
  private final XeroInvoiceWriteClient client=new XeroInvoiceWriteClient(builder.build(),clock,tenant -> {},false);
  private DraftInvoice draft() {
    var line=new DraftInvoice.Line("Synthetic work",new BigDecimal("2.5"),new BigDecimal("40.1234"),null,"200","OUTPUT2",new BigDecimal("5"),List.of());
    return new DraftInvoice(UUID.randomUUID(),"Synthetic customer","Q-1","J-1 / Q-1",LocalDate.of(2026,11,21),"NZD","Exclusive",null,new BigDecimal("100"),List.of(line));
  }
  @Test void gl17_putsPricePreservingDraftToTheResolvedTenant() {
    var id=UUID.randomUUID();
    server.expect(method(HttpMethod.PUT)).andExpect(requestTo("https://api.xero.com/api.xro/2.0/Invoices?unitdp=4"))
        .andExpect(header("xero-tenant-id",access.tenantId().toString()))
        .andExpect(headerDoesNotExist("Idempotency-Key"))
        .andExpect(content().json("{\"Invoices\":[{\"Type\":\"ACCREC\",\"Status\":\"DRAFT\",\"Reference\":\"J-1 / Q-1\",\"LineItems\":[{\"Quantity\":2.5,\"UnitAmount\":40.1234,\"AccountCode\":\"200\",\"TaxType\":\"OUTPUT2\",\"DiscountRate\":5}]}]}"))
        .andRespond(withSuccess("{\"Invoices\":[{\"InvoiceID\":\""+id+"\",\"Type\":\"ACCREC\",\"Status\":\"DRAFT\",\"HasErrors\":false}]}",MediaType.APPLICATION_JSON));
    assertThat(client.create(access,draft(),UUID.randomUUID()).id()).isEqualTo(id);
    server.verify();
  }
  @Test void gl22_ambiguousProviderResponseNeverBecomesSuccessOrLeaksBody() {
    server.expect(anything()).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("private synthetic body"));
    assertThatThrownBy(() -> client.create(access,draft(),UUID.randomUUID())).isInstanceOf(ProviderFailure.class)
        .hasMessageNotContaining("private").hasMessageNotContaining("synthetic");
  }
  @Test void gl22_successWithoutVerifiedDraftIdentityRemainsUnconfirmed() {
    server.expect(anything()).andRespond(withSuccess("{\"Invoices\":[{\"Type\":\"ACCREC\",\"Status\":\"DRAFT\"}]}",MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.create(access,draft(),UUID.randomUUID())).isInstanceOf(ProviderFailure.class)
        .hasMessageContaining("could not be confirmed");
  }
  @Test void gl17_preservesQuoteBrandingAndTrackingInTheWritePayload() {
    var category=UUID.randomUUID(); var option=UUID.randomUUID(); var brand=UUID.randomUUID(); var id=UUID.randomUUID();
    var tracking=new XeroQuoteClient.Tracking(category,option,"Region","North");
    var line=new DraftInvoice.Line("Work",BigDecimal.ONE,new BigDecimal("12.3456"),"ITEM","200","OUTPUT2",new BigDecimal("7.5"),List.of(tracking));
    var value=new DraftInvoice(UUID.randomUUID(),"Customer","Q-1","J-1 / Q-1",LocalDate.of(2026,11,21),"NZD","Exclusive",brand,BigDecimal.TEN,List.of(line));
    server.expect(content().json("{\"Invoices\":[{\"BrandingThemeID\":\""+brand+"\",\"LineItems\":[{\"UnitAmount\":12.3456,\"DiscountRate\":7.5,\"Tracking\":[{\"TrackingCategoryID\":\""+category+"\",\"TrackingOptionID\":\""+option+"\"}]}]}]}"))
        .andRespond(withSuccess("{\"Invoices\":[{\"InvoiceID\":\""+id+"\",\"Type\":\"ACCREC\",\"Status\":\"DRAFT\"}]}",MediaType.APPLICATION_JSON));
    assertThat(client.create(access,value,UUID.randomUUID()).id()).isEqualTo(id);
    server.verify();
  }
  @Test void gl22_idempotencyHeaderIsSentOnlyWhenExplicitlyVerified() {
    var operation=UUID.randomUUID(); var id=UUID.randomUUID();
    var verified=new XeroInvoiceWriteClient(builder.build(),clock,tenant -> {},true);
    server.expect(header("Idempotency-Key",operation.toString()))
        .andRespond(withSuccess("{\"Invoices\":[{\"InvoiceID\":\""+id+"\",\"Type\":\"ACCREC\",\"Status\":\"DRAFT\"}]}",MediaType.APPLICATION_JSON));
    assertThat(verified.create(access,draft(),operation).id()).isEqualTo(id);
  }
  @Test void gl22_rateLimitAndTimeoutAreNotAutomaticallyRetried() {
    server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER,"60"));
    assertThatThrownBy(() -> client.create(access,draft(),UUID.randomUUID())).isInstanceOfSatisfying(ProviderFailure.class,
        failure -> { assertThat(failure.upstreamStatus()).isEqualTo(429); assertThat(failure.retryAfterSeconds()).isEqualTo(60); });
    server.verify();
    server.reset();
    server.expect(anything()).andRespond(request -> { throw new java.net.SocketTimeoutException("private network detail"); });
    assertThatThrownBy(() -> client.create(access,draft(),UUID.randomUUID())).isInstanceOfSatisfying(ProviderFailure.class,
        failure -> { assertThat(failure.upstreamStatus()).isZero(); assertThat(failure.getMessage()).doesNotContain("private"); });
    server.verify();
  }
  @Test void gl06_missingWriteScopeStopsBeforeAnyRequest() {
    var readOnly=new TenantAccess(access.entityId(),access.tenantId(),access.grantId(),"synthetic",Set.of("accounting.invoices.read"));
    assertThatThrownBy(() -> client.create(readOnly,draft(),UUID.randomUUID())).isInstanceOf(com.airconcepts.dashboard.web.ApiFailure.class);
    server.verify();
  }
}
