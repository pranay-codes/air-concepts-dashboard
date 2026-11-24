package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.forecast.ForecastGridService;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @SecurityRequirement(name="bearerAuth") @RequestMapping("/api/v1/entities/{entityId}/forecast/grid")
public class ForecastGridController {
  private final ForecastGridService service; private final CurrentUserResolver users;
  public ForecastGridController(ForecastGridService service,CurrentUserResolver users){this.service=service;this.users=users;}
  @GetMapping @Operation(operationId="getForecastGrid")
  public ResponseEntity<ForecastGridService.Grid> grid(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam String startMonth,@RequestParam(defaultValue="8") int monthCount){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.grid(users.from(jwt),entityId,startMonth,monthCount));}
  @PostMapping("/generate") @Operation(operationId="generateForecastGrid")
  public ResponseEntity<ForecastGridService.Generation> generate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.generate(users.from(jwt),entityId));}
}
