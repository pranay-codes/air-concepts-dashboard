package com.airconcepts.dashboard.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.airconcepts.dashboard.TestJwts;
import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.xero.*;
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
class AttachmentEndpointTest {
  private static final String SUBJECT = UUID.randomUUID().toString();
  private final UUID entity = UUID.randomUUID(), invoice = UUID.randomUUID(), attachment = UUID.randomUUID();
  private final TenantAccess access = new TenantAccess(entity, UUID.randomUUID(), UUID.randomUUID(), "synthetic-access");
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean RoleLookup roles;
  @MockitoBean XeroConnectionService connections;
  @MockitoBean XeroInvoiceClient provider;
  @MockitoBean XeroAttachmentClient files;
  @MockitoBean XeroReadHealth health;
  private String path() { return "/api/v1/entities/" + entity + "/invoices/" + invoice + "/attachments"; }
  private void as(Role role) {
    when(jwtDecoder.decode(TestJwts.TOKEN)).thenReturn(TestJwts.forSubject(SUBJECT));
    when(roles.findRole(SUBJECT)).thenReturn(Optional.ofNullable(role));
    when(connections.authorisedAccess(any(), eq(entity))).thenReturn(access);
  }

  @Test void frUc15_21_25_standardAndInactiveUsersCannotReadBillAttachmentMetadataOrBytes() throws Exception {
    for (Role role : Arrays.asList(Role.STANDARD_USER, null)) {
      as(role);
      mvc.perform(get(path()).param("type", "ACCPAY").header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
      mvc.perform(get(path() + "/" + attachment + "/file").param("type", "ACCPAY")
          .header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isForbidden());
    }
    verifyNoInteractions(provider, files, health);
    verify(connections, never()).authorisedAccess(any(), any());
  }

  @Test void frUc15_24_25_previewsHaveNoStoreNosniffAndSandboxHeaders() throws Exception {
    as(Role.STANDARD_USER);
    when(files.file(access, invoice, attachment)).thenReturn(new AttachmentViews.File("report.pdf", "application/pdf", new byte[]{1,2}, true));
    mvc.perform(get(path() + "/" + attachment + "/file").param("preview", "true")
        .header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"));
    var order = inOrder(provider, files);
    order.verify(provider).detail(access, InvoiceType.ACCREC, invoice);
    order.verify(files).file(access, invoice, attachment);
  }

  @Test void frUc15_24_unsupportedContentAndExplicitDownloadsUseAttachmentDisposition() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    when(files.file(access, invoice, attachment)).thenReturn(new AttachmentViews.File("document.html", "application/octet-stream", new byte[]{1}, false));
    mvc.perform(get(path() + "/" + attachment + "/file").param("type", "ACCPAY").param("preview", "true")
        .header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
    verify(provider).detail(access, InvoiceType.ACCPAY, invoice);
    when(files.file(access, invoice, attachment)).thenReturn(new AttachmentViews.File("report.pdf", "application/pdf", new byte[]{1}, true));
    mvc.perform(get(path() + "/" + attachment + "/file").header("Authorization", "Bearer " + TestJwts.TOKEN))
        .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
  }

  @Test void frUc15_25_wrongDocumentNeverReachesAttachmentTransport() throws Exception {
    as(Role.DIRECTOR);
    when(provider.detail(any(), any(), any())).thenThrow(new ProviderFailure("XERO_INVALID_RESPONSE", "Wrong document type", 200));
    mvc.perform(get(path()).header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isBadGateway());
    verifyNoInteractions(files);
  }

  @Test void frUc15_25_binaryClientReceivesProviderProblemRatherThanAnErrorFile() throws Exception {
    as(Role.FINANCIAL_ADMIN);
    when(files.file(access, invoice, attachment)).thenThrow(new ProviderFailure("XERO_RATE_LIMITED", "Wait before retrying.", 429, 30));
    mvc.perform(get(path() + "/" + attachment + "/file").accept("application/octet-stream")
        .header("Authorization", "Bearer " + TestJwts.TOKEN)).andExpect(status().isBadGateway())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(header().string("Retry-After", "30")).andExpect(jsonPath("$.code").value("XERO_RATE_LIMITED"));
  }
}
