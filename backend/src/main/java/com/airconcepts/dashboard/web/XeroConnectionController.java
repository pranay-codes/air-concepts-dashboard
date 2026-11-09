package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.xero.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class XeroConnectionController {
  private final XeroConnectionService connections;
  private final CurrentUserResolver users;
  private final XeroSettings settings;
  public XeroConnectionController(XeroConnectionService connections, CurrentUserResolver users, XeroSettings settings) {
    this.connections = connections; this.users = users; this.settings = settings;
  }
  public record SelectConnection(@NotNull UUID connectionId) {}

  @PostMapping(value = "/entities/{entityId}/xero/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(operationId = "startXeroAuthorization", summary = "Start a Director-owned Xero authorisation attempt")
  public ResponseEntity<ConnectionViews.Start> start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
    return uncached(connections.start(users.from(jwt), entityId));
  }

  @GetMapping("/xero/callback")
  @ResponseStatus(HttpStatus.SEE_OTHER)
  @Operation(operationId = "xeroCallback", summary = "Receive a single-use Xero OAuth callback and redirect without credentials")
  public ResponseEntity<Void> callback(@RequestParam(required = false) String state,
      @RequestParam(required = false) String code, @RequestParam(required = false) String error) {
    UUID id = connections.callback(state, code, error);
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(settings.callbackDestination(id))
        .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
  }

  @GetMapping(value = "/xero/attempts/{attemptId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(operationId = "getXeroAuthorizationAttempt", summary = "Read the current Director's result and authorised organisation choices")
  public ResponseEntity<ConnectionViews.Attempt> attempt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
    return uncached(connections.attempt(users.from(jwt), attemptId));
  }

  @PutMapping(value = "/xero/attempts/{attemptId}/connection", produces = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(operationId = "confirmXeroConnection", summary = "Confirm a freshly discovered organisation for the attempt's entity")
  public ResponseEntity<ConnectionViews.Connection> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
      @Valid @RequestBody SelectConnection selection) {
    return uncached(connections.confirm(users.from(jwt), attemptId, selection.connectionId()));
  }

  @GetMapping(value = "/entities/{entityId}/xero", produces = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(operationId = "getXeroConnection", summary = "Read safe connection metadata for the selected entity")
  public ResponseEntity<ConnectionViews.Connection> status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
    return uncached(connections.status(users.from(jwt), entityId));
  }
  private static <T> ResponseEntity<T> uncached(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
