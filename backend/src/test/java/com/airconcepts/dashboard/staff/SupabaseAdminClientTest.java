package com.airconcepts.dashboard.staff;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.airconcepts.dashboard.web.ApiFailure;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseAdminClientTest {
  private final UUID id = UUID.randomUUID();
  private final String base = "https://auth.example.invalid/auth/v1";
  private final RestClient.Builder builder = RestClient.builder().baseUrl(base);
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final SupabaseAdminClient client = new SupabaseAdminClient(builder.build(), "synthetic-admin-key");

  @Test
  void frUc15_16_reconcilesExistingIdentityWithoutRepeatingExternalCreate() {
    server.expect(requestTo(base + "/admin/users/" + id)).andExpect(method(HttpMethod.GET))
        .andExpect(header("apikey", "synthetic-admin-key"))
        .andRespond(withSuccess(identity(id, "staff@example.invalid"), MediaType.APPLICATION_JSON));
    client.ensureUser(id, "staff@example.invalid");
    server.verify();
  }

  @Test
  void frUc15_16_createsOnlyThePersistedIdentityWithNoPasswordOrDashboardRoleClaim() {
    server.expect(requestTo(base + "/admin/users/" + id)).andRespond(withResourceNotFound());
    server.expect(requestTo(base + "/admin/users")).andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"id\":\"" + id + "\",\"email\":\"staff@example.invalid\",\"email_confirm\":true}", true))
        .andRespond(withSuccess(identity(id, "staff@example.invalid"), MediaType.APPLICATION_JSON));
    client.ensureUser(id, "staff@example.invalid");
    server.verify();
  }

  @Test
  void frUc15_16_conflictingProviderIdentityNeverGrantsAccess() {
    server.expect(anything()).andRespond(withSuccess(identity(id, "different@example.invalid"), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.ensureUser(id, "staff@example.invalid"))
        .isInstanceOf(ApiFailure.class).hasMessageContaining("identity");
    server.verify();
  }

  @Test
  void frUc15_18_banAndUnbanUseAdminOnlyAndVerifyIdentity() {
    server.expect(requestTo(base + "/admin/users/" + id)).andExpect(method(HttpMethod.PUT))
        .andExpect(content().json("{\"ban_duration\":\"876000h\"}", true))
        .andRespond(withSuccess(identity(id, "staff@example.invalid"), MediaType.APPLICATION_JSON));
    server.expect(requestTo(base + "/admin/users/" + id)).andExpect(method(HttpMethod.PUT))
        .andExpect(content().json("{\"ban_duration\":\"none\"}", true))
        .andRespond(withSuccess(identity(id, "staff@example.invalid"), MediaType.APPLICATION_JSON));
    client.setActive(id, false);
    client.setActive(id, true);
    server.verify();
  }

  @Test
  void frUc15_16_providerFailureIsExplicitAndNeverExposesPrivateBodyOrCredential() {
    server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN)
        .body("private-provider-detail").contentType(MediaType.TEXT_PLAIN));
    assertThatThrownBy(() -> client.ensureUser(id, "staff@example.invalid"))
        .isInstanceOf(ApiFailure.class).hasMessageNotContaining("private-provider-detail")
        .hasMessageNotContaining("synthetic-admin-key");
    server.verify();
  }

  @Test
  void frUc15_16_missingConfigurationFailsClosedBeforeAnyProviderRequest() {
    var unconfigured = new SupabaseAdminClient(builder.build(), "");
    assertThatThrownBy(() -> unconfigured.ensureUser(id, "staff@example.invalid"))
        .isInstanceOf(ApiFailure.class).hasMessageContaining("configured");
    server.verify();
  }

  private static String identity(UUID id, String email) {
    return "{\"id\":\"" + id + "\",\"email\":\"" + email + "\"}";
  }
}
