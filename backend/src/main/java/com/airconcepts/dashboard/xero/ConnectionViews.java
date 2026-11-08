package com.airconcepts.dashboard.xero;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Public connection metadata; deliberately contains neither tenant IDs nor grant tokens. */
public final class ConnectionViews {
  private ConnectionViews() {}
  public enum Status { CONNECTED, NOT_CONNECTED, NEEDS_RECONNECTION }
  @Schema(name = "XeroAuthorizationStart")
  public record Start(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID attemptId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authorizationUrl,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {}
  @Schema(name = "XeroTenantChoice")
  public record Choice(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID connectionId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
  @Schema(name = "XeroAuthorizationAttempt")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Attempt(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID entityId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OAuthAttempt.State state,
      String errorCode, Integer upstreamStatus,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Choice> choices) {}
  @Schema(name = "XeroConnection")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Connection(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID entityId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Status status,
      String organisationName, Instant lastConfirmedAt, String errorCode) {}
}
