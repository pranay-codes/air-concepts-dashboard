package com.airconcepts.dashboard.web;

import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.workitems.operations.*;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Exercises the real HTTP controller, submission service and mock provider with mocked persistence. */
@SpringBootTest(properties="dashboard.green-line.xero.invoice-mode=mock") @AutoConfigureMockMvc @ActiveProfiles("test")
class InvoiceMockFlowEndpointTest {
  private final UUID entity=UUID.randomUUID(),item=UUID.randomUUID(),tenant=UUID.randomUUID(),grant=UUID.randomUUID(),quote=UUID.randomUUID(),actor=UUID.randomUUID(),operation=UUID.randomUUID();
  @Autowired MockMvc mvc;
  @Autowired XeroQuoteClient quoteClient;
  @Autowired XeroContactClient contactClient;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean EntityService entities;
  @MockitoBean XeroConnectionService connections;
  @MockitoBean InvoiceDraftService drafts;
  @MockitoBean InvoiceReservation reservations;
  @MockitoBean InvoiceOperationCompletion completion;
  @Test void gl17_reviewAndSubmitReachTheMockProvider() throws Exception {
    assertThat(quoteClient).isInstanceOf(MockXeroQuoteClient.class);
    assertThat(contactClient).isInstanceOf(MockXeroContactClient.class);
    when(decoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(actor.toString()));
    when(roles.findRole(actor.toString())).thenReturn(Optional.of(Role.FINANCIAL_ADMIN));
    var context=new InvoiceDraftContext.Context(quote,"Q-1","J-1",4,tenant,grant,"Example Xero");
    var access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read","accounting.invoices"));
    var draft=new DraftInvoice(UUID.randomUUID(),"Customer","Q-1","J-1 / Q-1",LocalDate.of(2026,11,21),"NZD","Exclusive",null,BigDecimal.TEN,
        List.of(new DraftInvoice.Line("Work",BigDecimal.ONE,BigDecimal.TEN,null,"200","OUTPUT2",null,List.of())));
    when(drafts.review(any(),eq(entity),eq(item))).thenReturn(new InvoiceDraftService.Review(entity,item,4,"Q-1","Example Xero","Customer",draft.reference(),1,"10","NZD",draft.fingerprint()));
    when(drafts.prepare(any(),eq(entity),eq(item))).thenReturn(new InvoiceDraftService.Prepared(context,access,draft));
    when(connections.authorisedAccess(any(),eq(entity))).thenReturn(access);
    when(reservations.reserve(any(),eq(entity),eq(item),eq(context),eq(draft.fingerprint())))
        .thenReturn(new OperationReservationService.Reservation(operation,1));
    var path="/api/v1/entities/"+entity+"/work-items/"+item;
    mvc.perform(get(path+"/invoice-draft").header("Authorization","Bearer "+TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(jsonPath("$.fingerprint").value(draft.fingerprint()));
    mvc.perform(post(path+"/invoice").header("Authorization","Bearer "+TestJwts.TOKEN).contentType(MediaType.APPLICATION_JSON)
        .content("{\"version\":4,\"fingerprint\":\""+draft.fingerprint()+"\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("CREATED"));
    verify(completion).complete(eq(entity),eq(item),eq(context),eq(operation),argThat(invoice -> invoice.id()!=null
        && "J-1 / Q-1".equals(invoice.reference())),eq(OperationState.Result.CREATED));
  }
}
