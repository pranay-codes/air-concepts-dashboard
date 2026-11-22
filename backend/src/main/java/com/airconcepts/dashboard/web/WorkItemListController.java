package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.workitems.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/work-items", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkItemListController {
  private final CurrentUserResolver users;
  private final EntityService entities;
  private final WorkItemListService work;
  public WorkItemListController(CurrentUserResolver users, EntityService entities, WorkItemListService work) {
    this.users = users; this.entities = entities; this.work = work;
  }
  @GetMapping
  @Operation(operationId = "listWorkItems", summary = "List work items with entity-scoped filters and pagination")
  public ResponseEntity<WorkItemListService.Page> list(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) UUID entityId, @RequestParam(required = false) WorkItemStage stage,
      @RequestParam(required = false) StageMachine.QuoteStatus xeroStatus, @RequestParam(required = false) UUID ownerId,
      @RequestParam(required = false) LocalDate followUpDueBy, @RequestParam(required = false) Boolean late,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "stageEnteredAt") WorkItemListQuery.Sort sort,
      @RequestParam(defaultValue = "ASC") WorkItemListQuery.Direction direction,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int pageSize) {
    var user = users.from(jwt);
    user.requireRole(com.airconcepts.dashboard.security.Role.DIRECTOR,
        com.airconcepts.dashboard.security.Role.FINANCIAL_ADMIN, com.airconcepts.dashboard.security.Role.STANDARD_USER);
    if (entityId != null) entities.get(user, entityId);
    WorkItemListQuery query;
    try { query = new WorkItemListQuery(entityId, stage, xeroStatus, ownerId, followUpDueBy, late, search, sort, direction, page, pageSize); }
    catch (IllegalArgumentException invalid) {
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_WORK_ITEM_QUERY", "Check the search and page limits.");
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(work.list(user, query));
  }
}
