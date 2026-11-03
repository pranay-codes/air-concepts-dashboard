package com.airconcepts.dashboard.security;

/** Application roles. Always looked up on the server through {@link RoleLookup}, never read from a token. */
public enum Role {
  DIRECTOR,
  FINANCIAL_ADMIN,
  STANDARD_USER
}
