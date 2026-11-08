package com.airconcepts.dashboard.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.xero.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceEndpointTest {
  private static final String SUBJECT = UUID.randomUUID().toString();
  private final UUID entity = UUID.randomUUID();
  private final TenantAccess access = new TenantAccess(entity, UUID.randomUUID(), UUID.randomUUID(), "synthetic-access");
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean XeroConnectionService connections;
  @MockitoBean XeroInvoiceClient provider;
  @MockitoBean XeroReadHealth health;
  @MockitoBean InvoiceAssignmentRepository assignments;

  private void as(Role role) {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(SUBJECT));
    when(roles.findRole(SUBJECT)).thenReturn(Optional.ofNullable(role));
    when(connections.authorisedAccess(any(), eq(entity))).thenReturn(access);
  }
  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder list() {
    return get("/api/v1/entities/" + entity + "/invoices").header("Authorization", "Bearer " + TestJwts.TOKEN);
  }

  @Test void frUc15_08_26_defaultReturnsBoundedAllDatesAllStatusesAndNoStore() throws Exception {
    as(Role.STANDARD_USER);
    var defaults = InvoiceQuery.defaults(InvoiceType.ACCREC);
    when(provider.list(access, defaults)).thenReturn(new InvoiceViews.Page(List.of(), 1, 25, 0, 0));
    mvc.perform(list()).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.itemCount").value(0));
    verify(provider).list(access, defaults);
  }

  @Test void frUc15_13_supplierBillsAreForbiddenForStandardUserAndRevokedSession() throws Exception {
    for (Role role : Arrays.asList(Role.STANDARD_USER, null)) {
      as(role);
      mvc.perform(list().param("type", "ACCPAY")).andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/entities/" + entity + "/invoices/" + UUID.randomUUID())
          .param("type", "ACCPAY").header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    }
    verifyNoInteractions(provider, health);
    verify(connections, never()).authorisedAccess(any(), any());
  }

  @Test void frUc15_08_27_29_combinesFiltersBeforePaging() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    var query = new InvoiceQuery(InvoiceType.ACCPAY, "Acme", InvoiceStatus.AUTHORISED, true,
        InvoiceQuery.DateField.DUE_DATE, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-11-07"),
        InvoiceQuery.Sort.CONTACT, InvoiceQuery.Direction.ASC, 2, 10);
    when(provider.list(access, query)).thenReturn(new InvoiceViews.Page(List.of(), 2, 10, 1, 0));
    mvc.perform(list().param("type", "ACCPAY").param("search", "Acme").param("status", "AUTHORISED")
        .param("overdue", "true").param("dateField", "DUE_DATE").param("from", "2026-01-01")
        .param("to", "2026-11-07").param("sort", "CONTACT").param("direction", "ASC").param("page", "2").param("pageSize", "10"))
        .andExpect(status().isOk());
    verify(provider).list(access, query);
  }

  @Test void frUc15_26_invalidQueryFailsBeforeAnyProviderCall() throws Exception {
    as(Role.DIRECTOR);
    for (var pair : List.of(new String[]{"page", "0"}, new String[]{"pageSize", "101"},
        new String[]{"search", "x".repeat(201)}, new String[]{"sort", "raw expression"}, new String[]{"from", "invalid"})) {
      mvc.perform(list().param(pair[0], pair[1])).andExpect(status().isBadRequest());
    }
    mvc.perform(list().param("from", "2026-02-02").param("to", "2026-01-01")).andExpect(status().isBadRequest());
    verifyNoInteractions(provider, health);
  }

  @Test void frUc15_30_failureHasSafeProblemDetailAndRetryHeader() throws Exception {
    as(Role.DIRECTOR);
    when(provider.list(any(), any())).thenThrow(new ProviderFailure("XERO_RATE_LIMITED", "Wait before retrying.", 429, 60));
    mvc.perform(list()).andExpect(status().isBadGateway()).andExpect(header().string("Retry-After", "60"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("XERO_RATE_LIMITED")).andExpect(jsonPath("$.upstreamStatus").value(429));
  }

  @Test void frUc15_07_32_assignmentRouteRejectsStandardUsersAndBillsBeforeTransport() throws Exception {
    for (Role role : List.of(Role.STANDARD_USER, Role.DIRECTOR)) {
      as(role);
      mvc.perform(put("/api/v1/entities/" + entity + "/invoices/" + UUID.randomUUID() + "/assignment")
          .param("type", role == Role.DIRECTOR ? "ACCPAY" : "ACCREC")
          .header("Authorization", "Bearer " + TestJwts.TOKEN).contentType("application/json").content("{\"assigneeId\":null}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(provider, assignments, health);
  }

  @Test void frUc15_07_directorAndAdminCanUnassignOnlyAfterDocumentVerification() throws Exception {
    var invoice = UUID.randomUUID();
    for (Role role : List.of(Role.DIRECTOR, Role.FINANCIAL_ADMIN)) {
      as(role);
      mvc.perform(put("/api/v1/entities/" + entity + "/invoices/" + invoice + "/assignment")
          .header("Authorization", "Bearer " + TestJwts.TOKEN).contentType("application/json").content("{\"assigneeId\":null}"))
          .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.invoiceId").value(invoice.toString()));
    }
    verify(provider, times(2)).detail(access, InvoiceType.ACCREC, invoice);
    verify(assignments, times(2)).assign(access, UUID.fromString(SUBJECT), invoice, null);
  }

  @Test void frUc15_19_32_salesIncludesInactiveHistoricalOwnerAndBillsHaveNoOwner() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    var id = UUID.randomUUID(); var staff = UUID.randomUUID();
    var summary = new InvoiceViews.Summary(id, InvoiceType.ACCREC, "INV-1", null, "Acme", LocalDate.of(2026,11,1),
        null, null, null, "NZD", InvoiceStatus.DRAFT, false, null);
    var page = new InvoiceViews.Page(List.of(summary), 1, 25, 1, 1);
    when(provider.list(access, InvoiceQuery.defaults(InvoiceType.ACCREC))).thenReturn(page);
    when(assignments.owners(entity, List.of(id))).thenReturn(Map.of(id, new InvoiceViews.Owner(staff, "Previous staff", false)));
    mvc.perform(list()).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].owner.displayName").value("Previous staff"))
        .andExpect(jsonPath("$.items[0].owner.active").value(false));
    var bill = new InvoiceViews.Summary(id, InvoiceType.ACCPAY, "BILL-1", null, "Acme", summary.date(), null,
        null, null, "NZD", InvoiceStatus.DRAFT, false, null);
    when(provider.list(access, InvoiceQuery.defaults(InvoiceType.ACCPAY))).thenReturn(new InvoiceViews.Page(List.of(bill), 1,25,1,1));
    mvc.perform(list().param("type", "ACCPAY")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].owner").doesNotExist());
    verify(assignments, times(1)).owners(any(), any());
  }

  @Test void frUc15_06_financialJsonPreservesCentsBeyondJavascriptNumberPrecision() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    var summary = new InvoiceViews.Summary(UUID.randomUUID(), InvoiceType.ACCPAY, "BILL-2", null, "Acme",
        LocalDate.of(2026,11,1), null, new java.math.BigDecimal("9007199254740993.01"), null, "NZD", InvoiceStatus.DRAFT, false, null);
    when(provider.list(access, InvoiceQuery.defaults(InvoiceType.ACCPAY))).thenReturn(new InvoiceViews.Page(List.of(summary),1,25,1,1));
    mvc.perform(list().param("type", "ACCPAY")).andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].total").value("9007199254740993.01"))
        .andExpect(jsonPath("$.items[0].total").isString());
  }
}
