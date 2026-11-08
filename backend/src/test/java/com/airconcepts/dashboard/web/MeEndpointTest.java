package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.staff.*;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeEndpointTest {

  private static final String SUBJECT = UUID.randomUUID().toString();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @MockitoBean
  private RoleLookup roleLookup;
  @MockitoBean private StaffRepository staff;

  private void profile(Role role) {
    when(staff.findById(UUID.fromString(SUBJECT))).thenReturn(Optional.of(new StaffMember(
        UUID.fromString(SUBJECT), "staff@example.invalid", "Dashboard staff", role, true)));
  }

  @Test
  void rejectsRequestWithoutToken() throws Exception {
    mockMvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInvalidToken() throws Exception {
    when(jwtDecoder.decode("bad-token")).thenThrow(new BadJwtException("invalid signature"));

    mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, "Bearer bad-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void returnsSubjectAndServerSideRoleForValidToken() throws Exception {
    profile(Role.FINANCIAL_ADMIN);
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(SUBJECT));
    when(roleLookup.findRole(SUBJECT)).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));

    mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value(SUBJECT))
        .andExpect(jsonPath("$.role").value("FINANCIAL_ADMIN"))
        .andExpect(jsonPath("$.displayName").value("Dashboard staff"));
  }

  @Test
  void ignoresRoleClaimInTokenAndUsesServerLookup() throws Exception {
    profile(Role.STANDARD_USER);
    when(jwtDecoder.decode(TestJwts.TOKEN))
        .thenReturn(TestJwts.forSubject(SUBJECT, Map.of("role", "DIRECTOR")));
    when(roleLookup.findRole(SUBJECT)).thenReturn(Optional.of(Role.STANDARD_USER));

    mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("STANDARD_USER"));
  }

  @Test
  void frUc15_18_inactiveOrUnregisteredSessionCannotReadProtectedProfile() throws Exception {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(SUBJECT));
    when(roleLookup.findRole(anyString())).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isForbidden());
  }

  @Test
  void frUc15_18_existingJwtLosesAccessAsSoonAsServerRoleIsRevoked() throws Exception {
    profile(Role.STANDARD_USER);
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(SUBJECT));
    when(roleLookup.findRole(SUBJECT)).thenReturn(Optional.of(Role.STANDARD_USER), Optional.empty());
    mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isForbidden());
  }
}
