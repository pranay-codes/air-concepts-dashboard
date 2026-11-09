package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/entities", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityController {
  private final EntityService entities;
  private final CurrentUserResolver users;

  public EntityController(EntityService entities, CurrentUserResolver users) {
    this.entities = entities;
    this.users = users;
  }

  public record CreateEntity(@NotBlank @Size(max = 160) String displayName) {}
  public record RenameEntity(@NotBlank @Size(max = 160) String displayName) {}

  @GetMapping
  @Operation(operationId = "listEntities", summary = "List every configured entity accessible to active staff")
  public ResponseEntity<List<Entity>> list(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entities.list(users.from(jwt)));
  }

  @GetMapping("/{entityId}")
  @Operation(operationId = "getEntity", summary = "View a configured entity by its stable dashboard ID")
  public ResponseEntity<Entity> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entities.get(users.from(jwt), entityId));
  }

  @PostMapping
  @Operation(operationId = "createEntity", summary = "Create a dashboard entity (Director only)")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<Entity> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateEntity request) {
    Entity entity = entities.create(users.from(jwt), request.displayName());
    return ResponseEntity.created(URI.create("/api/v1/entities/" + entity.id()))
        .cacheControl(CacheControl.noStore()).body(entity);
  }

  @PatchMapping("/{entityId}")
  @Operation(operationId = "renameEntity", summary = "Rename a dashboard entity (Director only)")
  public ResponseEntity<Entity> rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @Valid @RequestBody RenameEntity request) {
    Entity entity = entities.rename(users.from(jwt), entityId, request.displayName());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entity);
  }

  @PostMapping("/{entityId}/archive")
  @Operation(operationId = "archiveEntity",
      summary = "Archive a dashboard entity (Director only). Records and the Xero grant are kept.")
  public ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId) {
    entities.archive(users.from(jwt), entityId);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
