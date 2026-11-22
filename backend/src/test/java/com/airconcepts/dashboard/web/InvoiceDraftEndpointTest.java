package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.workitems.operations.InvoiceDraftService;
import com.airconcepts.dashboard.workitems.operations.PrepareInvoiceService;
import com.airconcepts.dashboard.workitems.operations.OperationState;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InvoiceDraftEndpointTest {
  private final UUID entity=UUID.randomUUID(),item=UUID.randomUUID(),actor=UUID.randomUUID();
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;
  @MockitoBean InvoiceDraftService drafts;
  @MockitoBean PrepareInvoiceService prepare;
  private String path() { return "/api/v1/entities/"+entity+"/work-items/"+item+"/invoice-draft"; }
  private void as(Role role) {
    when(decoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(actor.toString()));
    when(roles.findRole(actor.toString())).thenReturn(Optional.of(role));
  }
  @Test void gl17_financeCanReviewServerResolvedQuote() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    when(drafts.review(any(),eq(entity),eq(item))).thenReturn(new InvoiceDraftService.Review(entity,item,4,"Q-1","Example Xero","Customer","J-1 / Q-1",2,"123.45","NZD","a".repeat(64)));
    mvc.perform(get(path()).header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.total").value("123.45"));
    verify(entities).get(any(),eq(entity));
  }
  @Test void gl06_standardUserAndUnauthenticatedCannotReview() throws Exception {
    as(Role.STANDARD_USER);
    mvc.perform(get(path()).header("Authorization","Bearer "+TestJwts.TOKEN)).andExpect(status().isForbidden());
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    verifyNoInteractions(drafts,entities);
  }
  @Test void gl17_financeSubmitsOnlyTheReviewedVersionAndFingerprint() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    var operation=UUID.randomUUID();
    when(prepare.submit(any(),eq(entity),eq(item),eq(4),eq("a".repeat(64))))
        .thenReturn(new PrepareInvoiceService.Result(operation,OperationState.Result.CREATED));
    mvc.perform(post("/api/v1/entities/"+entity+"/work-items/"+item+"/invoice")
        .header("Authorization","Bearer "+TestJwts.TOKEN).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .content("{\"version\":4,\"fingerprint\":\""+"a".repeat(64)+"\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.operationId").value(operation.toString()));
  }
}
