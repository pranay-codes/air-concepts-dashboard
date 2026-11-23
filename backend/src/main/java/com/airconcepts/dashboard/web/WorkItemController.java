package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.workitems.WorkItemReadService;
import com.airconcepts.dashboard.workitems.WorkItemActions;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/entities/{entityId}/work-items/{itemId}", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkItemController {
  public record UpdateRequest(@NotNull @Min(0) Integer version, LocalDate followUpDate,
      @Size(max = 4000) String notes, Boolean clearFollowUp, UUID ownerId, Boolean clearOwner) {
    @JsonAnySetter public void rejectUnknown(String name, Object value) {
      throw new IllegalArgumentException("Unsupported work item field");
    }
  }
  public record ReasonRequest(@NotNull @Min(0) Integer version,
      @io.swagger.v3.oas.annotations.media.Schema(requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
          minLength = 1, maxLength = 500) String reason) {}
  public record VersionRequest(@NotNull @Min(0) Integer version) {
    @JsonAnySetter public void rejectUnknown(String name,Object value) { throw new IllegalArgumentException("Unsupported work item field"); }
  }
  private final CurrentUserResolver users;
  private final EntityService entities;
  private final WorkItemReadService reads;
  private final WorkItemActions actions;
  public WorkItemController(CurrentUserResolver users, EntityService entities, WorkItemReadService reads, WorkItemActions actions) {
    this.users = users; this.entities = entities; this.reads = reads; this.actions = actions;
  }
  private CurrentUser active(Jwt jwt, UUID entity) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    entities.get(user, entity);
    return user;
  }
  private ResponseEntity<WorkItemReadService.Detail> detail(CurrentUser user, UUID entity, UUID item) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.get(user, entity, item));
  }
  @GetMapping
  @Operation(operationId = "getWorkItem", summary = "Read an entity's work item and timeline")
  public ResponseEntity<WorkItemReadService.Detail> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @PathVariable UUID itemId) { return detail(active(jwt, entityId), entityId, itemId); }

  @PatchMapping
  @Operation(operationId = "updateWorkItem", summary = "Update the work item's follow-up date, notes and owner")
  public ResponseEntity<WorkItemReadService.Detail> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @PathVariable UUID itemId, @Valid @RequestBody UpdateRequest request) {
    var user = active(jwt, entityId);
    boolean clear = Boolean.TRUE.equals(request.clearFollowUp());
    if (clear && request.followUpDate() != null)
      throw new ApiFailure(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_WORK_ITEM_UPDATE", "Choose a follow-up date or clear it, not both.");
    boolean clearOwner = Boolean.TRUE.equals(request.clearOwner());
    if (clearOwner && request.ownerId() != null)
      throw new ApiFailure(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_WORK_ITEM_UPDATE", "Choose an owner or clear it, not both.");
    var result = actions.edit(user, entityId, itemId, request.version(), request.followUpDate() != null, request.followUpDate(),
        clear, request.notes() != null, request.notes(), request.ownerId() != null, request.ownerId(), clearOwner);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }
  @PostMapping("/close-lost")
  @Operation(operationId = "closeWorkItemLost", summary = "Close a quoted work item as lost")
  public ResponseEntity<WorkItemReadService.Detail> closeLost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @PathVariable UUID itemId, @Valid @RequestBody ReasonRequest request) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN);
    requireReason(request.reason());
    entities.get(user, entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(actions.closeLost(user, entityId, itemId, request.version(), request.reason()));
  }
  @PostMapping("/no-invoice-needed")
  @Operation(operationId = "closeWorkItemNoInvoice", summary = "Close a work item that needs no invoice")
  public ResponseEntity<WorkItemReadService.Detail> closeNoInvoice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @PathVariable UUID itemId, @Valid @RequestBody ReasonRequest request) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN);
    requireReason(request.reason());
    entities.get(user, entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(actions.closeNoInvoice(user, entityId, itemId, request.version(), request.reason()));
  }
  @PostMapping("/mark-sent") @Operation(operationId="markInvoiceSent",summary="Confirm that an authorised invoice was sent")
  public ResponseEntity<WorkItemReadService.Detail> markSent(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,
      @PathVariable UUID itemId,@Valid @RequestBody VersionRequest request) {
    var user=users.from(jwt);
    user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);
    entities.get(user,entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actions.markSent(user,entityId,itemId,request.version()));
  }
  @PostMapping("/mark-accepted") @Operation(operationId="markQuoteAccepted",summary="Accept the quote in Xero and move the job to To schedule")
  public ResponseEntity<WorkItemReadService.Detail> markAccepted(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,
      @PathVariable UUID itemId,@Valid @RequestBody VersionRequest request) {
    var user=users.from(jwt);
    user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);
    entities.get(user,entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actions.acceptQuote(user,entityId,itemId,request.version()));
  }
  private static void requireReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw new ApiFailure(org.springframework.http.HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Enter a reason of 1 to 500 characters.");
  }
}
