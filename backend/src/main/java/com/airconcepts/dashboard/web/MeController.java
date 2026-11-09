package com.airconcepts.dashboard.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.staff.StaffRepository;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class MeController {

  private final CurrentUserResolver currentUsers;
  private final StaffRepository staff;

  public MeController(CurrentUserResolver currentUsers, StaffRepository staff) {
    this.currentUsers = currentUsers;
    this.staff = staff;
  }

  @GetMapping("/api/v1/me")
  @Operation(operationId = "getCurrentUser", summary = "Read the current active staff identity and server-side role")
  public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = currentUsers.from(jwt);
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    var profile = staff.findById(UUID.fromString(user.subject())).filter(person -> person.active())
        .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Active staff access is required."));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new MeResponse(user.subject(), profile.role(), profile.displayName()));
  }
}
