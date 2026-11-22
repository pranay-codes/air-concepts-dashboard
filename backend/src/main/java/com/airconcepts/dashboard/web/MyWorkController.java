package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.workitems.MyWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/work-items/my-work", produces = MediaType.APPLICATION_JSON_VALUE)
public class MyWorkController {
  private final CurrentUserResolver users;
  private final EntityService entities;
  private final MyWorkService work;
  public MyWorkController(CurrentUserResolver users, EntityService entities, MyWorkService work) {
    this.users = users; this.entities = entities; this.work = work;
  }
  @GetMapping
  @Operation(operationId = "getMyWork", summary = "Read the current user's work and attention queues")
  public ResponseEntity<MyWorkService.MyWork> get(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) UUID entityId) {
    var user = users.from(jwt);
    user.requireRole(com.airconcepts.dashboard.security.Role.DIRECTOR,
        com.airconcepts.dashboard.security.Role.FINANCIAL_ADMIN, com.airconcepts.dashboard.security.Role.STANDARD_USER);
    if (entityId != null) entities.get(user, entityId);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(work.get(user, entityId));
  }
}
