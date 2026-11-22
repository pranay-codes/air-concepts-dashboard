package com.airconcepts.dashboard.workitems.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.workitems.StageMachine;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import com.airconcepts.dashboard.xero.XeroQuoteClient;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.airconcepts.dashboard.web.ApiFailure;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteMirrorServiceTest {
  private final QuoteMirrorRepository repository = mock(QuoteMirrorRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);
  private final QuoteMirrorService service = new QuoteMirrorService(repository, clock);
  private final UUID entity = UUID.randomUUID(), quoteId = UUID.randomUUID(), owner = UUID.randomUUID(), manager = UUID.randomUUID();
  private final QuoteMirrorService.Mapping mapping = new QuoteMirrorService.Mapping(entity, UUID.randomUUID(), UUID.randomUUID());
  private final StageMachine.Settings owners = new StageMachine.Settings(owner, true, manager, true);
  private final QuoteMirrorRepository.Config later = new QuoteMirrorRepository.Config(false, 90, 2, owners);

  private XeroQuoteClient.Quote quote(XeroQuoteClient.Status status, String reference, Instant updated) {
    return new XeroQuoteClient.Quote(quoteId, "Q-123", reference, status, UUID.fromString("00000000-0000-4000-8000-000000000001"),
        "Synthetic", LocalDate.of(2026, 11, 10), null, updated, new BigDecimal("100.00"), "NZD", "EXCLUSIVE", List.of());
  }

  @BeforeEach void setUp() {
    when(repository.lockConfig(entity)).thenReturn(Optional.of(later));
    when(repository.lockMapping(mapping)).thenReturn(true);
    when(repository.provenanceMatches(mapping)).thenReturn(true);
    when(repository.lockQuote(entity, quoteId)).thenReturn(Optional.empty());
    when(repository.insert(any(), any(), any(), any(), any(), anyString())).thenReturn(UUID.randomUUID());
    when(repository.update(any(), any(), any(), any(), anyInt(), any(), anyBoolean(), any())).thenReturn(1);
  }

  @Test void gl01_and_gl02_createsSentQuoteWithDefaultOwnerAndAudit() {
    var quote = quote(XeroQuoteClient.Status.SENT, "J-1", Instant.parse("2026-11-18T00:00:00Z"));
    assertThat(service.apply(mapping, quote, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.CREATED);
    var state = new StageMachine.State(WorkItemStage.QUOTED, owner, null, null, false, false, Set.of());
    verify(repository).insert(eq(mapping), eq(quote), eq(state), eq(clock.instant()), isNull(), eq("SYNC"));
    verify(repository).audit(eq(entity), any(), isNull(), eq(state), eq("ITEM_CREATED"));
    var order = inOrder(repository);
    order.verify(repository).lockConfig(entity);
    order.verify(repository).lockMapping(mapping);
    order.verify(repository).provenanceMatches(mapping);
    order.verify(repository).lockIdentity(entity, quoteId);
    order.verify(repository).lockQuote(entity, quoteId);
  }

  @Test void gl08_existingAcceptedQuoteHandsOffWithoutTouchingNotes() {
    var before = quote(XeroQuoteClient.Status.SENT, "J-1", Instant.parse("2026-11-17T00:00:00Z"));
    var state = new StageMachine.State(WorkItemStage.QUOTED, owner, null, null, false, false, Set.of());
    var id = UUID.randomUUID();
    when(repository.lockQuote(entity, quoteId)).thenReturn(Optional.of(new QuoteMirrorRepository.Stored(id, 4, state, before, null)));
    var accepted = quote(XeroQuoteClient.Status.ACCEPTED, "J-1", Instant.parse("2026-11-18T00:00:00Z"));
    assertThat(service.apply(mapping, accepted, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.UPDATED);
    var next = new StageMachine.State(WorkItemStage.TO_SCHEDULE, manager, null, null, false, false, Set.of());
    verify(repository).update(eq(entity), eq(id), eq(accepted), eq(next), eq(4), eq(clock.instant()), eq(true), isNull());
    verify(repository).audit(entity, id, state, next, "STAGE_CHANGED");
  }

  @Test void gl02_repeatedAndOlderObservationsDoNotWriteOrAudit() {
    var quote = quote(XeroQuoteClient.Status.SENT, "J-1", Instant.parse("2026-11-18T00:00:00Z"));
    var state = new StageMachine.State(WorkItemStage.QUOTED, owner, null, null, false, false, Set.of());
    when(repository.lockQuote(entity, quoteId)).thenReturn(Optional.of(new QuoteMirrorRepository.Stored(UUID.randomUUID(), 3, state, quote, null)));
    assertThat(service.apply(mapping, quote, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.UNCHANGED);
    assertThat(service.apply(mapping, quote(XeroQuoteClient.Status.DECLINED, "J-1", Instant.parse("2026-11-17T00:00:00Z")),
        QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.STALE);
    verify(repository, never()).update(any(), any(), any(), any(), anyInt(), any(), anyBoolean(), any());
    verify(repository, never()).audit(any(), any(), any(), any(), anyString());
  }

  @Test void gl22_quoteChangesAreDeferredWhileAnyExternalOperationIsUnresolved() {
    var before = quote(XeroQuoteClient.Status.ACCEPTED, "J-1", Instant.parse("2026-11-17T00:00:00Z"));
    var state = new StageMachine.State(WorkItemStage.TO_SCHEDULE, manager, null, null, false, false, Set.of());
    var id = UUID.randomUUID();
    when(repository.lockQuote(entity, quoteId)).thenReturn(Optional.of(new QuoteMirrorRepository.Stored(id, 4, state, before, null)));
    when(repository.hasUnresolvedOperation(entity, id)).thenReturn(true);
    var changedReference = quote(XeroQuoteClient.Status.ACCEPTED, "J-2", Instant.parse("2026-11-18T00:00:00Z"));
    assertThatThrownBy(() -> service.apply(mapping, changedReference, QuoteMirrorService.InvoiceMatch.UNCHECKED))
        .isInstanceOfSatisfying(ApiFailure.class, error -> assertThat(error.code()).isEqualTo("QUOTE_SYNC_OPERATION_IN_PROGRESS"));
    assertThat(service.apply(mapping, before, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.UNCHANGED);
    verify(repository, never()).update(any(), any(), any(), any(), anyInt(), any(), anyBoolean(), any());
    verify(repository, never()).audit(any(), any(), any(), any(), anyString());
  }

  @Test void gl23_firstSyncAcceptedNeedsAConfirmedInvoiceCheck() {
    when(repository.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(true, 90, 2, owners)));
    var quote = quote(XeroQuoteClient.Status.ACCEPTED, "J-1", Instant.parse("2026-11-18T00:00:00Z"));
    assertThat(service.apply(mapping, quote, QuoteMirrorService.InvoiceMatch.UNCHECKED))
        .isEqualTo(QuoteMirrorService.Outcome.NEEDS_INVOICE_CHECK);
    verify(repository, never()).insert(any(), any(), any(), any(), any(), anyString());
    assertThat(service.apply(mapping, quote, QuoteMirrorService.InvoiceMatch.FOUND)).isEqualTo(QuoteMirrorService.Outcome.SKIPPED);
    assertThat(service.apply(mapping, quote, QuoteMirrorService.InvoiceMatch.NOT_FOUND)).isEqualTo(QuoteMirrorService.Outcome.CREATED);
  }

  @Test void gl03_longReferenceIsPreservedAndNeverTruncatedToAJobId() {
    var reference = "x".repeat(300);
    var quote = quote(XeroQuoteClient.Status.SENT, reference, Instant.parse("2026-11-18T00:00:00Z"));
    service.apply(mapping, quote, QuoteMirrorService.InvoiceMatch.UNCHECKED);
    assertThat(QuoteMirrorRepository.jobId(quote.reference())).isNull();
    verify(repository).insert(eq(mapping), argThat(q -> reference.equals(q.reference())), any(), any(), any(), anyString());
  }

  @Test void gl06_disabledEntityCannotBeMirrored() {
    when(repository.lockConfig(entity)).thenReturn(Optional.empty());
    assertThat(service.apply(mapping, quote(XeroQuoteClient.Status.SENT, "J-1", clock.instant()), QuoteMirrorService.InvoiceMatch.UNCHECKED))
        .isEqualTo(QuoteMirrorService.Outcome.SKIPPED);
    verify(repository, never()).insert(any(), any(), any(), any(), any(), anyString());
  }

  @Test void gl05_closedItemsRefreshMirrorWithoutReopeningOrCreatingAnEvent() {
    var before = quote(XeroQuoteClient.Status.SENT, "J-1", Instant.parse("2026-11-17T00:00:00Z"));
    var state = new StageMachine.State(WorkItemStage.CLOSED, owner, "LOST", "Customer declined", false, false, Set.of());
    var id = UUID.randomUUID();
    when(repository.lockQuote(entity, quoteId)).thenReturn(Optional.of(new QuoteMirrorRepository.Stored(id, 4, state, before, null)));
    var accepted = quote(XeroQuoteClient.Status.ACCEPTED, "J-1", Instant.parse("2026-11-18T00:00:00Z"));
    assertThat(service.apply(mapping, accepted, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.UPDATED);
    verify(repository).update(entity, id, accepted, state, 4, clock.instant(), false, null);
    verify(repository, never()).audit(any(), any(), any(), any(), anyString());
  }

  @Test void gl02_databaseDecimalScaleDoesNotCauseRepeatedUpdates() {
    var quote = quote(XeroQuoteClient.Status.SENT, "J-1", Instant.parse("2026-11-18T00:00:00Z"));
    var fromDatabase = new XeroQuoteClient.Quote(quote.id(), quote.number(), quote.reference(), quote.status(), quote.contactId(),
        quote.contactName(), quote.date(), quote.expiryDate(), quote.updatedAt(), new BigDecimal("100.0000"), quote.currency(), null, null);
    var state = new StageMachine.State(WorkItemStage.QUOTED, owner, null, null, false, false, Set.of());
    when(repository.lockQuote(entity, quoteId)).thenReturn(Optional.of(new QuoteMirrorRepository.Stored(UUID.randomUUID(), 1, state, fromDatabase, null)));
    assertThat(service.apply(mapping, quote, QuoteMirrorService.InvoiceMatch.UNCHECKED)).isEqualTo(QuoteMirrorService.Outcome.UNCHANGED);
    verify(repository, never()).update(any(), any(), any(), any(), anyInt(), any(), anyBoolean(), any());
  }

  @Test void gl06_remappedConnectionIsRejectedBeforeQuoteIsReadOrWritten() {
    var mapping = new QuoteMirrorService.Mapping(entity, UUID.randomUUID(), UUID.randomUUID());
    when(repository.lockMapping(mapping)).thenReturn(false);
    assertThatThrownBy(() -> service.apply(mapping, quote(XeroQuoteClient.Status.SENT, "J-1", clock.instant()),
        QuoteMirrorService.InvoiceMatch.UNCHECKED)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure)e).code()).isEqualTo("QUOTE_SYNC_CONNECTION_CHANGED");
    verify(repository, never()).lockQuote(any(), any());
    verify(repository, never()).insert(any(), any(), any(), any(), any(), anyString());
    verify(repository, never()).audit(any(), any(), any(), any(), anyString());
  }

  @Test void gl06_unknownOrDifferentHistoricalTenantBlocksNewMirrorWrites() {
    when(repository.provenanceMatches(mapping)).thenReturn(false);
    assertThatThrownBy(() -> service.apply(mapping, quote(XeroQuoteClient.Status.SENT, "J-1", clock.instant()),
        QuoteMirrorService.InvoiceMatch.UNCHECKED)).isInstanceOf(ApiFailure.class)
        .extracting(e -> ((ApiFailure)e).code()).isEqualTo("XERO_TENANT_CHANGED");
    verify(repository, never()).lockQuote(any(), any());
    verify(repository, never()).insert(any(), any(), any(), any(), any(), anyString());
  }
}
