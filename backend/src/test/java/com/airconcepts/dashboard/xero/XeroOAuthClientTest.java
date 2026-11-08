package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class XeroOAuthClientTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);
  private final XeroSettings settings = new XeroSettings("client-id", "synthetic-client-secret",
      "https://api.example.invalid/api/v1/xero/callback", "https://app.example.invalid/settings/entities");
  private final RestClient.Builder identity = RestClient.builder().baseUrl("https://identity.xero.com");
  private final RestClient.Builder api = RestClient.builder().baseUrl("https://api.xero.com");
  private final MockRestServiceServer tokenServer = MockRestServiceServer.bindTo(identity).build();
  private final MockRestServiceServer apiServer = MockRestServiceServer.bindTo(api).build();
  private final XeroOAuthClient client = new XeroOAuthClient(identity.build(), api.build(), settings, clock);

  @Test
  void frUc15_10_authorizeUrlUsesReadScopesAndEncodesRedirectAndState() {
    var url = settings.authorizationUrl("opaque-state");
    assertThat(url).startsWith("https://login.xero.com/identity/connect/authorize?")
        .contains("response_type=code", "state=opaque-state", "offline_access", "accounting.invoices.read", "accounting.attachments.read")
        .doesNotContain("synthetic-client-secret");
    assertThat(settings.toString()).doesNotContain("synthetic-client-secret");
  }

  @Test
  void frUc15_10_exchangesCodeServerSideAndRedactsTokenRepresentation() {
    tokenServer.expect(requestTo("https://identity.xero.com/connect/token")).andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("code=synthetic-code")))
        .andRespond(withSuccess(tokenBody(), MediaType.APPLICATION_JSON));
    var tokens = client.exchange("synthetic-code");
    assertThat(tokens.expiresAt()).isEqualTo(clock.instant().plusSeconds(1800));
    assertThat(tokens.toString()).doesNotContain("synthetic-access", "synthetic-refresh");
    tokenServer.verify();
  }

  @Test
  void frUc15_10_refreshReturnsBothRotatedValues() {
    tokenServer.expect(anything()).andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
        .andRespond(withSuccess(tokenBody(), MediaType.APPLICATION_JSON));
    var tokens = client.refresh("previous-synthetic-refresh");
    assertThat(tokens.refreshToken()).isEqualTo("synthetic-refresh");
    tokenServer.verify();
  }

  @Test
  void frUc15_03_discoversMultipleAuthorisedTenantsWithoutHardCodedNames() {
    var connection = UUID.randomUUID(); var tenant = UUID.randomUUID();
    apiServer.expect(requestTo("https://api.xero.com/connections")).andExpect(header("Authorization", "Bearer synthetic-access"))
        .andRespond(withSuccess("[{\"id\":\"" + connection + "\",\"tenantId\":\"" + tenant
            + "\",\"tenantType\":\"ORGANISATION\",\"tenantName\":\"Additional business\"}]", MediaType.APPLICATION_JSON));
    assertThat(client.connections("synthetic-access")).containsExactly(new XeroTenant(connection, tenant, "Additional business"));
    apiServer.verify();
  }

  @Test
  void frUc15_10_invalidGrantRequiresReconnectionAndNeverEchoesProviderBody() {
    tokenServer.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"invalid_grant\",\"error_description\":\"private-provider-detail\"}"));
    assertThatThrownBy(() -> client.refresh("previous-synthetic-refresh"))
        .isInstanceOf(XeroProviderFailure.class).hasMessageNotContaining("private-provider-detail")
        .satisfies(e -> assertThat(((XeroProviderFailure) e).requiresReconnect()).isTrue());
  }

  @Test
  void frUc15_14_rateLimitRetainsRetryAfterAndRejectsMissingTokenData() {
    tokenServer.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));
    assertThatThrownBy(() -> client.refresh("previous-synthetic-refresh"))
        .isInstanceOf(XeroProviderFailure.class)
        .satisfies(e -> assertThat(((XeroProviderFailure) e).retryAfterSeconds()).isEqualTo(120));
    tokenServer.verify();
  }

  @Test
  void frUc15_10_malformedSuccessNeverBecomesAnEmptySuccessfulGrant() {
    tokenServer.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.exchange("synthetic-code")).isInstanceOf(XeroProviderFailure.class);
  }

  @Test
  void frUc15_10_controlCharactersInProviderTokensAreRejectedBeforeStorageOrHeaders() {
    tokenServer.expect(anything()).andRespond(withSuccess(tokenBody().replace("synthetic-access", "synthetic\\r\\nprivate"), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.exchange("synthetic-code")).isInstanceOf(XeroProviderFailure.class)
        .hasMessageNotContaining("private");
  }

  @Test
  void frUc15_14_invalidAppCredentialsRequireSetupRecoveryRatherThanRepeatedConsent() {
    tokenServer.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"invalid_client\",\"error_description\":\"private-provider-detail\"}"));
    assertThatThrownBy(() -> client.refresh("previous-synthetic-refresh")).isInstanceOf(XeroProviderFailure.class)
        .satisfies(e -> {
          assertThat(((XeroProviderFailure)e).requiresReconnect()).isFalse();
          assertThat(((XeroProviderFailure)e).code()).isEqualTo("XERO_CONFIGURATION_ERROR");
        }).hasMessageNotContaining("private-provider-detail");
  }

  @Test
  void frUc15_14_httpDateRetryAfterRoundsUpSoFractionalSecondsCannotRetryEarly() {
    var fractional = Clock.fixed(clock.instant().plusMillis(500), ZoneOffset.UTC);
    var timedClient = new XeroOAuthClient(identity.build(), api.build(), settings, fractional);
    tokenServer.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", "Fri, 6 Nov 2026 10:02:00 GMT"));
    assertThatThrownBy(() -> timedClient.refresh("previous-synthetic-refresh"))
        .satisfies(e -> assertThat(((XeroProviderFailure)e).retryAfterSeconds()).isEqualTo(120));
  }

  private String tokenBody() {
    return "{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"expires_in\":1800,\"token_type\":\"Bearer\"}";
  }
}
