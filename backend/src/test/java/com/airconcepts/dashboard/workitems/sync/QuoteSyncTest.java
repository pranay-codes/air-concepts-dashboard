package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.TenantAccess;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QuoteSyncTest {
  private final UUID entity = UUID.randomUUID(), tenant = UUID.randomUUID(), grant = UUID.randomUUID();
  private final TenantAccess access = new TenantAccess(entity, tenant, grant, "synthetic-token", Set.of("accounting.invoices.read"));
  private final QuoteMirrorService.Mapping mapping = new QuoteMirrorService.Mapping(entity, tenant, grant);
  private final QuoteMirrorService mirror = mock(QuoteMirrorService.class);
  private final Instant modified = Instant.parse("2026-11-18T00:00:00Z");
  private XeroQuoteClient.Quote quote(XeroQuoteClient.Status status) {
    return new XeroQuoteClient.Quote(UUID.randomUUID(), "Q1", "J1", status, null, null,
        null, null, modified, null, null, null, List.of());
  }

  @Test void gl01_missingOrRevokedScopePreventsProviderReads() {
    var source = mock(QuoteSync.Source.class);
    var sync = new QuoteSync(mirror, source, (ctx, quote) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND);
    var unknown = new TenantAccess(entity, tenant, grant, "synthetic-token", null);
    assertThatThrownBy(() -> sync.run(entity, () -> unknown, null))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
    verifyNoInteractions(source, mirror);
    var calls = new AtomicInteger();
    assertThatThrownBy(() -> sync.run(entity, () -> calls.incrementAndGet() == 1 ? access : unknown, null))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
    verifyNoInteractions(source, mirror);
  }

  @Test void draftAndSentAreBothReadEvenWhenCompletenessIsUnknown() {
    var draft = quote(XeroQuoteClient.Status.DRAFT);
    var sent = quote(XeroQuoteClient.Status.SENT);
    QuoteSync.Source source = (ctx, status, page, since) -> new QuoteSync.Page(
        status == XeroQuoteClient.Status.DRAFT ? List.of(draft) :
        status == XeroQuoteClient.Status.SENT ? List.of(sent) : List.of(), QuoteSync.Continuation.UNKNOWN);
    when(mirror.apply(any(), any(), any())).thenReturn(QuoteMirrorService.Outcome.CREATED);
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND)
        .run(entity, () -> access, null)).isInstanceOfSatisfying(ApiFailure.class,
            error -> assertThat(error.code()).isEqualTo("QUOTE_SYNC_COMPLETENESS_UNVERIFIED"));
    verify(mirror).apply(mapping, draft, QuoteMirrorService.InvoiceMatch.UNCHECKED);
    verify(mirror).apply(mapping, sent, QuoteMirrorService.InvoiceMatch.UNCHECKED);
  }

  @Test void gl01_gl02_processesCompletePagesAndResolvesFirstSyncInvoiceCheck() {
    var sent = quote(XeroQuoteClient.Status.SENT);
    var accepted = quote(XeroQuoteClient.Status.ACCEPTED);
    var source = mock(QuoteSync.Source.class);
    when(source.page(any(), any(), anyInt(), any())).thenAnswer(call -> {
      var status = call.getArgument(1, XeroQuoteClient.Status.class);
      return new QuoteSync.Page(status == XeroQuoteClient.Status.SENT ? List.of(sent) :
          status == XeroQuoteClient.Status.ACCEPTED ? List.of(accepted) : List.of(), QuoteSync.Continuation.COMPLETE);
    });
    when(mirror.apply(mapping, sent, QuoteMirrorService.InvoiceMatch.UNCHECKED)).thenReturn(QuoteMirrorService.Outcome.CREATED);
    when(mirror.apply(mapping, accepted, QuoteMirrorService.InvoiceMatch.UNCHECKED)).thenReturn(QuoteMirrorService.Outcome.NEEDS_INVOICE_CHECK);
    when(mirror.apply(mapping, accepted, QuoteMirrorService.InvoiceMatch.NOT_FOUND)).thenReturn(QuoteMirrorService.Outcome.CREATED);
    var result = new QuoteSync(mirror, source, (ctx, quote) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND)
        .run(entity, () -> access, null);
    assertThat(result.seen()).isEqualTo(2);
    assertThat(result.created()).isEqualTo(2);
    assertThat(result.complete()).isTrue();
    verify(mirror).apply(mapping, accepted, QuoteMirrorService.InvoiceMatch.NOT_FOUND);
    verify(source, times(6)).page(any(), any(), eq(1), isNull());
  }

  @Test void gl23_neverReportsSuccessWhenProviderCompletenessOrInvoiceLookupIsUnknown() {
    var accepted = quote(XeroQuoteClient.Status.ACCEPTED);
    var source = mock(QuoteSync.Source.class);
    when(source.page(any(), any(), anyInt(), any())).thenReturn(new QuoteSync.Page(List.of(), QuoteSync.Continuation.UNKNOWN));
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.UNCHECKED)
        .run(entity, () -> access, null)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_COMPLETENESS_UNVERIFIED");
    when(source.page(any(), any(), anyInt(), any())).thenAnswer(call -> {
      var status = call.getArgument(1, XeroQuoteClient.Status.class);
      return new QuoteSync.Page(status == XeroQuoteClient.Status.ACCEPTED ? List.of(accepted) : List.of(), QuoteSync.Continuation.COMPLETE);
    });
    when(mirror.apply(mapping, accepted, QuoteMirrorService.InvoiceMatch.UNCHECKED)).thenReturn(QuoteMirrorService.Outcome.NEEDS_INVOICE_CHECK);
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.UNCHECKED)
        .run(entity, () -> access, null)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_INVOICE_CHECK_REQUIRED");
  }

  @Test void gl06_rejectsEntityOrConnectionChangesBeforeFurtherReads() {
    var source = mock(QuoteSync.Source.class);
    when(source.page(any(), any(), anyInt(), any())).thenReturn(new QuoteSync.Page(List.of(), QuoteSync.Continuation.COMPLETE));
    var calls = new AtomicInteger();
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND)
        .run(entity, () -> calls.incrementAndGet() == 1 ? access : new TenantAccess(entity, UUID.randomUUID(), grant, "synthetic"), modified))
        .isInstanceOf(ApiFailure.class).extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_CONNECTION_CHANGED");
    verifyNoInteractions(mirror);
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND)
        .run(UUID.randomUUID(), () -> access, modified)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_CONNECTION_CHANGED");
  }

  @Test void gl02_duplicateQuoteAcrossPagesFailsClosed() {
    var row = quote(XeroQuoteClient.Status.SENT);
    var source = mock(QuoteSync.Source.class);
    when(source.page(any(), eq(XeroQuoteClient.Status.SENT), eq(1), any())).thenReturn(new QuoteSync.Page(List.of(row), QuoteSync.Continuation.MORE));
    when(source.page(any(), eq(XeroQuoteClient.Status.SENT), eq(2), any())).thenReturn(new QuoteSync.Page(List.of(row), QuoteSync.Continuation.COMPLETE));
    when(mirror.apply(any(), any(), any())).thenReturn(QuoteMirrorService.Outcome.CREATED);
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND)
        .run(entity, () -> access, modified)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_INVALID_PAGE");
  }

  @Test void gl22_deferredQuotePreventsACompleteSyncResult() {
    var row = quote(XeroQuoteClient.Status.SENT);
    var source = mock(QuoteSync.Source.class);
    when(source.page(any(), eq(XeroQuoteClient.Status.SENT), eq(1), any()))
        .thenReturn(new QuoteSync.Page(List.of(row), QuoteSync.Continuation.COMPLETE));
    when(mirror.apply(mapping, row, QuoteMirrorService.InvoiceMatch.UNCHECKED))
        .thenThrow(new ApiFailure(org.springframework.http.HttpStatus.CONFLICT, "QUOTE_SYNC_OPERATION_IN_PROGRESS", "Resolve operation."));
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND)
        .run(entity, () -> access, modified))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("QUOTE_SYNC_OPERATION_IN_PROGRESS"));
    verify(source, times(1)).page(any(), any(), anyInt(), any());
  }

  @Test void gl25_documentedShortPageCannotClaimCompletion() {
    var client = mock(XeroQuoteClient.class);
    var row = quote(XeroQuoteClient.Status.SENT);
    when(client.page(access, 1, XeroQuoteClient.Status.SENT, modified)).thenReturn(List.of(row));
    var page = new DocumentedQuoteSource(client).page(access, XeroQuoteClient.Status.SENT, 1, modified);
    assertThat(page.quotes()).containsExactly(row);
    assertThat(page.continuation()).isEqualTo(QuoteSync.Continuation.UNKNOWN);
    verify(client).page(access, 1, XeroQuoteClient.Status.SENT, modified);
  }

  @Test void gl25_emptyContinuationAndWrongStatusAreRejected() {
    var source = mock(QuoteSync.Source.class);
    when(source.page(any(), any(), anyInt(), any())).thenReturn(new QuoteSync.Page(List.of(), QuoteSync.Continuation.MORE));
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND)
        .run(entity, () -> access, modified)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_INVALID_PAGE");
    when(source.page(any(), any(), anyInt(), any())).thenReturn(new QuoteSync.Page(List.of(quote(XeroQuoteClient.Status.ACCEPTED)), QuoteSync.Continuation.COMPLETE));
    assertThatThrownBy(() -> new QuoteSync(mirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND)
        .run(entity, () -> access, modified)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_INVALID_PAGE");
    verifyNoInteractions(mirror);
  }

  @Test void gl08_syntheticSyncUsesRealMirrorAndStageMachineForAcceptanceHandoff() {
    var repository = mock(QuoteMirrorRepository.class);
    var owner = UUID.randomUUID(); var manager = UUID.randomUUID(); var itemId = UUID.randomUUID();
    var settings = new StageMachine.Settings(owner, true, manager, true);
    when(repository.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(false, 90, 2, settings)));
    when(repository.lockMapping(mapping)).thenReturn(true);
    when(repository.provenanceMatches(mapping)).thenReturn(true);
    var original = quote(XeroQuoteClient.Status.SENT);
    var accepted = new XeroQuoteClient.Quote(original.id(), original.number(), original.reference(), XeroQuoteClient.Status.ACCEPTED,
        original.contactId(), original.contactName(), original.date(), original.expiryDate(), modified.plusSeconds(1),
        original.total(), original.currency(), original.lineAmountTypes(), original.lines());
    var observed = new AtomicReference<>(original);
    QuoteSync.Source source = (ctx, status, page, since) -> new QuoteSync.Page(
        status == observed.get().status() ? List.of(observed.get()) : List.of(), QuoteSync.Continuation.COMPLETE);
    when(repository.lockQuote(entity, original.id())).thenReturn(Optional.empty());
    when(repository.insert(any(), any(), any(), any(), any(), anyString())).thenReturn(itemId);
    when(repository.update(any(), any(), any(), any(), anyInt(), any(), anyBoolean(), any())).thenReturn(1);
    var realMirror = new QuoteMirrorService(repository, Clock.fixed(modified.plusSeconds(60), ZoneOffset.UTC));
    var sync = new QuoteSync(realMirror, source, (ctx, q) -> QuoteMirrorService.InvoiceMatch.NOT_FOUND);
    assertThat(sync.run(entity, () -> access, null).created()).isEqualTo(1);
    var quoted = new StageMachine.State(WorkItemStage.QUOTED, owner, null, null, false, false, Set.of());
    when(repository.lockQuote(entity, original.id())).thenReturn(Optional.of(new QuoteMirrorRepository.Stored(itemId, 0, quoted, original, null)));
    observed.set(accepted);
    assertThat(sync.run(entity, () -> access, modified).updated()).isEqualTo(1);
    var scheduled = new StageMachine.State(WorkItemStage.TO_SCHEDULE, manager, null, null, false, false, Set.of());
    verify(repository).audit(entity, itemId, quoted, scheduled, "STAGE_CHANGED");
  }
}
