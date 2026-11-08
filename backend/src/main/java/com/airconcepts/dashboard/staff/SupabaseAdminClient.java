package com.airconcepts.dashboard.staff;

import com.airconcepts.dashboard.web.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.client.*;

/** Fixed-origin server-side Auth administration. No request/response bodies or keys are logged. */
public class SupabaseAdminClient {
  private final RestClient client;
  private final String adminKey;

  public SupabaseAdminClient(RestClient client, String adminKey) {
    this.client = client;
    this.adminKey = adminKey;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AuthIdentity(UUID id, String email) {}

  public void ensureUser(UUID id, String email) {
    requireConfigured();
    AuthIdentity identity;
    try {
      identity = request(client.get().uri("/admin/users/{id}", id));
    } catch (ProviderFailure failure) {
      if (failure.upstreamStatus() != 404) throw failure;
      // The stable id was committed before this operation. Never retry an uncertain POST blindly.
      identity = request(client.post().uri("/admin/users").contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("id", id.toString(), "email", email, "email_confirm", true)));
    }
    if (identity == null || !id.equals(identity.id()) || identity.email() == null
        || !email.equalsIgnoreCase(identity.email())) {
      throw new ApiFailure(HttpStatus.CONFLICT, "AUTH_IDENTITY_CONFLICT",
          "The Auth identity does not match this user request. Ask the setup owner to check the account.");
    }
  }

  public void setActive(UUID id, boolean active) {
    requireConfigured();
    var identity = request(client.put().uri("/admin/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("ban_duration", active ? "none" : "876000h")));
    if (identity == null || !id.equals(identity.id())) {
      throw new ProviderFailure("AUTH_INVALID_RESPONSE", "Auth did not confirm the account change. Refresh and retry.", 200);
    }
  }

  private AuthIdentity request(RestClient.RequestHeadersSpec<?> request) {
    try {
      return request.header("apikey", adminKey).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminKey)
          .retrieve().body(AuthIdentity.class);
    } catch (RestClientResponseException response) {
      int status = response.getStatusCode().value();
      if (status == 409 || status == 422) {
        throw new ApiFailure(HttpStatus.CONFLICT, "AUTH_IDENTITY_CONFLICT",
            "Auth could not create this account because it conflicts with an existing identity. Ask the setup owner to check it.");
      }
      throw new ProviderFailure("AUTH_PROVIDER_FAILED",
          "Auth could not confirm this change. Dashboard access may already have been revoked. Refresh Settings and retry.", status);
    } catch (RestClientException unavailable) {
      throw new ProviderFailure("AUTH_PROVIDER_UNAVAILABLE",
          "Auth is unavailable. Dashboard access may already have been revoked. Refresh Settings and retry.", 0);
    }
  }

  private void requireConfigured() {
    if (adminKey == null || adminKey.isBlank()) {
      throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_ADMIN_NOT_CONFIGURED",
          "Staff administration is not configured. Ask the setup owner to configure server-side Auth access.");
    }
  }
}
