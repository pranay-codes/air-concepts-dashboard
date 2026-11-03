package com.airconcepts.dashboard.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CurrentUserTest {

  @Test
  void allowsListedRole() {
    CurrentUser user = new CurrentUser("user-1", Role.DIRECTOR);

    assertThatCode(() -> user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN)).doesNotThrowAnyException();
  }

  @Test
  void deniesUnlistedRole() {
    CurrentUser user = new CurrentUser("user-1", Role.STANDARD_USER);

    assertThatThrownBy(() -> user.requireRole(Role.DIRECTOR))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void deniesUserWithNoRole() {
    CurrentUser user = new CurrentUser("user-1", null);

    assertThatThrownBy(() -> user.requireRole(Role.DIRECTOR))
        .isInstanceOf(AccessDeniedException.class);
  }
}
