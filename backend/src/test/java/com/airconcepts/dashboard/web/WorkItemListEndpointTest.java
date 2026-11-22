package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.workitems.WorkItemListService;
import com.airconcepts.dashboard.workitems.WorkItemReadService;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
class WorkItemListEndpointTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;
  @MockitoBean WorkItemListService work;
  private final String subject = UUID.randomUUID().toString();
  private void active() {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.of(Role.STANDARD_USER));
  }
  private String auth() { return "Bearer " + TestJwts.TOKEN; }

  @Test void gl01_activeUserGetsNoStorePageAndOptionalEntityIsChecked() throws Exception {
    active();
    var entity = UUID.randomUUID();
    when(work.list(any(), any())).thenReturn(new WorkItemListService.Page(List.of(), 1, 25, 0, 0));
    mvc.perform(get("/api/v1/work-items").header("Authorization", auth()).param("entityId", entity.toString())
        .param("search", "J%1")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.total").value(0));
    verify(entities).get(any(), eq(entity));
    verify(work).list(any(), argThat(q -> q.entityId().equals(entity) && q.search().equals("J%1") && q.pageSize()==25));
  }
  @Test void gl01_invalidFiltersAndInactiveUsersAreRejected() throws Exception {
    mvc.perform(get("/api/v1/work-items")).andExpect(status().isUnauthorized());
    active();
    mvc.perform(get("/api/v1/work-items").header("Authorization", auth()).param("pageSize", "101"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WORK_ITEM_QUERY"));
    mvc.perform(get("/api/v1/work-items").header("Authorization", auth()).param("sort", "injected"))
        .andExpect(status().isBadRequest());
    when(roles.findRole(subject)).thenReturn(Optional.empty());
    mvc.perform(get("/api/v1/work-items").header("Authorization", auth())).andExpect(status().isForbidden());
    verifyNoInteractions(work);
  }

  @Test void gl01_optionalSummaryFieldsAreOmittedAsDescribedByContract() throws Exception {
    active();
    var entity = UUID.randomUUID();
    var summary = new WorkItemReadService.Summary(UUID.randomUUID(), entity, "Example entity", UUID.randomUUID(),
        "Q-1", null, null, "SENT", null, null, WorkItemStage.QUOTED, null, null, null,
        Instant.EPOCH, 0, false, "FOLLOW_UP", Set.of("JOB_ID_MISSING"), Set.of(), 0);
    when(work.list(any(), any())).thenReturn(new WorkItemListService.Page(List.of(summary), 1, 25, 1, 1));
    mvc.perform(get("/api/v1/work-items").header("Authorization", auth()).param("entityId", entity.toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].entityId").value(entity.toString()))
        .andExpect(jsonPath("$.items[0].jobId").doesNotHaveJsonPath()).andExpect(jsonPath("$.items[0].owner").doesNotHaveJsonPath());
  }
}
