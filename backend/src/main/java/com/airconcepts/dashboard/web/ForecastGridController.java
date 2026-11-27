package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.forecast.ForecastGridService;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @SecurityRequirement(name="bearerAuth") @RequestMapping("/api/v1/entities/{entityId}/forecast/grid")
public class ForecastGridController {
  public record AmountRequest(String month,String direction,String scope,String key,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal amount,Boolean reset) {}
  public record AmountSaved(boolean saved) {}
  private final ForecastGridService service; private final CurrentUserResolver users;
  public ForecastGridController(ForecastGridService service,CurrentUserResolver users){this.service=service;this.users=users;}
  @GetMapping @Operation(operationId="getForecastGrid")
  public ResponseEntity<ForecastGridService.Grid> grid(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam String startMonth,@RequestParam(defaultValue="8") int monthCount){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.grid(users.from(jwt),entityId,startMonth,monthCount));}
  @PostMapping("/generate") @Operation(operationId="generateForecastGrid")
  public ResponseEntity<ForecastGridService.Generation> generate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.generate(users.from(jwt),entityId));}
  @PutMapping("/amount") @Operation(operationId="saveForecastGridAmount")
  public ResponseEntity<AmountSaved> saveAmount(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestBody AmountRequest body){
    if(body==null) throw com.airconcepts.dashboard.forecast.ForecastValidation.invalid("amount","Enter an amount.");
    if(body.amount()==null&&!Boolean.TRUE.equals(body.reset())) throw com.airconcepts.dashboard.forecast.ForecastValidation.invalid("amount","Enter an amount or choose the calculated amount.");
    if(body.amount()!=null&&Boolean.TRUE.equals(body.reset())) throw com.airconcepts.dashboard.forecast.ForecastValidation.invalid("amount","Choose an amount or restore the calculation, not both.");
    service.saveAmount(users.from(jwt),entityId,body.month(),body.direction(),body.scope(),body.key(),body.amount());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AmountSaved(true));
  }
}
