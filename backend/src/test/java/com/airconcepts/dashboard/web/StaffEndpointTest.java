package com.airconcepts.dashboard.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.staff.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffEndpointTest {
  private static final UUID CALLER = UUID.randomUUID();
  private static final UUID ID = UUID.randomUUID();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean StaffRepository users;
  @MockitoBean SupabaseAdminClient auth;

  private void as(Role role) {
    when(decoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(CALLER.toString()));
    when(roles.findRole(CALLER.toString())).thenReturn(Optional.ofNullable(role));
  }

  @Test
  void frUc15_16_directorCreatesStaffWithoutReceivingAuthSecrets() throws Exception {
    as(Role.DIRECTOR);
    var draft = new StaffProvision(ID, "staff@example.invalid", "Staff", Role.STANDARD_USER);
    when(users.reserve(draft.email(), draft.displayName(), draft.role())).thenReturn(draft);
    when(users.complete(draft)).thenReturn(member(true));
    mvc.perform(post("/api/v1/users").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"staff@example.invalid\",\"displayName\":\"Staff\",\"role\":\"STANDARD_USER\"}"))
        .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/users/" + ID))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.id").value(ID.toString())).andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.access_token").doesNotExist());
    verify(auth).ensureUser(ID, draft.email());
  }

  @Test
  void frUc15_16_onlyDirectorCanReadTheUserDirectory() throws Exception {
    when(users.findAll()).thenReturn(List.of(member(false)));
    as(Role.DIRECTOR);
    mvc.perform(get("/api/v1/users").header("Authorization", bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
    as(Role.FINANCIAL_ADMIN);
    mvc.perform(get("/api/v1/users").header("Authorization", bearer())).andExpect(status().isForbidden());
    as(Role.STANDARD_USER);
    mvc.perform(get("/api/v1/users").header("Authorization", bearer())).andExpect(status().isForbidden());
    verify(users, times(1)).findAll();
  }

  @Test
  void frUc15_16_nonDirectorAndInactiveUsersCannotCreateOrUpdate() throws Exception {
    for (Role role : Arrays.asList(Role.FINANCIAL_ADMIN, Role.STANDARD_USER, null)) {
      as(role);
      mvc.perform(post("/api/v1/users").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"staff@example.invalid\",\"displayName\":\"Staff\",\"role\":\"DIRECTOR\"}"))
          .andExpect(status().isForbidden());
      mvc.perform(patch("/api/v1/users/" + ID).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
          .content("{\"displayName\":\"Staff\",\"role\":\"DIRECTOR\",\"active\":true}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(users, auth);
  }

  @Test
  void frUc15_18_directorCanDeactivateWithoutDeletingTheUser() throws Exception {
    as(Role.DIRECTOR);
    when(users.findById(ID)).thenReturn(Optional.of(member(true)), Optional.of(member(false)));
    when(users.update(CALLER, ID, "Staff", Role.STANDARD_USER, false)).thenReturn(member(false));
    mvc.perform(patch("/api/v1/users/" + ID).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"displayName\":\"Staff\",\"role\":\"STANDARD_USER\",\"active\":false}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    verify(auth).setActive(ID, false);
  }

  @Test
  void frUc15_18_providerFailureDoesNotBecomeSuccessOrLeakItsPayload() throws Exception {
    as(Role.DIRECTOR);
    when(users.findById(ID)).thenReturn(Optional.of(member(true)));
    when(users.update(CALLER, ID, "Staff", Role.STANDARD_USER, false)).thenReturn(member(false));
    doThrow(new ProviderFailure("AUTH_PROVIDER_FAILED", "Auth could not confirm this change. Refresh Settings and retry.", 503))
        .when(auth).setActive(ID, false);
    mvc.perform(patch("/api/v1/users/" + ID).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"displayName\":\"Staff\",\"role\":\"STANDARD_USER\",\"active\":false}"))
        .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AUTH_PROVIDER_FAILED"))
        .andExpect(jsonPath("$.upstreamStatus").value(503));
    verify(users).update(CALLER, ID, "Staff", Role.STANDARD_USER, false);
  }

  @Test
  void frUc15_19_assignmentChoicesExposeNamesWithoutStaffEmails() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    when(users.activeChoices()).thenReturn(List.of(new StaffOption(ID, "Staff")));
    mvc.perform(get("/api/v1/staff").header("Authorization", bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].displayName").value("Staff"))
        .andExpect(jsonPath("$[0].email").doesNotExist());
    as(Role.STANDARD_USER);
    mvc.perform(get("/api/v1/staff").header("Authorization", bearer())).andExpect(status().isForbidden());
  }

  @Test
  void frUc15_16_invalidRoleMissingActivationOrBlankNameCannotMutateStaff() throws Exception {
    as(Role.DIRECTOR);
    for (String body : List.of("{\"displayName\":\"Staff\",\"role\":\"OWNER\",\"active\":true}",
        "{\"displayName\":\"Staff\",\"role\":\"DIRECTOR\"}",
        "{\"displayName\":\" \",\"role\":\"DIRECTOR\",\"active\":true}")) {
      mvc.perform(patch("/api/v1/users/" + ID).header("Authorization", bearer())
          .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(users, auth);
  }

  private String bearer() { return "Bearer " + TestJwts.TOKEN; }
  private StaffMember member(boolean active) {
    return new StaffMember(ID, "staff@example.invalid", "Staff", Role.STANDARD_USER, active);
  }
}
