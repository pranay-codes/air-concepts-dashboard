package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.workitems.OverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/work-items/overview", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkItemOverviewController {
  private final CurrentUserResolver users;
  private final EntityService entities;
  private final OverviewService overview;
  public WorkItemOverviewController(CurrentUserResolver users, EntityService entities, OverviewService overview) {
    this.users = users; this.entities = entities; this.overview = overview;
  }
  @GetMapping
  @Operation(operationId = "getWorkItemOverview", summary = "Read the Director overview of work, cycle times and invoices")
  public ResponseEntity<OverviewService.Overview> get(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR);
    if (entityId != null) entities.get(user, entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(overview.get(user, entityId, from, to));
  }
}
