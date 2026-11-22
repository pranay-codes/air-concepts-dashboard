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
  public enum QuoteAccess { NOT_ENABLED, UNKNOWN, READY, RECONNECT_REQUIRED }
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
  /** maskedTenantId shows only the first and last four characters of the Xero tenant ID. */
  @Schema(name = "XeroConnection")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Connection(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID entityId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Status status,
      String organisationName, String maskedTenantId, String connectedBy,
      Instant lastConfirmedAt, Instant lastCheckedAt, String errorCode,
      QuoteAccess quoteAccess) {}

  /** Returns a display form such as b41e…7c21. The full tenant ID never leaves the server. */
  public static String maskTenant(UUID tenantId) {
    String value = tenantId.toString();
    return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
  }
}
