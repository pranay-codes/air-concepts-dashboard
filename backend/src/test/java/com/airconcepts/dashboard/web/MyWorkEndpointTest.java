package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.workitems.MyWorkService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class MyWorkEndpointTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;
  @MockitoBean MyWorkService work;
  private final String subject = UUID.randomUUID().toString();

  @Test void gl10_activeUserGetsBoundedNoStoreQueueAndOptionalEntityIsValidated() throws Exception {
    var entity = UUID.randomUUID();
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.of(Role.STANDARD_USER));
    when(work.get(any(), eq(entity))).thenReturn(new MyWorkService.MyWork(List.of(), List.of(), List.of(), Instant.EPOCH, true));
    mvc.perform(get("/api/v1/work-items/my-work").param("entityId", entity.toString())
        .header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.truncated").value(true));
    verify(entities).get(any(), eq(entity));
    verify(work).get(any(), eq(entity));
  }

  @Test void gl06_unauthenticatedOrInactiveUserCannotRead() throws Exception {
    mvc.perform(get("/api/v1/work-items/my-work")).andExpect(status().isUnauthorized());
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.empty());
    mvc.perform(get("/api/v1/work-items/my-work").header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isForbidden());
    verifyNoInteractions(work, entities);
  }
}
