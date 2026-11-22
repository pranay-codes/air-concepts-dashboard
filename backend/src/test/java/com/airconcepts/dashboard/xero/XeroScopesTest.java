package com.airconcepts.dashboard.xero;

import static org.assertj.core.api.Assertions.*;

import com.airconcepts.dashboard.web.ApiFailure;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class XeroScopesTest {
  @Test void gl01_quoteWorkflowAddsContactReadWithoutWriteAccess() {
    var baseline = XeroScopes.requested(false, false, false);
    var quoteWorkflow = XeroScopes.requested(true, false, false);
    assertThat(baseline).contains("offline_access", "accounting.invoices.read", "accounting.attachments.read")
        .doesNotContain("accounting.contacts.read", "accounting.invoices", "accounting.attachments");
    assertThat(quoteWorkflow).contains("accounting.invoices.read", "accounting.contacts.read")
        .doesNotContain("accounting.invoices", "accounting.attachments");
    assertThat(XeroScopes.requested(true, true, true)).contains("accounting.invoices", "accounting.attachments");
  }

  @Test void gl06_missingOrUnknownGrantScopesFailClosed() {
    assertThatThrownBy(() -> XeroScopes.require(null, "accounting.contacts.read"))
        .isInstanceOfSatisfying(ApiFailure.class, e -> assertThat(e.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
    assertThatThrownBy(() -> XeroScopes.require(Set.of("accounting.invoices.read"), "accounting.contacts.read"))
        .isInstanceOfSatisfying(ApiFailure.class, e -> assertThat(e.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
    assertThatCode(() -> XeroScopes.require(Set.of("accounting.contacts.read"), "accounting.contacts.read"))
        .doesNotThrowAnyException();
  }

  @Test void gl01_authorizationUrlUsesEnabledWorkflowScopes() {
    var settings = new XeroSettings("client", "synthetic-secret", "https://example.invalid/api/v1/xero/callback",
        "https://example.invalid/settings", true, false, false);
    var url = URLDecoder.decode(settings.authorizationUrl("synthetic-state"), StandardCharsets.UTF_8);
    assertThat(url).contains("accounting.contacts.read", "accounting.invoices.read").doesNotContain("synthetic-secret");
  }
}
