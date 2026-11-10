package com.airconcepts.dashboard.web;
import com.airconcepts.dashboard.forecast.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.Split;
import com.airconcepts.dashboard.forecast.ForecastAdjustmentRepository.AdjustmentState;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @SecurityRequirement(name="bearerAuth")
@RequestMapping("/api/v1/entities/{entityId}/forecast/documents/{id}")
public class ForecastAdjustmentController {
  public record SplitsRequest(List<Split> lines){}public record ConflictRequest(String resolution){}
  private final ForecastAdjustmentService service;private final CurrentUserResolver users;
  public ForecastAdjustmentController(ForecastAdjustmentService service,CurrentUserResolver users){this.service=service;this.users=users;}
  private static ResponseEntity<AdjustmentState> ok(AdjustmentState state){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(state);}
  @PutMapping("/adjustment") @Operation(operationId="adjustForecastDocument")
  public ResponseEntity<AdjustmentState> adjust(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id,@RequestBody ForecastAdjustmentService.AdjustmentChange body){return ok(service.adjust(users.from(jwt),entityId,id,body));}
  @PutMapping("/splits") @Operation(operationId="splitForecastDocument")
  public ResponseEntity<AdjustmentState> splits(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id,@RequestBody SplitsRequest body){return ok(service.splits(users.from(jwt),entityId,id,body.lines()));}
  @PostMapping("/conflict") @Operation(operationId="resolveForecastConflict")
  public ResponseEntity<AdjustmentState> conflict(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id,@RequestBody ConflictRequest body){return ok(service.conflict(users.from(jwt),entityId,id,body.resolution()));}
}
