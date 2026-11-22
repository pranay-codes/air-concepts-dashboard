package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.security.RoleLookup;
import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.vwork.VworkTemplatesRepository;
import com.airconcepts.dashboard.workitems.operations.*;
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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class VworkJobEndpointTest {
  private final UUID entity=UUID.randomUUID(), item=UUID.randomUUID(), actor=UUID.randomUUID(), operation=UUID.randomUUID();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;
  @MockitoBean VworkJobDraftService drafts;
  @MockitoBean CreateJobService jobs;
  @MockitoBean VworkOperationReconciler reconciler;
  private void as(Role role) {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(actor.toString()));
    when(roles.findRole(actor.toString())).thenReturn(Optional.ofNullable(role));
  }
  private String path() { return "/api/v1/entities/"+entity+"/work-items/"+item; }
  private String body() { return """
      {"version":4,"reference":"J-1","templateId":"template-a","description":"Service work",
       "customer":"Synthetic customer","contactPerson":"Alex","phone":"555123","siteAddress":"Example Street",
       "notes":"","scope":[{"description":"Install","quantity":"1"}]}
      """; }
  @Test void gl12_gl13_readsPriceFreeDraftAndSubmitsJob() throws Exception {
    as(Role.DIRECTOR);
    when(drafts.draft(any(),eq(entity),eq(item))).thenReturn(new VworkJobDraftService.Draft(entity,item,4,"J-1","Q-1",
        "Synthetic customer","Alex","555123",null,List.of(new VworkClient.ScopeLine("Install","1")),
        List.of(new VworkTemplatesRepository.Template("template-a",true)),"template-a",Set.of("siteAddress")));
    when(jobs.submit(any(),eq(entity),eq(item),eq(4),any())).thenReturn(new CreateJobService.Result(operation,OperationState.Result.CREATED));
    mvc.perform(get(path()+"/job-draft").header("Authorization","Bearer "+TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.scope[0].description").value("Install"))
        .andExpect(jsonPath("$.templates[0].isDefault").value(true))
        .andExpect(jsonPath("$.scope[0].unitAmount").doesNotExist());
    mvc.perform(post(path()+"/vwork-job").header("Authorization","Bearer "+TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content(body()))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.operationId").value(operation.toString()));
    verify(jobs).submit(any(),eq(entity),eq(item),eq(4),argThat(job -> "Example Street".equals(job.siteAddress())));
  }
  @Test void gl26_rejectsMissingSiteAndUnexpectedPriceFields() throws Exception {
    as(Role.DIRECTOR);
    for (String value : List.of(body().replace("Example Street", ""),body().replace("\"quantity\":\"1\"", "\"quantity\":\"1\",\"unitAmount\":123"))) {
      mvc.perform(post(path()+"/vwork-job").header("Authorization","Bearer "+TestJwts.TOKEN)
          .contentType(MediaType.APPLICATION_JSON).content(value)).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(jobs);
  }
  @Test void gl06_inactiveAndUnauthenticatedCannotAccessDraftOrCreate() throws Exception {
    as(null);
    mvc.perform(get(path()+"/job-draft").header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isForbidden());
    mvc.perform(post(path()+"/vwork-job").header("Authorization","Bearer "+TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
    mvc.perform(get(path()+"/job-draft")).andExpect(status().isUnauthorized());
    verifyNoInteractions(drafts,jobs);
  }
  @Test void gl22_lookupIsEntityScopedAndReturnsOutcome() throws Exception {
    as(Role.DIRECTOR);
    when(reconciler.lookup(any(),eq(entity),eq(item),eq(operation))).thenReturn(OperationState.Result.LINKED_EXISTING);
    mvc.perform(post(path()+"/operations/"+operation+"/lookup").header("Authorization","Bearer "+TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.outcome").value("LINKED_EXISTING"));
    verify(entities).get(any(),eq(entity));
  }
  @Test void gl22_retryPassesPriorOperationAndReviewedDraft() throws Exception {
    as(Role.DIRECTOR);
    when(jobs.retry(any(),eq(entity),eq(item),eq(4),any(),eq(operation)))
        .thenReturn(new CreateJobService.Result(UUID.randomUUID(),OperationState.Result.CREATED));
    mvc.perform(post(path()+"/operations/"+operation+"/retry").header("Authorization","Bearer "+TestJwts.TOKEN)
        .contentType(MediaType.APPLICATION_JSON).content(body()))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andExpect(jsonPath("$.outcome").value("CREATED"));
    verify(jobs).retry(any(),eq(entity),eq(item),eq(4),argThat(job -> "Example Street".equals(job.siteAddress())),eq(operation));
  }
}
