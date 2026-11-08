package com.airconcepts.dashboard.invoices;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.xero.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class InvoiceAssignmentTest {
  private final XeroConnectionService connections = mock(XeroConnectionService.class);
  private final XeroInvoiceClient provider = mock(XeroInvoiceClient.class);
  private final XeroReadHealth health = mock(XeroReadHealth.class);
  private final InvoiceAssignmentRepository assignments = mock(InvoiceAssignmentRepository.class);
  private final InvoiceService service = new InvoiceService(connections, provider, health, assignments, Clock.systemUTC());

  @Test void frUc15_07_32_standardUsersCannotAssignAndBillsCannotBeAssigned() {
    var entity = UUID.randomUUID(); var invoice = UUID.randomUUID();
    assertThatThrownBy(() -> service.assign(new CurrentUser(UUID.randomUUID().toString(), Role.STANDARD_USER),
        entity, InvoiceType.ACCREC, invoice, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.assign(new CurrentUser(UUID.randomUUID().toString(), Role.DIRECTOR),
        entity, InvoiceType.ACCPAY, invoice, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(connections, provider, health, assignments);
  }

  @Test void frUc15_07_19_assignmentVerifiesSalesDocumentBeforePersistingAndReturnsHistoricalOwner() {
    var entity = UUID.randomUUID(); var invoice = UUID.randomUUID(); var staff = UUID.randomUUID();
    var user = new CurrentUser(UUID.randomUUID().toString(), Role.FINANCIAL_ADMIN);
    var access = new TenantAccess(entity, UUID.randomUUID(), UUID.randomUUID(), "synthetic-access");
    var owner = new InvoiceViews.Owner(staff, "Previous staff", false);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(assignments.assign(access, UUID.fromString(user.subject()), invoice, staff)).thenReturn(owner);
    assertThat(service.assign(user, entity, InvoiceType.ACCREC, invoice, staff).owner()).isEqualTo(owner);
    var order = inOrder(provider, assignments);
    order.verify(provider).detail(access, InvoiceType.ACCREC, invoice);
    order.verify(assignments).assign(access, UUID.fromString(user.subject()), invoice, staff);
  }

  @Test void frUc15_07_wrongDocumentOrProviderFailurePreventsAssignmentWrite() {
    var user = new CurrentUser(UUID.randomUUID().toString(), Role.DIRECTOR); var entity = UUID.randomUUID();
    var access = new TenantAccess(entity, UUID.randomUUID(), UUID.randomUUID(), "synthetic-access");
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(provider.detail(any(), any(), any())).thenThrow(new com.airconcepts.dashboard.web.ProviderFailure("XERO_INVALID_RESPONSE", "Wrong document", 200));
    assertThatThrownBy(() -> service.assign(user, entity, InvoiceType.ACCREC, UUID.randomUUID(), null))
        .isInstanceOf(com.airconcepts.dashboard.web.ProviderFailure.class);
    verifyNoInteractions(assignments);
  }
}
