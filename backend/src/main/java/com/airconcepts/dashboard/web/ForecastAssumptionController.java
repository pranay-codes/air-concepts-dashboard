package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.forecast.*;
import com.airconcepts.dashboard.forecast.calc.ForecastDataset.*;
import com.airconcepts.dashboard.forecast.ForecastAssumptionRepository.*;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @SecurityRequirement(name="bearerAuth")
@RequestMapping("/api/v1/entities/{entityId}/forecast")
public class ForecastAssumptionController {
  public record OpeningRequest(@io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal openingBalance,String source) {}
  public record ManualRequest(String direction,String category,String description,@io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal amount,String recurrence,Integer dayOfMonth,YearMonth startMonth,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) YearMonth endMonth,Boolean active) {
    ManualItem item(UUID id){return new ManualItem(id,direction,category,description,amount,recurrence,dayOfMonth,startMonth,endMonth,active==null||active);}
  }
  public record StaffingRequest(String label,@io.swagger.v3.oas.annotations.media.Schema(type="string",format="decimal") BigDecimal monthlyCost,Integer payDayOfMonth,YearMonth startMonth,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) YearMonth endMonth,Boolean active,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal hourlyRate,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal") BigDecimal weeklyHours,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) String paymentFrequency,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="date") LocalDate firstPayday,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="date") LocalDate employmentStart,
      @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="date") LocalDate employmentEnd) {
    StaffingLine item(UUID id){return new StaffingLine(id,label,monthlyCost,payDayOfMonth,startMonth,endMonth,active==null||active,hourlyRate,weeklyHours,paymentFrequency,firstPayday,employmentStart,employmentEnd);}
  }
  private final CurrentUserResolver users;private final ForecastPeriodService periods;private final ManualItemService manual;
  private final StaffingService staff;private final ForecastAccess access;private final ForecastAssumptionRepository repository;
  public ForecastAssumptionController(CurrentUserResolver users,ForecastPeriodService periods,ManualItemService manual,StaffingService staff,ForecastAccess access,ForecastAssumptionRepository repository){
    this.users=users;this.periods=periods;this.manual=manual;this.staff=staff;this.access=access;this.repository=repository;
  }
  private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
  @GetMapping("/periods/{month}/opening-balance") @Operation(operationId="getForecastOpeningBalance")
  public ResponseEntity<OpeningBalance> opening(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable String month){return ok(periods.get(users.from(jwt),entityId,ForecastMonths.parse(month).atDay(1)));}
  @PutMapping("/periods/{month}/opening-balance") @Operation(operationId="saveForecastOpeningBalance")
  public ResponseEntity<OpeningBalance> saveOpening(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable String month,@RequestBody OpeningRequest body){return ok(periods.save(users.from(jwt),entityId,ForecastMonths.parse(month).atDay(1),body.openingBalance(),body.source()));}
  @GetMapping("/manual-items") @Operation(operationId="listForecastManualItems")
  public ResponseEntity<List<ManualItem>> manual(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam(defaultValue="false") boolean includeInactive){return ok(manual.list(users.from(jwt),entityId,includeInactive));}
  @PostMapping("/manual-items") @Operation(operationId="createForecastManualItem")
  public ResponseEntity<ManualItem> createManual(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestBody ManualRequest body){return ok(manual.create(users.from(jwt),entityId,body.item(null)));}
  @PatchMapping("/manual-items/{id}") @Operation(operationId="updateForecastManualItem")
  public ResponseEntity<ManualItem> updateManual(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id,@io.swagger.v3.oas.annotations.parameters.RequestBody(content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=ManualRequest.class))) @RequestBody Map<String,Object> body){return ok(manual.patch(users.from(jwt),entityId,id,body));}
  @PostMapping("/manual-items/{id}/deactivate") @Operation(operationId="deactivateForecastManualItem")
  public ResponseEntity<ManualItem> deactivateManual(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id){return ok(manual.deactivate(users.from(jwt),entityId,id));}
  @GetMapping("/staffing") @Operation(operationId="listForecastStaffing")
  public ResponseEntity<List<StaffingLine>> staff(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam(defaultValue="false") boolean includeInactive){return ok(staff.list(users.from(jwt),entityId,includeInactive));}
  @PostMapping("/staffing") @Operation(operationId="createForecastStaffing")
  public ResponseEntity<StaffingLine> createStaff(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestBody StaffingRequest body){return ok(staff.create(users.from(jwt),entityId,body.item(null)));}
  @PatchMapping("/staffing/{id}") @Operation(operationId="updateForecastStaffing")
  public ResponseEntity<StaffingLine> updateStaff(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id,@io.swagger.v3.oas.annotations.parameters.RequestBody(content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=StaffingRequest.class))) @RequestBody Map<String,Object> body){return ok(staff.patch(users.from(jwt),entityId,id,body));}
  @PostMapping("/staffing/{id}/deactivate") @Operation(operationId="deactivateForecastStaffing")
  public ResponseEntity<StaffingLine> deactivateStaff(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID id){return ok(staff.deactivate(users.from(jwt),entityId,id));}
  @GetMapping("/audit") @Operation(operationId="getForecastAudit")
  public ResponseEntity<AuditPage> audit(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="25") int pageSize){
    access.requireRead(users.from(jwt),entityId);
    if(page<1||pageSize<1||pageSize>100)throw ForecastValidation.invalid("page","Use a positive page and 1–100 rows.");
    return ok(repository.auditRows(entityId,page,pageSize));
  }
}
