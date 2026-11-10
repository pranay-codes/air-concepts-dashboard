package com.airconcepts.dashboard.config;

import com.airconcepts.dashboard.xero.*;
import java.net.http.HttpClient;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class XeroConfig {
  @Bean RestClient xeroAccountingRestClient() {
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    var requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(Duration.ofSeconds(20));
    return RestClient.builder().baseUrl("https://api.xero.com").requestFactory(requests).build();
  }
  @Bean XeroInvoiceClient xeroInvoiceClient(RestClient xeroAccountingRestClient, Clock clock) { return new XeroInvoiceClient(xeroAccountingRestClient, clock); }
  // TEMPORARY: set DASHBOARD_XERO_FORECAST_DIAGNOSTICS=true locally to log forecast response shapes (remove after the investigation).
  @Bean XeroForecastClient xeroForecastClient(RestClient xeroAccountingRestClient, Clock clock, ForecastRequestPacer pacer,
      @Value("${dashboard.xero.forecast-diagnostics:false}") boolean diagnostics) {
    return new XeroForecastClient(xeroAccountingRestClient, clock, pacer, diagnostics);
  }
  @Bean XeroAttachmentClient xeroAttachmentClient(RestClient xeroAccountingRestClient, Clock clock) { return new XeroAttachmentClient(xeroAccountingRestClient, clock); }
  /** Instants are unchanged; a zone-less LocalDate.now(clock) therefore reads the business calendar, Auckland. */
  @Bean Clock dashboardClock() { return Clock.system(com.airconcepts.dashboard.time.BusinessTime.ZONE); }
  @Bean TokenCipher xeroTokenCipher(@Value("${XERO_TOKEN_ENCRYPTION_KEY:}") String key) { return new TokenCipher(key); }
  @Bean XeroSettings xeroSettings(@Value("${XERO_CLIENT_ID:}") String clientId,
      @Value("${XERO_CLIENT_SECRET:}") String secret, @Value("${XERO_REDIRECT_URI:}") String redirect,
      @Value("${XERO_FRONTEND_CALLBACK_URI:}") String frontendCallback) {
    return new XeroSettings(clientId, secret, redirect, frontendCallback);
  }
  @Bean XeroOAuthClient xeroOAuthClient(XeroSettings settings, Clock clock) {
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    var requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(Duration.ofSeconds(20));
    return new XeroOAuthClient(RestClient.builder().baseUrl("https://identity.xero.com").requestFactory(requests).build(),
        RestClient.builder().baseUrl("https://api.xero.com").requestFactory(requests).build(), settings, clock);
  }
}
