package com.airconcepts.dashboard.config;

import com.airconcepts.dashboard.xero.*;
import com.airconcepts.dashboard.workitems.operations.InvoiceGateway;
import com.airconcepts.dashboard.workitems.operations.MockInvoiceGateway;
import java.net.http.HttpClient;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class XeroConfig {
  /** Request diagnostics are off unless dashboard.diagnostics.xero.enabled=true (Test environment only). */
  @Bean RestClient xeroAccountingRestClient(
      @Value("${dashboard.diagnostics.xero.enabled:false}") boolean diagnostics,
      @Value("${dashboard.diagnostics.xero.include-bodies:false}") boolean includeBodies,
      @Value("${dashboard.diagnostics.xero.max-body-chars:4000}") int maxBodyChars) {
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    var requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(Duration.ofSeconds(20));
    return RestClient.builder().baseUrl("https://api.xero.com").requestFactory(requests)
        .requestInterceptor(new XeroRequestDiagnostics(diagnostics, includeBodies, maxBodyChars)).build();
  }
  @Bean XeroInvoiceClient xeroInvoiceClient(RestClient xeroAccountingRestClient, Clock clock, ForecastRequestPacer pacer) { return new XeroInvoiceClient(xeroAccountingRestClient, clock, pacer); }
  @Bean XeroQuoteClient xeroQuoteClient(RestClient xeroAccountingRestClient, Clock clock, ForecastRequestPacer pacer, InvoiceGateway gateway) {
    return gateway instanceof MockInvoiceGateway ? new MockXeroQuoteClient(xeroAccountingRestClient,clock,pacer)
        : new XeroQuoteClient(xeroAccountingRestClient, clock, pacer);
  }
  @Bean XeroContactClient xeroContactClient(RestClient xeroAccountingRestClient, Clock clock, ForecastRequestPacer pacer, InvoiceGateway gateway) {
    return gateway instanceof MockInvoiceGateway ? new MockXeroContactClient(xeroAccountingRestClient,clock,pacer)
        : new XeroContactClient(xeroAccountingRestClient, clock, pacer);
  }
  @Bean XeroForecastClient xeroForecastClient(RestClient xeroAccountingRestClient, Clock clock, ForecastRequestPacer pacer) {
    return new XeroForecastClient(xeroAccountingRestClient, clock, pacer);
  }
  @Bean XeroAttachmentClient xeroAttachmentClient(RestClient xeroAccountingRestClient, Clock clock) { return new XeroAttachmentClient(xeroAccountingRestClient, clock); }
  /** Instants are unchanged; a zone-less LocalDate.now(clock) therefore reads the business calendar, Auckland. */
  @Bean Clock dashboardClock() { return Clock.system(com.airconcepts.dashboard.time.BusinessTime.ZONE); }
  @Bean TokenCipher xeroTokenCipher(@Value("${XERO_TOKEN_ENCRYPTION_KEY:}") String key) { return new TokenCipher(key); }
  @Bean XeroSettings xeroSettings(@Value("${XERO_CLIENT_ID:}") String clientId,
      @Value("${XERO_CLIENT_SECRET:}") String secret, @Value("${XERO_REDIRECT_URI:}") String redirect,
      @Value("${XERO_FRONTEND_CALLBACK_URI:}") String frontendCallback,
      @Value("${dashboard.green-line.xero.read-enabled:false}") boolean quoteWorkflow,
      @Value("${dashboard.green-line.xero.write-enabled:false}") boolean invoiceWrite,
      @Value("${dashboard.green-line.xero.attachments-write-enabled:false}") boolean attachmentWrite) {
    return new XeroSettings(clientId, secret, redirect, frontendCallback, quoteWorkflow, invoiceWrite, attachmentWrite);
  }
  @Bean XeroOAuthClient xeroOAuthClient(XeroSettings settings, Clock clock) {
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    var requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(Duration.ofSeconds(20));
    return new XeroOAuthClient(RestClient.builder().baseUrl("https://identity.xero.com").requestFactory(requests).build(),
        RestClient.builder().baseUrl("https://api.xero.com").requestFactory(requests).build(), settings, clock);
  }
}
