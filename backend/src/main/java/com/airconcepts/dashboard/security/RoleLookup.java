package com.airconcepts.dashboard.security;

import java.util.Optional;

/** Server-side source of a user's role. Replace the default bean when role storage exists. */
public interface RoleLookup {

  Optional<Role> findRole(String subject);
}
