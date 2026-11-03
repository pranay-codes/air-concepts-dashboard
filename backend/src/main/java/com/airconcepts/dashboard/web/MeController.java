package com.airconcepts.dashboard.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.CurrentUserResolver;

@RestController
public class MeController {

  private final CurrentUserResolver currentUsers;

  public MeController(CurrentUserResolver currentUsers) {
    this.currentUsers = currentUsers;
  }

  @GetMapping("/api/v1/me")
  public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = currentUsers.from(jwt);
    return new MeResponse(user.subject(), user.role());
  }
}
