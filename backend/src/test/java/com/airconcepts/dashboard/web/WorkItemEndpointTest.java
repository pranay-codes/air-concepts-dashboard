package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.workitems.WorkItemReadService;
import com.airconcepts.dashboard.workitems.WorkItemActions;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.time.Instant;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class WorkItemEndpointTest {
  private final UUID entity = UUID.randomUUID(), item = UUID.randomUUID();
  private final String subject = UUID.randomUUID().toString();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;
  @MockitoBean WorkItemReadService reads;
  @MockitoBean WorkItemActions actions;

  private void as(Role role) {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(subject));
    when(roles.findRole(subject)).thenReturn(Optional.ofNullable(role));
    when(reads.get(any(), eq(entity), eq(item))).thenReturn(mock(WorkItemReadService.Detail.class));
    when(actions.edit(any(), any(), any(), anyInt(), anyBoolean(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(mock(WorkItemReadService.Detail.class));
    when(actions.closeNoInvoice(any(), any(), any(), anyInt(), any())).thenReturn(mock(WorkItemReadService.Detail.class));
  }
  private String path() { return "/api/v1/entities/" + entity + "/work-items/" + item; }
  private String auth() { return "Bearer " + TestJwts.TOKEN; }

  @Test void gl04_patchPassesOnlySuppliedFieldsAndReturnsNoStore() throws Exception {
    as(Role.STANDARD_USER);
    mvc.perform(patch(path()).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":2,\"notes\":\"Call\"}"))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    verify(entities).get(any(), eq(entity));
    verify(actions).edit(any(), eq(entity), eq(item), eq(2), eq(false), isNull(), eq(false), eq(true), eq("Call"));
    mvc.perform(patch(path()).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":2,\"followUpDate\":\"2026-11-25\",\"clearFollowUp\":true}"))
        .andExpect(status().isBadRequest());
    verify(actions, never()).edit(any(), any(), any(), anyInt(), eq(true), eq(LocalDate.of(2026,11,25)), eq(true), anyBoolean(), any());
  }

  @Test void gl05_and_gl20_financeActionsRequireRoleAndValidReason() throws Exception {
    as(Role.STANDARD_USER);
    mvc.perform(post(path()+"/no-invoice-needed").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":1,\"reason\":\"No charge\"}")).andExpect(status().isForbidden());
    verifyNoInteractions(actions);
    as(Role.FINANCIAL_ADMIN);
    mvc.perform(post(path()+"/close-lost").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":1,\"reason\":\" \"}")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    mvc.perform(post(path()+"/no-invoice-needed").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":1,\"reason\":\"No charge\"}")).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    verify(actions).closeNoInvoice(any(), eq(entity), eq(item), eq(1), eq("No charge"));
  }

  @Test void gl04_ownerChangeIsRejectedRatherThanIgnored() throws Exception {
    as(Role.DIRECTOR);
    mvc.perform(patch(path()).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":2,\"ownerId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    verifyNoInteractions(actions);
  }
  @Test void gl21_markSentRequiresFinanceAndPassesVersion() throws Exception {
    as(Role.STANDARD_USER);
    mvc.perform(post(path()+"/mark-sent").header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":2}")).andExpect(status().isForbidden());
    verifyNoInteractions(actions);
    as(Role.FINANCIAL_ADMIN);
    when(actions.markSent(any(),eq(entity),eq(item),eq(2))).thenReturn(mock(WorkItemReadService.Detail.class));
    mvc.perform(post(path()+"/mark-sent").header("Authorization",auth()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":2}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    verify(actions).markSent(any(),eq(entity),eq(item),eq(2));
  }

  @Test void gl06_detailRequiresActiveUserAndEntityValidation() throws Exception {
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    as(null);
    mvc.perform(get(path()).header("Authorization", auth())).andExpect(status().isForbidden());
    verifyNoInteractions(entities, reads);
    as(Role.STANDARD_USER);
    mvc.perform(get(path()).header("Authorization", auth())).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    verify(entities).get(any(), eq(entity));
    verify(reads).get(any(), eq(entity), eq(item));
  }

  @Test void gl01_detailOmitsOptionalNullFieldsForTheGeneratedClient() throws Exception {
    as(Role.STANDARD_USER);
    var summary = new WorkItemReadService.Summary(item, entity, "Example entity", UUID.randomUUID(), "Q-1", null,
        null, "SENT", null, null, WorkItemStage.QUOTED, null, null, null, Instant.EPOCH, 0,
        false, "FOLLOW_UP", Set.of(), Set.of(), 0);
    var view = new WorkItemReadService.Detail(summary, null, null,
        new WorkItemReadService.VworkPanel(null, null, null, null, null, null, null, "NOT_APPLICABLE", "NOT_CHECKED"),
        new WorkItemReadService.InvoicePanel(null, null, null, null, null, null, null),
        new WorkItemReadService.Links(null, null, null), List.of(), List.of(), Set.of());
    when(reads.get(any(), eq(entity), eq(item))).thenReturn(view);
    mvc.perform(get(path()).header("Authorization", auth())).andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.vwork.jobId").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.invoice.invoiceId").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.links.xeroQuoteUrl").doesNotHaveJsonPath());
  }

}
