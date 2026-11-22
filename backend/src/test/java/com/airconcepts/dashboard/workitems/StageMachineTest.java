package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageMachineTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final StageMachine.Settings SETTINGS = new StageMachine.Settings(OWNER, true, MANAGER, true);

  private StageMachine.State item(WorkItemStage stage, boolean job, boolean invoice) {
    return new StageMachine.State(stage, OWNER, null, null, job, invoice, Set.of());
  }

  @Test void gl05_manualLostClosureRequiresQuotedStageAndValidReason() {
    var quoted = item(WorkItemStage.QUOTED, false, false);
    var result = StageMachine.apply(quoted, new StageMachine.CloseLost("Customer declined"), SETTINGS);
    assertThat(result.state().stage()).isEqualTo(WorkItemStage.CLOSED);
    assertThat(result.state().closeReason()).isEqualTo("LOST");
    assertThat(result.state().closingNote()).isEqualTo("Customer declined");
    assertThat(result.event()).isEqualTo("CLOSED_LOST");
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(quoted, new StageMachine.CloseLost(" "), SETTINGS));
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(quoted, new StageMachine.CloseLost("x".repeat(501)), SETTINGS));
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(item(WorkItemStage.TO_SCHEDULE, false, false),
        new StageMachine.CloseLost("Customer declined"), SETTINGS));
  }

  @Test void gl05_quoteDeclineDeletionAndInvoicingFollowDistinctTransitions() {
    var quoted = item(WorkItemStage.QUOTED, false, false);
    for (var status : new StageMachine.QuoteStatus[]{StageMachine.QuoteStatus.DECLINED, StageMachine.QuoteStatus.DELETED}) {
      var result = StageMachine.apply(quoted, new StageMachine.QuoteObserved(status), SETTINGS);
      assertThat(result.state().stage()).isEqualTo(WorkItemStage.CLOSED);
      assertThat(result.state().closeReason()).isEqualTo("LOST");
      assertThat(result.state().closingNote()).isEqualTo(status == StageMachine.QuoteStatus.DECLINED ? "Declined in Xero" : "Deleted in Xero");
      assertThat(result.event()).isEqualTo("STAGE_CHANGED");
    }
    var invoiced = StageMachine.apply(quoted, new StageMachine.QuoteObserved(StageMachine.QuoteStatus.INVOICED), SETTINGS);
    assertThat(invoiced.state().stage()).isEqualTo(WorkItemStage.TO_INVOICE);
    assertThat(invoiced.state().attention()).contains("QUOTE_INVOICED_IN_XERO");
    assertThat(invoiced.event()).isEqualTo("STAGE_CHANGED");
    assertThat(StageMachine.apply(quoted, new StageMachine.QuoteObserved(StageMachine.QuoteStatus.SENT), SETTINGS).event()).isNull();
  }

  @Test void gl05_allRegressedQuoteStatusesRaiseAttentionOnceForEveryOpenLaterStage() {
    for (var stage : new WorkItemStage[]{WorkItemStage.TO_SCHEDULE, WorkItemStage.SCHEDULED,
        WorkItemStage.TO_INVOICE, WorkItemStage.INVOICE_DRAFTED, WorkItemStage.INVOICED}) {
      for (var status : new StageMachine.QuoteStatus[]{StageMachine.QuoteStatus.SENT, StageMachine.QuoteStatus.DECLINED,
          StageMachine.QuoteStatus.DELETED, StageMachine.QuoteStatus.DRAFT}) {
        var result = StageMachine.apply(item(stage, true, false), new StageMachine.QuoteObserved(status), SETTINGS);
        assertThat(result.state().stage()).isEqualTo(stage);
        assertThat(result.state().attention()).contains("XERO_STATUS_REGRESSED");
        assertThat(result.event()).isEqualTo("ATTENTION_RAISED");
        assertThat(StageMachine.apply(result.state(), new StageMachine.QuoteObserved(status), SETTINGS).event()).isNull();
      }
    }
  }

  @Test void gl05_closedItemsIgnoreAllSyncEventsAndOldSentQuotesRemainQuoted() {
    var closed = new StageMachine.State(WorkItemStage.CLOSED, OWNER, "LOST", "Declined", false, false, Set.of());
    for (var status : StageMachine.QuoteStatus.values())
      assertThat(StageMachine.apply(closed, new StageMachine.QuoteObserved(status), SETTINGS).state()).isEqualTo(closed);
    for (var status : StageMachine.InvoiceStatus.values())
      assertThat(StageMachine.apply(closed, new StageMachine.InvoiceObserved(status, false, null), SETTINGS).state()).isEqualTo(closed);
    assertThat(StageMachine.apply(closed, new StageMachine.JobObserved(true, true, true, false), SETTINGS).state()).isEqualTo(closed);
    var quoted = item(WorkItemStage.QUOTED, false, false);
    for (int day = 0; day < 180; day++) {
      var result = StageMachine.apply(quoted, new StageMachine.QuoteObserved(StageMachine.QuoteStatus.SENT), SETTINGS);
      assertThat(result.state()).isEqualTo(quoted);
      assertThat(result.event()).isNull();
    }
  }

  @Test void gl08_acceptedHandsOffAndMissingOrInactiveManagerRaisesAttention() {
    var quoted = item(WorkItemStage.QUOTED, false, false);
    var result = StageMachine.apply(quoted, new StageMachine.QuoteObserved(StageMachine.QuoteStatus.ACCEPTED), SETTINGS);
    assertThat(result.state().stage()).isEqualTo(WorkItemStage.TO_SCHEDULE);
    assertThat(result.state().ownerId()).isEqualTo(MANAGER);
    assertThat(result.event()).isEqualTo("STAGE_CHANGED");
    var missing = StageMachine.apply(quoted, new StageMachine.QuoteObserved(StageMachine.QuoteStatus.ACCEPTED),
        new StageMachine.Settings(OWNER, true, null, false));
    assertThat(missing.state().ownerId()).isNull();
    assertThat(missing.state().attention()).containsExactly("NO_SERVICE_MANAGER");
    var inactive = StageMachine.apply(quoted, new StageMachine.QuoteObserved(StageMachine.QuoteStatus.ACCEPTED),
        new StageMachine.Settings(OWNER, true, MANAGER, false));
    assertThat(inactive.state().attention()).containsExactly("OWNER_INACTIVE");
  }

  @Test void gl15_scheduleRequiresWorkerAndStartAndCanRevert() {
    var pending = item(WorkItemStage.TO_SCHEDULE, true, false);
    var scheduled = StageMachine.apply(pending, new StageMachine.JobObserved(true, true, false, false), SETTINGS);
    assertThat(scheduled.state().stage()).isEqualTo(WorkItemStage.SCHEDULED);
    assertThat(StageMachine.apply(scheduled.state(), new StageMachine.JobObserved(false, true, false, false), SETTINGS)
        .state().stage()).isEqualTo(WorkItemStage.TO_SCHEDULE);
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(
        item(WorkItemStage.TO_SCHEDULE, false, false), new StageMachine.JobObserved(true, true, false, false), SETTINGS));
  }

  @Test void gl16_completionHandsOffAndCancellationRaisesAttention() {
    var scheduled = item(WorkItemStage.SCHEDULED, true, false);
    var completed = StageMachine.apply(scheduled, new StageMachine.JobObserved(false, false, true, false), SETTINGS);
    assertThat(completed.state().stage()).isEqualTo(WorkItemStage.TO_INVOICE);
    assertThat(completed.state().ownerId()).isEqualTo(OWNER);
    var cancelled = StageMachine.apply(scheduled, new StageMachine.JobObserved(false, false, false, true), SETTINGS);
    assertThat(cancelled.state().stage()).isEqualTo(WorkItemStage.SCHEDULED);
    assertThat(cancelled.state().attention()).containsExactly("VWORK_JOB_CANCELLED");
    assertThat(StageMachine.apply(cancelled.state(), new StageMachine.JobObserved(false, false, false, true), SETTINGS).event()).isNull();
  }

  @Test void gl19_and_gl21_invoiceTransitionsAndPartialPayment() {
    var pending = item(WorkItemStage.TO_INVOICE, true, false);
    var drafted = StageMachine.apply(pending, new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.DRAFT, false, null), SETTINGS);
    assertThat(drafted.state().stage()).isEqualTo(WorkItemStage.INVOICE_DRAFTED);
    assertThat(drafted.state().invoiceLinked()).isTrue();
    var sent = StageMachine.apply(drafted.state(), new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.AUTHORISED, true, new BigDecimal("20.00")), SETTINGS);
    assertThat(sent.state().stage()).isEqualTo(WorkItemStage.INVOICED);
    var partial = StageMachine.apply(sent.state(), new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.AUTHORISED, true, new BigDecimal("5.00")), SETTINGS);
    assertThat(partial.state().stage()).isEqualTo(WorkItemStage.INVOICED);
    assertThat(partial.event()).isNull();
    var paid = StageMachine.apply(partial.state(), new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.PAID, true, BigDecimal.ZERO), SETTINGS);
    assertThat(paid.state().stage()).isEqualTo(WorkItemStage.CLOSED);
    assertThat(paid.state().closeReason()).isEqualTo("PAID");
  }

  @Test void gl19_voidInvoiceUnlinksAndReturnsToInvoice() {
    var result = StageMachine.apply(item(WorkItemStage.INVOICED, true, true),
        new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.VOIDED, false, null), SETTINGS);
    assertThat(result.state().stage()).isEqualTo(WorkItemStage.TO_INVOICE);
    assertThat(result.state().invoiceLinked()).isFalse();
    assertThat(result.state().attention()).containsExactly("INVOICE_VOIDED");
    assertThat(result.event()).isEqualTo("INVOICE_UNLINKED");
  }

  @Test void gl05_closedItemsDoNotReopenAndRegressedQuoteRaisesAttentionOnce() {
    var closed = new StageMachine.State(WorkItemStage.CLOSED, OWNER, "PAID", null, true, true, Set.of());
    assertThat(StageMachine.apply(closed, new StageMachine.QuoteObserved(StageMachine.QuoteStatus.SENT), SETTINGS).event()).isNull();
    var scheduled = item(WorkItemStage.SCHEDULED, true, false);
    var regression = StageMachine.apply(scheduled, new StageMachine.QuoteObserved(StageMachine.QuoteStatus.SENT), SETTINGS);
    assertThat(regression.state().stage()).isEqualTo(WorkItemStage.SCHEDULED);
    assertThat(regression.state().attention()).containsExactly("XERO_STATUS_REGRESSED");
    assertThat(StageMachine.apply(regression.state(), new StageMachine.QuoteObserved(StageMachine.QuoteStatus.SENT), SETTINGS).event()).isNull();
  }

  @Test void gl20_noInvoiceRequiresReasonAndNoLinkedInvoice() {
    var pending = item(WorkItemStage.TO_INVOICE, true, false);
    var result = StageMachine.apply(pending, new StageMachine.NoInvoiceNeeded("No charge"), SETTINGS);
    assertThat(result.state().closeReason()).isEqualTo("NO_INVOICE_NEEDED");
    assertThat(result.state().closingNote()).isEqualTo("No charge");
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(pending, new StageMachine.NoInvoiceNeeded(" "), SETTINGS));
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(item(WorkItemStage.TO_INVOICE, true, true), new StageMachine.NoInvoiceNeeded("No charge"), SETTINGS));
  }

  @Test void gl14_linkThenObserveJobAndRejectDuplicateLink() {
    var pending = item(WorkItemStage.TO_SCHEDULE, false, false);
    var linked = StageMachine.apply(pending, new StageMachine.LinkJob(), SETTINGS);
    assertThat(linked.state().jobLinked()).isTrue();
    assertThat(linked.event()).isEqualTo("JOB_LINKED");
    assertThat(StageMachine.apply(linked.state(), new StageMachine.JobObserved(true, true, false, false), SETTINGS)
        .state().stage()).isEqualTo(WorkItemStage.SCHEDULED);
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(linked.state(), new StageMachine.LinkJob(), SETTINGS));
  }

  @Test void gl19_markSentRequiresAuthorisedInvoiceAndEnabledFallback() {
    var draft = item(WorkItemStage.INVOICE_DRAFTED, true, true);
    var marked = StageMachine.apply(draft, new StageMachine.MarkSent(true, true), SETTINGS);
    assertThat(marked.state().stage()).isEqualTo(WorkItemStage.INVOICED);
    assertThat(marked.event()).isEqualTo("MARKED_SENT");
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(draft, new StageMachine.MarkSent(false, true), SETTINGS));
    assertThatIllegalArgumentException().isThrownBy(() -> StageMachine.apply(draft, new StageMachine.MarkSent(true, false), SETTINGS));
  }

  @Test void gl21_zeroBalanceDraftIsNotTreatedAsPaid() {
    var result = StageMachine.apply(item(WorkItemStage.TO_INVOICE, true, false),
        new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.DRAFT, false, BigDecimal.ZERO), SETTINGS);
    assertThat(result.state().stage()).isEqualTo(WorkItemStage.INVOICE_DRAFTED);
  }
  @Test void gl21_authorisedInvoiceWithoutVerifiedSendIsDrafted() {
    var result = StageMachine.apply(item(WorkItemStage.TO_INVOICE,true,true),
        new StageMachine.InvoiceObserved(StageMachine.InvoiceStatus.AUTHORISED,false,new BigDecimal("40")),SETTINGS);
    assertThat(result.state().stage()).isEqualTo(WorkItemStage.INVOICE_DRAFTED);
  }

  @Test void gl23_firstSyncOnlyCreatesEligibleAcceptedQuotesAndAllSentQuotes() {
    var first = new StageMachine.InitialContext(true, true, false, false);
    assertThat(StageMachine.create(StageMachine.QuoteStatus.SENT, first, SETTINGS).orElseThrow().state().stage())
        .isEqualTo(WorkItemStage.QUOTED);
    assertThat(StageMachine.create(StageMachine.QuoteStatus.ACCEPTED, first, SETTINGS)).isEmpty();
    var eligible = new StageMachine.InitialContext(true, true, true, false);
    var accepted = StageMachine.create(StageMachine.QuoteStatus.ACCEPTED, eligible, SETTINGS).orElseThrow();
    assertThat(accepted.state().stage()).isEqualTo(WorkItemStage.TO_SCHEDULE);
    assertThat(accepted.state().ownerId()).isEqualTo(MANAGER);
    assertThat(accepted.event()).isEqualTo("ITEM_CREATED");
    assertThat(StageMachine.create(StageMachine.QuoteStatus.ACCEPTED,
        new StageMachine.InitialContext(true, true, true, true), SETTINGS)).isEmpty();
    assertThat(StageMachine.create(StageMachine.QuoteStatus.DECLINED, eligible, SETTINGS)).isEmpty();
    assertThat(StageMachine.create(StageMachine.QuoteStatus.INVOICED, eligible, SETTINGS)).isEmpty();
  }

  @Test void gl01_and_gl05_subsequentCreationHandlesQuoteStatusesAndDisabledEntities() {
    var later = new StageMachine.InitialContext(true, false, false, false);
    var declined = StageMachine.create(StageMachine.QuoteStatus.DECLINED, later, SETTINGS).orElseThrow();
    assertThat(declined.state().closeReason()).isEqualTo("LOST");
    assertThat(declined.state().closingNote()).isEqualTo("Declined in Xero");
    var invoiced = StageMachine.create(StageMachine.QuoteStatus.INVOICED, later, SETTINGS).orElseThrow();
    assertThat(invoiced.state().stage()).isEqualTo(WorkItemStage.TO_INVOICE);
    assertThat(invoiced.state().attention()).contains("QUOTE_INVOICED_IN_XERO");
    assertThat(StageMachine.create(StageMachine.QuoteStatus.DRAFT, later, SETTINGS).orElseThrow().state().stage()).isEqualTo(WorkItemStage.QUOTED);
    assertThat(StageMachine.create(StageMachine.QuoteStatus.DELETED, later, SETTINGS)).isEmpty();
    assertThat(StageMachine.create(StageMachine.QuoteStatus.SENT,
        new StageMachine.InitialContext(false, false, true, false), SETTINGS)).isEmpty();
  }
}
