package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.web.ProviderFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroConnectionService;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import com.airconcepts.dashboard.xero.XeroQuoteWriteClient;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class QuoteAcceptServiceTest {
  private final UUID entity = UUID.randomUUID(), item = UUID.randomUUID(), tenant = UUID.randomUUID(),
      grant = UUID.randomUUID(), quote = UUID.randomUUID(), operation = UUID.randomUUID();
  private final CurrentUser finance = new CurrentUser(UUID.randomUUID().toString(), Role.FINANCIAL_ADMIN);
  private final OperationReservationService reservations = mock(OperationReservationService.class);
  private final ExternalOperationRepository operations = mock(ExternalOperationRepository.class);
  private final OperationOutcomeService outcomes = mock(OperationOutcomeService.class);
  private final XeroConnectionService connections = mock(XeroConnectionService.class);
  private final QuoteAcceptGateway gateway = mock(QuoteAcceptGateway.class);
  private final QuoteAcceptCompletion completion = mock(QuoteAcceptCompletion.class);
  private final QuoteAcceptService service = new QuoteAcceptService(reservations, operations, outcomes, connections, gateway, completion);
  private final TenantAccess access = new TenantAccess(entity, tenant, grant, "synthetic",
      Set.of("accounting.invoices.read", "accounting.invoices"));
  private final XeroQuoteWriteClient.Receipt receipt = new XeroQuoteWriteClient.Receipt(quote, XeroQuoteClient.Status.ACCEPTED);

  private void sentQuote() {
    when(operations.acceptContext(entity, item)).thenReturn(Optional.of(new ExternalOperationRepository.AcceptContext(
        quote, tenant, grant, 3, "QUOTED", "SENT")));
    when(reservations.reserve(eq(finance), eq(entity), eq(item), eq(3), eq(ExternalOperationRepository.Kind.XERO_ACCEPT_QUOTE),
        eq(quote.toString()), anyString())).thenReturn(new OperationReservationService.Reservation(operation, 1));
    when(connections.authorisedAccess(finance, entity)).thenReturn(access);
    when(gateway.available()).thenReturn(true);
  }

  @Test void gl08_financeAcceptsSentQuoteThroughReservationProviderThenCompletion() {
    sentQuote();
    when(gateway.accept(access, quote, operation)).thenReturn(receipt);

    var result = service.submit(finance, entity, item, 3);

    assertThat(result.outcome()).isEqualTo(OperationState.Result.ACCEPTED);
    assertThat(result.operationId()).isEqualTo(operation);
    var order = inOrder(reservations, gateway, completion);
    order.verify(reservations).reserve(eq(finance), eq(entity), eq(item), eq(3),
        eq(ExternalOperationRepository.Kind.XERO_ACCEPT_QUOTE), eq(quote.toString()), anyString());
    order.verify(gateway).accept(access, quote, operation);
    order.verify(completion).complete(UUID.fromString(finance.subject()), entity, item,
        tenant, grant, operation, receipt);
    verify(outcomes, never()).markFailed(any(), any(), any(), any(), any());
    verify(outcomes, never()).markUnknown(any(), any(), any());
  }

  @Test void gl09_directorAndFinanceOnlyAndNeverStandardUsers() {
    var standard = new CurrentUser(UUID.randomUUID().toString(), Role.STANDARD_USER);
    assertThatThrownBy(() -> service.submit(standard, entity, item, 3)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(operations, reservations, gateway, completion);
  }

  @Test void gl08_unavailableProviderMakesNoReservationOrWrite() {
    when(gateway.available()).thenReturn(false);
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("QUOTE_ACCEPT_UNAVAILABLE"));
    verifyNoInteractions(operations, reservations, completion);
    verify(gateway, never()).accept(any(), any(), any());
  }

  @Test void gl08_staleVersionIsRejectedBeforeReservation() {
    when(gateway.available()).thenReturn(true);
    when(operations.acceptContext(entity, item)).thenReturn(Optional.of(new ExternalOperationRepository.AcceptContext(
        quote, tenant, grant, 4, "QUOTED", "SENT")));
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("WORK_ITEM_CHANGED"));
    verifyNoInteractions(reservations, completion);
    verify(gateway, never()).accept(any(), any(), any());
  }

  @Test void gl08_onlyASentQuoteAtTheQuotedStageCanBeAccepted() {
    when(gateway.available()).thenReturn(true);
    when(operations.acceptContext(entity, item)).thenReturn(Optional.of(new ExternalOperationRepository.AcceptContext(
        quote, tenant, grant, 3, "QUOTED", "ACCEPTED")));
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("STAGE_ACTION_NOT_ALLOWED"));
    verifyNoInteractions(reservations, completion);
    verify(gateway, never()).accept(any(), any(), any());
  }

  @Test void gl22_confirmedRejectionIsRecordedAsFailedAndNotCompleted() {
    sentQuote();
    when(gateway.accept(access, quote, operation)).thenThrow(new ProviderFailure("XERO_PROVIDER_FAILED", "rejected", 400));
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3)).isInstanceOf(ProviderFailure.class);
    verify(outcomes).markFailed(entity, item, operation, OperationOutcomeService.FailureCode.PROVIDER_REJECTED, 400);
    verify(outcomes, never()).markUnknown(any(), any(), any());
    verifyNoInteractions(completion);
  }

  @Test void gl22_rateLimitIsAConfirmedNonWriteWithItsOwnCode() {
    sentQuote();
    when(gateway.accept(access, quote, operation)).thenThrow(new ProviderFailure("XERO_RATE_LIMITED", "limited", 429, 30));
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3)).isInstanceOf(ProviderFailure.class);
    verify(outcomes).markFailed(entity, item, operation, OperationOutcomeService.FailureCode.PROVIDER_RATE_LIMITED, 429);
    verifyNoInteractions(completion);
  }

  @Test void gl22_timeoutOrServerErrorIsUnknownNeverFailedAndNeverRetriedSilently() {
    sentQuote();
    when(gateway.accept(access, quote, operation)).thenThrow(new ProviderFailure("XERO_UNAVAILABLE", "timeout", 0));
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("XERO_UNAVAILABLE"));
    verify(outcomes).markUnknown(entity, item, operation);
    verify(outcomes, never()).markFailed(any(), any(), any(), any(), any());
    verifyNoInteractions(completion);
  }

  @Test void gl22_unexpectedProviderExceptionIsUnknown() {
    sentQuote();
    when(gateway.accept(access, quote, operation)).thenThrow(new IllegalStateException("boom"));
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("EXTERNAL_OUTCOME_UNKNOWN"));
    verify(outcomes).markUnknown(entity, item, operation);
    verifyNoInteractions(completion);
  }

  @Test void gl22_failedLocalRecordAfterXeroAcceptanceHoldsTheItemForReview() {
    sentQuote();
    when(gateway.accept(access, quote, operation)).thenReturn(receipt);
    doThrow(new ApiFailure(org.springframework.http.HttpStatus.CONFLICT, "OPERATION_STATE_CHANGED", "changed"))
        .when(completion).complete(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("QUOTE_ACCEPT_RECORD_FAILED"));
    verify(outcomes).markUnknown(entity, item, operation);
    verify(gateway, times(1)).accept(any(), any(), any());
  }

  @Test void gl08_connectionOutsideTheItemsTenantIsRejectedWithoutAWrite() {
    sentQuote();
    var otherTenant = new TenantAccess(entity, UUID.randomUUID(), grant, "synthetic",
        Set.of("accounting.invoices"));
    when(connections.authorisedAccess(finance, entity)).thenReturn(otherTenant);
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("XERO_CONNECTION_CHANGED"));
    verify(outcomes).markFailed(entity, item, operation, OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE, null);
    verify(gateway, never()).accept(any(), any(), any());
  }

  @Test void gl08_missingInvoicesWriteScopeIsRejectedWithoutAWrite() {
    sentQuote();
    var readOnly = new TenantAccess(entity, tenant, grant, "synthetic", Set.of("accounting.invoices.read"));
    when(connections.authorisedAccess(finance, entity)).thenReturn(readOnly);
    assertThatThrownBy(() -> service.submit(finance, entity, item, 3))
        .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
    verify(outcomes).markFailed(entity, item, operation, OperationOutcomeService.FailureCode.LOOKUP_UNAVAILABLE, null);
    verify(gateway, never()).accept(any(), any(), any());
  }

  @Test void gl22_previousFailedAttemptIsRetriedInsteadOfCreatingAThirdPath() {
    sentQuote();
    var previous = UUID.randomUUID();
    when(operations.latest(entity, item, ExternalOperationRepository.Kind.XERO_ACCEPT_QUOTE))
        .thenReturn(Optional.of(new ExternalOperationRepository.Existing(previous, OperationState.Status.FAILED, 1)));
    when(reservations.retry(eq(finance), eq(entity), eq(item), eq(3),
        eq(ExternalOperationRepository.Kind.XERO_ACCEPT_QUOTE), eq(previous), eq(quote.toString()), anyString()))
        .thenReturn(new OperationReservationService.Reservation(operation, 2));
    when(gateway.accept(access, quote, operation)).thenReturn(receipt);
    assertThat(service.submit(finance, entity, item, 3).outcome()).isEqualTo(OperationState.Result.ACCEPTED);
    verify(reservations, never()).reserve(any(), any(), any(), anyInt(), any(), any(), any());
  }
}
