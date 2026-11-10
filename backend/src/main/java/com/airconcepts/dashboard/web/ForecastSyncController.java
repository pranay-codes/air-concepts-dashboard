package com.airconcepts.dashboard.web;
import com.airconcepts.dashboard.forecast.sync.*;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@SecurityRequirement(name="bearerAuth")
@RequestMapping("/api/v1/entities/{entityId}/forecast/sync")
public class ForecastSyncController {
  private final ForecastSyncService service;
  private final CurrentUserResolver users;
  public ForecastSyncController(ForecastSyncService service,CurrentUserResolver users) {this.service=service;this.users=users;}
  @GetMapping @Operation(operationId="getForecastSyncHistory",summary="Read the last ten forecast sync runs")
  public ResponseEntity<List<SyncRun>> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(users.from(jwt),entityId));
  }
  @PostMapping @Operation(operationId="syncForecast",summary="Reconcile the entity forecast from its Xero organisation")
  public ResponseEntity<SyncRun> sync(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId) {
    try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.runManual(users.from(jwt),entityId)); }
    catch (ProviderFailure failure) { if(failure.upstreamStatus()==429)throw new ForecastRateLimited(failure);throw failure; }
  }
  private static final class ForecastRateLimited extends ProviderFailure {
    ForecastRateLimited(ProviderFailure failure){super(failure.code(),failure.getMessage(),failure.upstreamStatus(),failure.retryAfterSeconds());}
    @Override public HttpStatus status(){return HttpStatus.TOO_MANY_REQUESTS;}
  }
}
