package com.airconcepts.dashboard.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.Entity;
import com.airconcepts.dashboard.entities.EntityRepository;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntityEndpointTest {
  private static final String SUBJECT = UUID.randomUUID().toString();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityRepository entities;

  private void as(Role role) {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(SUBJECT));
    when(roles.findRole(SUBJECT)).thenReturn(Optional.ofNullable(role));
  }

  @Test
  void frUc15_12_30_allActiveRolesCanListZeroOrMoreThanThreeEntities() throws Exception {
    var four = List.of(new Entity(UUID.randomUUID(), "One"), new Entity(UUID.randomUUID(), "Two"),
        new Entity(UUID.randomUUID(), "Three"), new Entity(UUID.randomUUID(), "Additional"));
    when(entities.findAll()).thenReturn(List.of(), four);
    as(Role.STANDARD_USER);
    mvc.perform(get("/api/v1/entities").header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/v1/entities").header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[3].displayName").value("Additional"));
  }

  @Test
  void frUc15_01_directorCanCreateEntityAndReceivesLocation() throws Exception {
    as(Role.DIRECTOR);
    var id = UUID.randomUUID();
    when(entities.create("New business")).thenReturn(new Entity(id, "New business"));
    mvc.perform(post("/api/v1/entities").header("Authorization", "Bearer " + TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"  New business  \"}"))
        .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/entities/" + id))
        .andExpect(jsonPath("$.id").value(id.toString()));
    verify(entities).create("New business");
  }

  @Test
  void frUc15_01_otherRolesCannotCreateEvenWithValidJwt() throws Exception {
    for (var role : List.of(Role.STANDARD_USER, Role.FINANCIAL_ADMIN)) {
      as(role);
      mvc.perform(post("/api/v1/entities").header("Authorization", "Bearer " + TestJwts.TOKEN)
          .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New\"}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(entities);
  }

  @Test
  void frUc15_18_inactiveAndUnknownUsersCannotReadEntities() throws Exception {
    as(null);
    mvc.perform(get("/api/v1/entities").header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isForbidden());
    verifyNoInteractions(entities);
  }

  @Test
  void frUc15_30_entityDetailChecksExistenceAndUsesStableUuid() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    var id = UUID.randomUUID();
    when(entities.findById(id)).thenReturn(Optional.of(new Entity(id, "New entity")), Optional.empty());
    mvc.perform(get("/api/v1/entities/" + id).header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.id").value(id.toString()));
    mvc.perform(get("/api/v1/entities/" + id).header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isNotFound());
  }

  @Test
  void frUc15_01_18_noBearerCannotListOrCreate() throws Exception {
    mvc.perform(get("/api/v1/entities")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/entities").contentType(MediaType.APPLICATION_JSON)
        .content("{\"displayName\":\"New\"}")).andExpect(status().isUnauthorized());
    verifyNoInteractions(entities);
  }

  @Test
  void frUc15_02_invalidDisplayNameNeverReachesRepository() throws Exception {
    as(Role.DIRECTOR);
    for (String name : List.of("", " ", "a".repeat(161))) {
      mvc.perform(post("/api/v1/entities").header("Authorization", "Bearer " + TestJwts.TOKEN)
          .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"" + name + "\"}"))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(entities);
  }
}
