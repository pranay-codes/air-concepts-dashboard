package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.workitems.sync.GreenLineSyncService;
import com.airconcepts.dashboard.workitems.sync.GreenLineSyncViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/entities/{entityId}/green-line/sync")
public class GreenLineSyncController {
  private final GreenLineSyncService service;
  private final CurrentUserResolver users;
  public GreenLineSyncController(GreenLineSyncService service, CurrentUserResolver users) {
    this.service = service; this.users = users;
  }
  @GetMapping @Operation(operationId = "getGreenLineSync", summary = "Read workflow sync status")
  public ResponseEntity<GreenLineSyncViews.Status> status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(user, entityId));
  }
  @PostMapping @Operation(operationId = "refreshGreenLine", summary = "Refresh workflow quotes")
  public ResponseEntity<GreenLineSyncViews.Status> refresh(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
    var user = users.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    service.runManual(user, entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(user, entityId));
  }
}
