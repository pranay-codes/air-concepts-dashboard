package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.workitems.operations.InvoiceDraftService;
import com.airconcepts.dashboard.workitems.operations.PrepareInvoiceService;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name="bearerAuth")
@RequestMapping(value="/api/v1/entities/{entityId}/work-items/{itemId}",produces=MediaType.APPLICATION_JSON_VALUE)
public class InvoiceDraftController {
  private final CurrentUserResolver users;
  private final EntityService entities;
  private final InvoiceDraftService drafts;
  private final PrepareInvoiceService prepare;
  @io.swagger.v3.oas.annotations.media.Schema(name="PrepareInvoiceRequest")
  public record Request(@NotNull @Min(0) Integer version,@NotBlank @Pattern(regexp="[0-9a-f]{64}") String fingerprint) {
    @JsonAnySetter public void rejectUnknown(String name,Object value) { throw new IllegalArgumentException("Unsupported invoice field"); }
  }
  public InvoiceDraftController(CurrentUserResolver users, EntityService entities, InvoiceDraftService drafts, PrepareInvoiceService prepare) {
    this.users=users; this.entities=entities; this.drafts=drafts; this.prepare=prepare;
  }
  @GetMapping("/invoice-draft") @Operation(operationId="reviewInvoiceDraft",summary="Review invoice details from the Xero quote")
  public ResponseEntity<InvoiceDraftService.Review> review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,@PathVariable UUID itemId) {
    var user=users.from(jwt);
    user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);
    entities.get(user,entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(drafts.review(user,entityId,itemId));
  }
  @PostMapping("/invoice") @Operation(operationId="prepareInvoice",summary="Prepare a draft invoice for the job")
  public ResponseEntity<PrepareInvoiceService.Result> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID entityId,
      @PathVariable UUID itemId,@Valid @RequestBody Request request) {
    var user=users.from(jwt);
    user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);
    entities.get(user,entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(prepare.submit(user,entityId,itemId,request.version(),request.fingerprint()));
  }
}
