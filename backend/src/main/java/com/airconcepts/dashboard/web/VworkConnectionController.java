package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.vwork.VworkConnectionService;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/vwork-connection", produces = MediaType.APPLICATION_JSON_VALUE)
public class VworkConnectionController {
  @Schema(name = "VworkConnectionRequest", requiredProperties = "apiKey")
  public record ConnectionRequest(@Schema(description = "vWork API key; write only", accessMode = Schema.AccessMode.WRITE_ONLY) String apiKey) {
    @Override public String toString() { return "VworkConnectionRequest[redacted]"; }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "VworkConnection", requiredProperties = "status",
      description = "All active staff see status; only Directors receive connection metadata. Credentials are never returned.")
  public record ConnectionResponse(String status, UUID configuredBy, Instant configuredAt, Instant verifiedAt, String errorCode) {
    static ConnectionResponse from(VworkConnectionService.View view, boolean director) {
      return new ConnectionResponse(view.status(), director ? view.configuredBy() : null,
          director ? view.configuredAt() : null, director ? view.verifiedAt() : null, director ? view.errorCode() : null);
    }
  }

  private final VworkConnectionService connection;
  private final CurrentUserResolver users;
  public VworkConnectionController(VworkConnectionService connection, CurrentUserResolver users) {
    this.connection = connection; this.users = users;
  }

  @GetMapping
  @Operation(operationId = "getVworkConnection", summary = "Read vWork connection status")
  public ResponseEntity<ConnectionResponse> get(@AuthenticationPrincipal Jwt jwt) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    return response(user);
  }

  @PutMapping
  @Operation(operationId = "connectVwork", summary = "Connect or replace the vWork key (Director only)")
  public ResponseEntity<ConnectionResponse> connect(@AuthenticationPrincipal Jwt jwt, @RequestBody ConnectionRequest request) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR);
    connection.connect(user, request.apiKey());
    return response(user);
  }

  @DeleteMapping
  @Operation(operationId = "disconnectVwork", summary = "Disconnect vWork (Director only)")
  public ResponseEntity<ConnectionResponse> disconnect(@AuthenticationPrincipal Jwt jwt) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR);
    connection.disconnect(user);
    return response(user);
  }

  private ResponseEntity<ConnectionResponse> response(CurrentUser user) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ConnectionResponse.from(connection.get(user), user.role() == Role.DIRECTOR));
  }
}
