package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.web.ApiFailure;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;

/**
 * Pure reporting rules for the Director overview (GL-DIR-03 to 05). Event times are when the dashboard saw a
 * change, so every step that cannot be measured honestly is counted under a reason instead of being estimated.
 */
public final class OverviewMetrics {
  private static final int MAX_DAYS = 366;
  private static final int DEFAULT_DAYS = 30;

  public enum Step { QUOTE_TO_ACCEPTED, ACCEPTED_TO_SCHEDULED, QUOTE_TO_SCHEDULED, SCHEDULED_TO_COMPLETED, COMPLETED_TO_SENT }

  /** Inclusive Auckland calendar dates. */
  public record Period(LocalDate from, LocalDate to) {
    public boolean contains(Instant at) {
      if (at == null) return false;
      var date = BusinessTime.dateOf(at);
      return !date.isBefore(from) && !date.isAfter(to);
    }
  }

  /** One work-item event; job statuses are the vWork statuses recorded with it, where present. */
  public record Event(Instant at, String source, String fromStage, String toStage, UUID fromOwner,
      String fromJobStatus, String toJobStatus) {}

  /** First time each fact was seen, except the latest closure. A null time means it was never seen. */
  public record Milestones(Instant accepted, Instant scheduled, Instant completed, boolean completionTimeUnknown,
      UUID creditedOwner, Instant sent, Instant closed) {}

  public record Item(boolean backfilled, LocalDate quoteDate, Milestones milestones) {}

  public record OverviewCycleTime(Step step, Double medianDays, Double averageDays, int sampleSize,
      int excludedBackfilled, int excludedNoStart, int excludedAlreadyComplete, int excludedOutOfOrder) {}

  private static final Set<String> JOB_STAGES = Set.of("TO_SCHEDULE", "SCHEDULED", "WORK_IN_PROGRESS");
  private static final Set<String> AFTER_JOB = Set.of("JOB_COMPLETED", "INVOICE_CREATED", "INVOICED", "CLOSED");

  private OverviewMetrics() {}

  public static Period period(LocalDate from, LocalDate to, LocalDate today) {
    var end = to != null ? to : today;
    var start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
    if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_DAYS)
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "Choose a start date on or before the end date, within 366 days.");
    return new Period(start, end);
  }

  /** @param events one item's events, oldest first */
  public static Milestones milestones(List<Event> events, Instant providerCompletedAt) {
    var accepted = first(events, e -> "QUOTED".equals(e.fromStage()) && JOB_STAGES.contains(String.valueOf(e.toStage())));
    var scheduled = first(events, e -> entered(e, "SCHEDULED") || entered(e, "WORK_IN_PROGRESS"));
    var completion = first(events, e -> "VWORK_SYNC".equals(e.source())
        && ("COMPLETED".equals(e.toJobStatus()) || entered(e, "JOB_COMPLETED")));
    // A job recorded as complete with no earlier status was already complete when the dashboard first saw it.
    boolean unknown = providerCompletedAt == null && completion != null
        && "COMPLETED".equals(completion.toJobStatus()) && completion.fromJobStatus() == null;
    Instant completed = completion == null ? null : providerCompletedAt != null ? providerCompletedAt : completion.at();
    var leftJob = first(events, e -> e.fromStage() != null && JOB_STAGES.contains(e.fromStage())
        && e.toStage() != null && AFTER_JOB.contains(e.toStage()));
    var sent = first(events, e -> entered(e, "INVOICED"));
    // A reopened item can close again, so the latest closure is the one its current close reason describes.
    var closed = events.stream().filter(e -> entered(e, "CLOSED")).reduce((earlier, later) -> later).orElse(null);
    return new Milestones(at(accepted), at(scheduled), completed, unknown,
        leftJob == null ? null : leftJob.fromOwner(), at(sent), at(closed));
  }

  public static List<OverviewCycleTime> cycleTimes(List<Item> items, Period period) {
    return List.of(
        measure(Step.QUOTE_TO_ACCEPTED, items, period, Item::quoteDate, m -> m.accepted(), false),
        measure(Step.ACCEPTED_TO_SCHEDULED, items, period, i -> date(i.milestones().accepted()), m -> m.scheduled(), false),
        measure(Step.QUOTE_TO_SCHEDULED, items, period, Item::quoteDate, m -> m.scheduled(), false),
        measure(Step.SCHEDULED_TO_COMPLETED, items, period, i -> date(i.milestones().scheduled()), m -> m.completed(), true),
        measure(Step.COMPLETED_TO_SENT, items, period, i -> date(i.milestones().completed()), m -> m.sent(), true));
  }

  private static OverviewCycleTime measure(Step step, List<Item> items, Period period, Function<Item, LocalDate> start,
      Function<Milestones, Instant> end, boolean usesCompletion) {
    var days = new ArrayList<Long>();
    int backfilled = 0, noStart = 0, alreadyComplete = 0, outOfOrder = 0;
    for (var item : items) {
      var milestones = item.milestones();
      var finished = end.apply(milestones);
      if (!period.contains(finished)) continue;
      var started = start.apply(item);
      if (item.backfilled()) backfilled++;
      else if (usesCompletion && milestones.completionTimeUnknown()) alreadyComplete++;
      else if (started == null) noStart++;
      // Same-day events can still be reversed, so completion against sending compares the instants.
      else if (started.isAfter(date(finished))
          || (step == Step.COMPLETED_TO_SENT && milestones.completed().isAfter(finished))) outOfOrder++;
      else days.add(ChronoUnit.DAYS.between(started, date(finished)));
    }
    days.sort(null);
    int size = days.size();
    Double median = size == 0 ? null : (days.get((size - 1) / 2) + days.get(size / 2)) / 2.0;
    Double average = size == 0 ? null : Math.round(days.stream().mapToLong(Long::longValue).sum() * 10.0 / size) / 10.0;
    return new OverviewCycleTime(step, median, average, size, backfilled, noStart, alreadyComplete, outOfOrder);
  }

  private static boolean entered(Event event, String stage) {
    return stage.equals(event.toStage()) && !stage.equals(event.fromStage());
  }
  private static Event first(List<Event> events, Predicate<Event> match) {
    return events.stream().filter(match).findFirst().orElse(null);
  }
  private static Instant at(Event event) { return event == null ? null : event.at(); }
  private static LocalDate date(Instant at) { return at == null ? null : BusinessTime.dateOf(at); }
}
