package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.staff.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffController {
  private final StaffService staff;
  private final CurrentUserResolver users;

  public StaffController(StaffService staff, CurrentUserResolver users) {
    this.staff = staff;
    this.users = users;
  }

  public record CreateStaff(@NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 160) String displayName, @NotNull Role role) {}
  public record UpdateStaff(@NotBlank @Size(max = 160) String displayName,
      @NotNull Role role, @NotNull Boolean active) {}

  @GetMapping("/users")
  @Operation(operationId = "listUsers", summary = "List staff including inactive accounts (Director only)")
  public ResponseEntity<List<StaffMember>> list(@AuthenticationPrincipal Jwt jwt) {
    return uncached(staff.list(users.from(jwt)));
  }

  @GetMapping("/users/{userId}")
  @Operation(operationId = "getUser", summary = "View a dashboard staff account (Director only)")
  public ResponseEntity<StaffMember> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
    return uncached(staff.get(users.from(jwt), userId));
  }

  @GetMapping("/staff")
  @Operation(operationId = "listAssignableStaff", summary = "List active assignment choices (Director or Financial Admin)")
  public ResponseEntity<List<StaffOption>> choices(@AuthenticationPrincipal Jwt jwt) {
    return uncached(staff.choices(users.from(jwt)));
  }

  @PostMapping("/users")
  @Operation(operationId = "createUser", summary = "Provision a registered staff account (Director only)")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<StaffMember> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateStaff request) {
    var user = staff.create(users.from(jwt), request.email(), request.displayName(), request.role());
    return ResponseEntity.created(URI.create("/api/v1/users/" + user.id()))
        .cacheControl(CacheControl.noStore()).body(user);
  }

  @PatchMapping("/users/{userId}")
  @Operation(operationId = "updateUser", summary = "Change a staff name, role or active status (Director only)")
  public ResponseEntity<StaffMember> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
      @Valid @RequestBody UpdateStaff request) {
    return uncached(staff.update(users.from(jwt), userId, request.displayName(), request.role(), request.active()));
  }

  private static <T> ResponseEntity<T> uncached(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
