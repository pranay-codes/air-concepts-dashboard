package com.airconcepts.dashboard.workitems.sync;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public final class GreenLineSyncViews {
  private GreenLineSyncViews() {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "GreenLineSyncRun", requiredProperties = {"id", "status", "startedAt"})
  public record Run(UUID id, String status, Instant startedAt, Instant finishedAt, String errorCode) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "GreenLineSystemSyncStatus")
  public record SystemStatus(Run latest, Run lastSuccess, Run lastFailure) {}
  @Schema(name = "GreenLineSyncStatus", requiredProperties = {"entityId", "xero", "vwork"})
  public record Status(UUID entityId, SystemStatus xero, SystemStatus vwork) {}
}
