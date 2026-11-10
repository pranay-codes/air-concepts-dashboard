package com.airconcepts.dashboard.web;
import com.airconcepts.dashboard.forecast.*;
import com.airconcepts.dashboard.forecast.ForecastService.*;
import com.airconcepts.dashboard.forecast.ForecastStoredDocuments.DocumentPage;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @SecurityRequirement(name="bearerAuth") @RequestMapping("/api/v1")
public class ForecastController {
  private final ForecastService service;private final ForecastStoredDocuments documents;private final CurrentUserResolver users;
  public ForecastController(ForecastService service,ForecastStoredDocuments documents,CurrentUserResolver users){this.service=service;this.documents=documents;this.users=users;}
  private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
  @GetMapping("/entities/{entityId}/forecast") @Operation(operationId="getForecast")
  public ResponseEntity<ForecastView> view(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam(required=false) String startMonth){return ok(service.view(users.from(jwt),entityId,startMonth));}
  @GetMapping("/entities/{entityId}/forecast/months/{month}/items") @Operation(operationId="getForecastMonthItems")
  public ResponseEntity<MonthItems> items(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable String month,@RequestParam(required=false) String direction,@RequestParam(required=false) String startMonth){return ok(service.items(users.from(jwt),entityId,month,direction,startMonth));}
  @GetMapping("/entities/{entityId}/forecast/documents") @Operation(operationId="listForecastDocuments")
  public ResponseEntity<DocumentPage> documents(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam(required=false) String type,@RequestParam(defaultValue="false") boolean includeSettled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="25") int pageSize){return ok(documents.list(users.from(jwt),entityId,type,includeSettled,page,pageSize));}
  @GetMapping("/entities/{entityId}/forecast/documents/{id}") @Operation(operationId="getForecastDocument")
  public ResponseEntity<ForecastStoredDocuments.StoredDocument> document(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id){return ok(documents.get(users.from(jwt),entityId,id));}
  @GetMapping("/forecast/combined") @Operation(operationId="getCombinedForecast")
  public ResponseEntity<CombinedView> combined(@AuthenticationPrincipal Jwt jwt,@RequestParam List<UUID> entityIds,@RequestParam(required=false) String startMonth){return ok(service.combined(users.from(jwt),entityIds,startMonth));}
}
