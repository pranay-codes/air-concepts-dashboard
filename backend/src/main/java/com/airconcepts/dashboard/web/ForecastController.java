package com.airconcepts.dashboard.web;
import com.airconcepts.dashboard.forecast.*;
import com.airconcepts.dashboard.forecast.ForecastGenerationService.GenerationResult;
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
  private final ForecastService service;private final ForecastGenerationService generation;private final ForecastStoredDocuments documents;private final CurrentUserResolver users;private final ForecastNoteService notes;
  public ForecastController(ForecastService service,ForecastGenerationService generation,ForecastStoredDocuments documents,CurrentUserResolver users,ForecastNoteService notes){
    this.service=service;this.generation=generation;this.documents=documents;this.users=users;this.notes=notes;
  }
  private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
  @GetMapping("/entities/{entityId}/forecast") @Operation(operationId="getForecast")
  public ResponseEntity<ForecastView> view(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam(required=false) String financialYearStart){return ok(service.view(users.from(jwt),entityId,financialYearStart));}
  @GetMapping("/entities/{entityId}/forecast/months/{month}/items") @Operation(operationId="getForecastMonthItems")
  public ResponseEntity<MonthItems> items(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable String month,@RequestParam(required=false) String direction){return ok(service.items(users.from(jwt),entityId,month,direction));}
  /** Internal note for one forecast transaction. A blank note removes it. Never sent to Xero. */
  @PutMapping("/entities/{entityId}/forecast/transactions/{source}/{sourceId}/note") @Operation(operationId="saveForecastTransactionNote")
  public ResponseEntity<ForecastNoteRepository.TransactionNote> note(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable String source,@PathVariable UUID sourceId,@RequestBody ForecastNoteService.NoteRequest body){return ok(notes.save(users.from(jwt),entityId,source,sourceId,body.note()));}
  @PostMapping("/entities/{entityId}/forecast/months/{month}/generate") @Operation(operationId="generateForecastMonth")
  public ResponseEntity<GenerationResult> generate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable String month){return ok(generation.generate(users.from(jwt),entityId,month));}
  @GetMapping("/entities/{entityId}/forecast/documents") @Operation(operationId="listForecastDocuments")
  public ResponseEntity<DocumentPage> documents(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam(required=false) String type,@RequestParam(defaultValue="false") boolean includeSettled,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="25") int pageSize){return ok(documents.list(users.from(jwt),entityId,type,includeSettled,page,pageSize));}
  @GetMapping("/entities/{entityId}/forecast/documents/{id}") @Operation(operationId="getForecastDocument")
  public ResponseEntity<ForecastStoredDocuments.StoredDocument> document(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id){return ok(documents.get(users.from(jwt),entityId,id));}
  @GetMapping("/forecast/combined") @Operation(operationId="getCombinedForecast")
  public ResponseEntity<CombinedView> combined(@AuthenticationPrincipal Jwt jwt,@RequestParam List<UUID> entityIds,@RequestParam(required=false) String financialYearStart){return ok(service.combined(users.from(jwt),entityIds,financialYearStart));}
}
