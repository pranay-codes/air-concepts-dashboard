package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.workitems.operations.CreateJobService;
import com.airconcepts.dashboard.workitems.operations.VworkJobDraftService;
import com.airconcepts.dashboard.workitems.operations.VworkOperationReconciler;
import com.airconcepts.dashboard.workitems.operations.OperationState;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name="bearerAuth")
@RequestMapping(value="/api/v1/entities/{entityId}/work-items/{itemId}",produces=MediaType.APPLICATION_JSON_VALUE)
public class VworkJobController {
  @Schema(name="CreateJobScopeLine")
  public record ScopeRequest(@NotBlank @Size(max=4000) String description, @NotBlank @Size(max=80) String quantity) {
    @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported scope field"); }
  }
  @Schema(name="CreateJobRequest")
  public record Request(@NotNull @Min(0) Integer version, @NotBlank @Size(max=255) String reference,
      @NotBlank @Size(max=120) String templateId, @NotBlank @Size(max=4000) String description,
      @NotBlank @Size(max=500) String customer, @NotBlank @Size(max=500) String contactPerson,
      @NotBlank @Size(max=40) String phone, @NotBlank @Size(max=2000) String siteAddress,
      @Size(max=2000) String notes, @NotEmpty @Size(max=200) List<@NotNull @Valid ScopeRequest> scope) {
    @JsonAnySetter public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported job field"); }
  }
  private final CurrentUserResolver users;
  private final EntityService entities;
  private final VworkJobDraftService drafts;
  private final CreateJobService jobs;
  private final VworkOperationReconciler reconciler;
  public record LookupResult(OperationState.Result outcome) {}
  public VworkJobController(CurrentUserResolver users, EntityService entities, VworkJobDraftService drafts, CreateJobService jobs,
      VworkOperationReconciler reconciler) {
    this.users=users; this.entities=entities; this.drafts=drafts; this.jobs=jobs; this.reconciler=reconciler;
  }
  private CurrentUser active(Jwt jwt, UUID entity) {
    var user=users.from(jwt);
    user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN,Role.STANDARD_USER);
    entities.get(user,entity);
    return user;
  }
  @GetMapping("/job-draft") @Operation(operationId="getCreateJobDraft",summary="Prepare a price-free vWork job draft")
  public ResponseEntity<VworkJobDraftService.Draft> draft(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID itemId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(drafts.draft(active(jwt,entityId),entityId,itemId));
  }
  @PostMapping("/vwork-job") @Operation(operationId="createVworkJob",summary="Create or link a vWork job")
  public ResponseEntity<CreateJobService.Result> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID itemId,
      @Valid @RequestBody Request request) {
    var user=active(jwt,entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.submit(user,entityId,itemId,request.version(),job(request)));
  }
  private VworkClient.NewJob job(Request request) {
    var scope=request.scope().stream().map(line -> new VworkClient.ScopeLine(line.description(),line.quantity())).toList();
    return new VworkClient.NewJob(request.reference(),request.templateId(),request.description(),scope,
        request.customer(),request.contactPerson(),request.phone(),request.siteAddress(),request.notes());
  }
  @PostMapping("/operations/{operationId}/retry") @Operation(operationId="retryVworkOperation",summary="Retry a failed vWork job creation")
  public ResponseEntity<CreateJobService.Result> retry(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID itemId,
      @PathVariable UUID operationId,@Valid @RequestBody Request request) {
    var user=active(jwt,entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(jobs.retry(user,entityId,itemId,request.version(),job(request),operationId));
  }
  @PostMapping("/operations/{operationId}/lookup") @Operation(operationId="lookupVworkOperation",summary="Look up an unconfirmed vWork job")
  public ResponseEntity<LookupResult> lookup(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID itemId,
      @PathVariable UUID operationId) {
    var user=active(jwt,entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new LookupResult(reconciler.lookup(user,entityId,itemId,operationId)));
  }
}
