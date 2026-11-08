package com.airconcepts.dashboard.invoices;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.web.ProviderFailure;
import com.airconcepts.dashboard.xero.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class InvoiceServiceTest {
  private final XeroConnectionService connections = mock(XeroConnectionService.class);
  private final XeroInvoiceClient provider = mock(XeroInvoiceClient.class);
  private final XeroReadHealth health = mock(XeroReadHealth.class);
  private final InvoiceAssignmentRepository assignments = mock(InvoiceAssignmentRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC);
  private final InvoiceService service = new InvoiceService(connections, provider, health, assignments, clock);
  private final UUID entity = UUID.randomUUID();

  @Test void frUc15_13_deniesSupplierBillsBeforeLookingUpTokensOrCallingProvider() {
    for (Role role : Arrays.asList(Role.STANDARD_USER, null)) {
      var user = new CurrentUser(UUID.randomUUID().toString(), role);
      assertThatThrownBy(() -> service.list(user, entity, InvoiceQuery.defaults(InvoiceType.ACCPAY)))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> service.detail(user, entity, InvoiceType.ACCPAY, UUID.randomUUID()))
          .isInstanceOf(AccessDeniedException.class);
    }
    verifyNoInteractions(connections, provider, health);
  }

  @Test void frUc15_12_13_allActiveRolesReadSalesOnlyUsingServerTenantMapping() {
    for (Role role : Role.values()) {
      var user = new CurrentUser(UUID.randomUUID().toString(), role);
      var access = new TenantAccess(entity, UUID.randomUUID(), UUID.randomUUID(), "synthetic-access");
      var q = InvoiceQuery.defaults(InvoiceType.ACCREC);
      var page = new InvoiceViews.Page(List.of(), 1, 25, 0, 0);
      when(connections.authorisedAccess(user, entity)).thenReturn(access);
      when(provider.list(access, q)).thenReturn(page);
      assertThat(service.list(user, entity, q)).isSameAs(page);
      verify(health).check(access);
      verify(health).success(access, clock.instant());
    }
  }

  @Test void frUc15_30_failureIsRecordedPerTenantAndNeverReturnsAnEarlierPage() {
    var user = new CurrentUser(UUID.randomUUID().toString(), Role.FINANCIAL_ADMIN);
    var access = new TenantAccess(entity, UUID.randomUUID(), UUID.randomUUID(), "synthetic-access");
    var q = InvoiceQuery.defaults(InvoiceType.ACCPAY);
    var failure = new ProviderFailure("XERO_ORGANISATION_ACCESS_DENIED", "Access refused.", 403);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(provider.list(access, q)).thenThrow(failure);
    assertThatThrownBy(() -> service.list(user, entity, q)).isSameAs(failure);
    verify(health).failure(access, clock.instant(), failure);
    verify(health, never()).success(any(), any());
  }

  @Test void frUc15_14_providerFailureSurvivesFailureToRecordHealth() {
    var user = new CurrentUser(UUID.randomUUID().toString(), Role.DIRECTOR);
    var access = new TenantAccess(entity, UUID.randomUUID(), UUID.randomUUID(), "synthetic-access");
    var query = InvoiceQuery.defaults(InvoiceType.ACCREC);
    var failure = new ProviderFailure("XERO_RATE_LIMITED", "Wait before retrying.", 429, 60);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    when(provider.list(access, query)).thenThrow(failure);
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("private database diagnostic"))
        .when(health).failure(access, clock.instant(), failure);
    assertThatThrownBy(() -> service.list(user, entity, query)).isSameAs(failure);
    verify(health, never()).success(any(), any());
  }
}
