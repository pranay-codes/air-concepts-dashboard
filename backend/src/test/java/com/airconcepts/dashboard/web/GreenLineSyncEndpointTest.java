package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.workitems.sync.GreenLineSyncService;
import com.airconcepts.dashboard.workitems.sync.GreenLineSyncViews;
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
class GreenLineSyncEndpointTest {
  private final UUID entity = UUID.randomUUID();
  private final String subject = UUID.randomUUID().toString();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean GreenLineSyncService service;
  private String path() { return "/api/v1/entities/" + entity + "/green-line/sync"; }
  private void as(Role role) {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.ofNullable(role));
  }
  @Test void gl25_readsAndRefreshesNoStoreStatus() throws Exception {
    as(Role.DIRECTOR);
    var empty = new GreenLineSyncViews.SystemStatus(null, null, null);
    when(service.status(any(), eq(entity))).thenReturn(new GreenLineSyncViews.Status(entity, empty, empty));
    mvc.perform(get(path()).header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.entityId").value(entity.toString()));
    mvc.perform(post(path()).header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    verify(service).runManual(any(), eq(entity));
  }
  @Test void gl25_inactiveAndUnauthenticatedCannotReadOrRefresh() throws Exception {
    as(null);
    mvc.perform(get(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    mvc.perform(post(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);
  }
  @Test void gl25_refreshReportsConflictAndRateLimit() throws Exception {
    as(Role.DIRECTOR);
    doThrow(new ApiFailure(org.springframework.http.HttpStatus.CONFLICT, "SYNC_IN_PROGRESS", "A sync is already running."))
        .doThrow(new ApiFailure(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "SYNC_RATE_LIMITED", "Wait before retrying."))
        .when(service).runManual(any(), eq(entity));
    mvc.perform(post(path()).header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYNC_IN_PROGRESS"));
    mvc.perform(post(path()).header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SYNC_RATE_LIMITED"));
  }
}
