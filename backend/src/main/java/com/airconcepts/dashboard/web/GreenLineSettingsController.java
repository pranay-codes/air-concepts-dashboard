package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.workitems.settings.GreenLineSettingsRepository.Settings;
import com.airconcepts.dashboard.workitems.settings.GreenLineSettingsService;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/entities/{entityId}/green-line/settings", produces = MediaType.APPLICATION_JSON_VALUE)
public class GreenLineSettingsController {
  public record SettingsRequest(@NotNull Boolean enabled, UUID defaultOwnerId, UUID serviceManagerId,
      @NotNull @Min(1) @Max(30) Integer lateThresholdWorkingDays,
      @NotNull @Min(0) @Max(365) Integer backfillDays) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "GreenLineSettings", requiredProperties = {"entityId", "enabled"},
      description = "All active staff see entity and enabled status; only Directors receive the other settings.")
  public record SettingsResponse(UUID entityId, boolean enabled, UUID defaultOwnerId, UUID serviceManagerId,
      Integer lateThresholdWorkingDays, Integer backfillDays) {
    static SettingsResponse from(Settings value, boolean director) {
      return new SettingsResponse(value.entityId(), value.enabled(), director ? value.defaultOwnerId() : null,
          director ? value.serviceManagerId() : null, director ? value.lateThresholdWorkingDays() : null,
          director ? value.backfillDays() : null);
    }
  }

  private final GreenLineSettingsService settings;
  private final CurrentUserResolver users;
  public GreenLineSettingsController(GreenLineSettingsService settings, CurrentUserResolver users) {
    this.settings = settings;
    this.users = users;
  }

  @GetMapping
  @Operation(operationId = "getGreenLineSettings", summary = "Read workflow status and Director settings for an entity")
  public ResponseEntity<SettingsResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(SettingsResponse.from(settings.get(user, entityId), user.role() == Role.DIRECTOR));
  }

  @PutMapping
  @Operation(operationId = "updateGreenLineSettings", summary = "Update an entity's workflow settings (Director only)")
  public ResponseEntity<SettingsResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @Valid @RequestBody SettingsRequest request) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR);
    var input = new Settings(entityId, request.enabled(), request.defaultOwnerId(), request.serviceManagerId(),
        request.lateThresholdWorkingDays(), request.backfillDays());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SettingsResponse.from(settings.save(user, input), true));
  }
}
