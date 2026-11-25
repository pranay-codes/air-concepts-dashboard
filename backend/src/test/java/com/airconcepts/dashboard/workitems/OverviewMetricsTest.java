package com.airconcepts.dashboard.workitems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.airconcepts.dashboard.web.ApiFailure;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OverviewMetricsTest {
  private static final UUID MANAGER = UUID.randomUUID();
  private static final UUID FINANCE = UUID.randomUUID();
  private static final OverviewMetrics.Period NOVEMBER =
      new OverviewMetrics.Period(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));

  private static OverviewMetrics.Event event(String at, String source, String from, String to, UUID owner) {
    return new OverviewMetrics.Event(Instant.parse(at), source, from, to, owner, null, null);
  }

  private static OverviewMetrics.Event job(String at, String from, String to, UUID owner, String before, String after) {
    return new OverviewMetrics.Event(Instant.parse(at), "VWORK_SYNC", from, to, owner, before, after);
  }

  /** Accepted 3 Nov, scheduled 5 Nov, completed 10 Nov, sent 12 Nov (Auckland dates). */
  private static List<OverviewMetrics.Event> journey() {
    return List.of(
        event("2026-11-02T20:00:00Z", "XERO_SYNC", "QUOTED", "TO_SCHEDULE", FINANCE),
        job("2026-11-04T20:00:00Z", "TO_SCHEDULE", "SCHEDULED", MANAGER, "ASSIGNED", "ASSIGNED"),
        job("2026-11-09T20:00:00Z", "SCHEDULED", "JOB_COMPLETED", MANAGER, "ASSIGNED", "COMPLETED"),
        event("2026-11-11T20:00:00Z", "XERO_SYNC", "JOB_COMPLETED", "INVOICED", FINANCE));
  }

  private static OverviewMetrics.Item item(boolean backfilled, String quoteDate, List<OverviewMetrics.Event> events) {
    return new OverviewMetrics.Item(backfilled, quoteDate == null ? null : LocalDate.parse(quoteDate),
        OverviewMetrics.milestones(events, null));
  }

  private static OverviewMetrics.OverviewCycleTime step(List<OverviewMetrics.Item> items, OverviewMetrics.Step step) {
    return OverviewMetrics.cycleTimes(items, NOVEMBER).stream().filter(row -> row.step() == step).findFirst().orElseThrow();
  }

  @Test void glDir03_measuresEachStepInAucklandCalendarDays() {
    var items = List.of(item(false, "2026-11-01", journey()));
    assertThat(step(items, OverviewMetrics.Step.QUOTE_TO_ACCEPTED).medianDays()).isEqualTo(2.0);
    assertThat(step(items, OverviewMetrics.Step.ACCEPTED_TO_SCHEDULED).medianDays()).isEqualTo(2.0);
    assertThat(step(items, OverviewMetrics.Step.QUOTE_TO_SCHEDULED).medianDays()).isEqualTo(4.0);
    assertThat(step(items, OverviewMetrics.Step.SCHEDULED_TO_COMPLETED).medianDays()).isEqualTo(5.0);
    assertThat(step(items, OverviewMetrics.Step.COMPLETED_TO_SENT).medianDays()).isEqualTo(2.0);
  }

  @Test void glDir03_medianAndAverageUseEverySample() {
    var items = List.of(item(false, "2026-11-02", journey()), item(false, "2026-11-01", journey()),
        item(false, "2026-10-24", journey()));
    var row = step(items, OverviewMetrics.Step.QUOTE_TO_ACCEPTED);
    assertThat(row.sampleSize()).isEqualTo(3);
    assertThat(row.medianDays()).isEqualTo(2.0);
    assertThat(row.averageDays()).isEqualTo(4.3);
    assertThat(step(items.subList(0, 2), OverviewMetrics.Step.QUOTE_TO_ACCEPTED).medianDays()).isEqualTo(1.5);
  }

  @Test void glDir03_countsBackfilledAndMissingStartInsteadOfMeasuringThem() {
    var backfilled = step(List.of(item(true, "2026-11-01", journey())), OverviewMetrics.Step.QUOTE_TO_ACCEPTED);
    assertThat(backfilled.sampleSize()).isZero();
    assertThat(backfilled.medianDays()).isNull();
    assertThat(backfilled.excludedBackfilled()).isEqualTo(1);
    var undated = step(List.of(item(false, null, journey())), OverviewMetrics.Step.QUOTE_TO_SCHEDULED);
    assertThat(undated.excludedNoStart()).isEqualTo(1);
  }

  @Test void glDir03_invoiceSentBeforeCompletionIsOutOfOrderNotNegative() {
    var events = List.of(
        event("2026-11-03T20:00:00Z", "XERO_SYNC", "SCHEDULED", "INVOICED", MANAGER),
        job("2026-11-09T20:00:00Z", "INVOICED", "INVOICED", FINANCE, "STARTED", "COMPLETED"));
    var row = step(List.of(item(false, "2026-11-01", events)), OverviewMetrics.Step.COMPLETED_TO_SENT);
    assertThat(row.sampleSize()).isZero();
    assertThat(row.excludedOutOfOrder()).isEqualTo(1);
  }

  @Test void glDir03_jobAlreadyCompleteWhenFirstSeenHasNoUsableCompletionTime() {
    var events = List.of(
        job("2026-11-09T20:00:00Z", "TO_SCHEDULE", "JOB_COMPLETED", MANAGER, null, "COMPLETED"),
        event("2026-11-11T20:00:00Z", "XERO_SYNC", "JOB_COMPLETED", "INVOICED", FINANCE));
    var unknown = item(false, "2026-11-01", events);
    assertThat(unknown.milestones().completionTimeUnknown()).isTrue();
    assertThat(step(List.of(unknown), OverviewMetrics.Step.COMPLETED_TO_SENT).excludedAlreadyComplete()).isEqualTo(1);
    var known = OverviewMetrics.milestones(events, Instant.parse("2026-11-08T20:00:00Z"));
    assertThat(known.completionTimeUnknown()).isFalse();
    assertThat(known.completed()).isEqualTo(Instant.parse("2026-11-08T20:00:00Z"));
  }

  @Test void glDir03_countsOnlyStepsEndingInsideTheAucklandPeriod() {
    // 31 Oct 11:30 UTC is 1 November in Auckland; 30 Nov 11:30 UTC is 1 December.
    var inside = List.of(event("2026-10-31T11:30:00Z", "XERO_SYNC", "QUOTED", "TO_SCHEDULE", FINANCE));
    var outside = List.of(event("2026-11-30T11:30:00Z", "XERO_SYNC", "QUOTED", "TO_SCHEDULE", FINANCE));
    var row = step(List.of(item(false, "2026-10-30", inside), item(false, "2026-10-30", outside)),
        OverviewMetrics.Step.QUOTE_TO_ACCEPTED);
    assertThat(row.sampleSize()).isEqualTo(1);
    assertThat(row.medianDays()).isEqualTo(2.0);
  }

  @Test void glDir04_creditsTheOwnerBeforeTheHandOffToFinance() {
    assertThat(OverviewMetrics.milestones(journey(), null).creditedOwner()).isEqualTo(MANAGER);
  }

  @Test void glDir04_keepsTheJobOwnerWhenInvoicedBeforeCompletion() {
    var other = UUID.randomUUID();
    var events = List.of(
        event("2026-11-03T20:00:00Z", "XERO_SYNC", "WORK_IN_PROGRESS", "INVOICE_CREATED", other),
        job("2026-11-09T20:00:00Z", "INVOICE_CREATED", "INVOICE_CREATED", FINANCE, "STARTED", "COMPLETED"));
    var milestones = OverviewMetrics.milestones(events, null);
    assertThat(milestones.completed()).isEqualTo(Instant.parse("2026-11-09T20:00:00Z"));
    assertThat(milestones.creditedOwner()).isEqualTo(other);
  }

  @Test void glDir04_isUnattributedWhenTheItemNeverHadAJobStage() {
    var events = List.of(job("2026-11-09T20:00:00Z", "QUOTED", "JOB_COMPLETED", FINANCE, "STARTED", "COMPLETED"));
    assertThat(OverviewMetrics.milestones(events, null).creditedOwner()).isNull();
  }

  @Test void glDir05_usesTheFirstTimeAnInvoiceWasSeenAsSent() {
    var events = List.of(
        event("2026-11-03T20:00:00Z", "USER", "INVOICE_CREATED", "INVOICED", FINANCE),
        event("2026-11-04T20:00:00Z", "USER", "INVOICED", "INVOICED", FINANCE),
        event("2026-11-20T20:00:00Z", "XERO_SYNC", "CLOSED", "INVOICED", FINANCE));
    assertThat(OverviewMetrics.milestones(events, null).sent()).isEqualTo(Instant.parse("2026-11-03T20:00:00Z"));
  }

  @Test void gl24_countsTheLatestClosureOfAReopenedItem() {
    var events = List.of(
        event("2026-10-03T20:00:00Z", "XERO_SYNC", "INVOICED", "CLOSED", FINANCE),
        event("2026-10-20T20:00:00Z", "XERO_SYNC", "CLOSED", "INVOICED", FINANCE),
        event("2026-11-04T20:00:00Z", "XERO_SYNC", "INVOICED", "CLOSED", FINANCE));
    assertThat(OverviewMetrics.milestones(events, null).closed()).isEqualTo(Instant.parse("2026-11-04T20:00:00Z"));
  }

  @Test void glDir06_periodDefaultsToThirtyDaysAndRejectsBadRanges() {
    var today = LocalDate.parse("2026-11-25");
    assertThat(OverviewMetrics.period(null, null, today))
        .isEqualTo(new OverviewMetrics.Period(LocalDate.parse("2026-10-27"), today));
    assertThat(OverviewMetrics.period(today.minusDays(365), today, today).from()).isEqualTo(today.minusDays(365));
    assertThatThrownBy(() -> OverviewMetrics.period(today, today.minusDays(1), today)).isInstanceOf(ApiFailure.class)
        .extracting(failure -> ((ApiFailure) failure).code()).isEqualTo("INVALID_PERIOD");
    assertThatThrownBy(() -> OverviewMetrics.period(today.minusDays(366), today, today)).isInstanceOf(ApiFailure.class);
  }
}
