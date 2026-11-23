package com.airconcepts.dashboard.workitems;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure workflow decisions. Persistence, audit, authorisation and mirror updates belong to callers. */
public final class StageMachine {
  public enum QuoteStatus { DRAFT, SENT, ACCEPTED, DECLINED, DELETED, INVOICED }
  public enum InvoiceStatus { DRAFT, SUBMITTED, AUTHORISED, PAID, VOIDED, DELETED }

  public record Settings(UUID defaultOwnerId, boolean defaultOwnerActive,
      UUID serviceManagerId, boolean serviceManagerActive) {}

  /** The caller determines backfill eligibility and whether a matching invoice exists. */
  public record InitialContext(boolean entityEnabled, boolean firstSync,
      boolean withinBackfillWindow, boolean matchingInvoice) {}

  public record State(WorkItemStage stage, UUID ownerId, String closeReason, String closingNote,
      boolean jobLinked, boolean invoiceLinked, Set<String> attention) {
    public State {
      Objects.requireNonNull(stage, "stage");
      attention = Set.copyOf(attention);
    }
  }

  public sealed interface Event permits QuoteObserved, JobObserved, InvoiceObserved, NoInvoiceNeeded, CloseLost, LinkJob, MarkSent {}
  public record QuoteObserved(QuoteStatus status, QuoteStatus previousStatus) implements Event {
    public QuoteObserved(QuoteStatus status) { this(status, null); }
  }
  public record JobObserved(boolean hasTechnician, boolean hasStart, boolean completed, boolean cancelled) implements Event {}
  public record InvoiceObserved(InvoiceStatus status, boolean sent, BigDecimal amountDue, WorkItemStage stageAfterVoid) implements Event {
    public InvoiceObserved(InvoiceStatus status, boolean sent, BigDecimal amountDue) { this(status, sent, amountDue, null); }
  }
  public record NoInvoiceNeeded(String reason) implements Event {}
  public record CloseLost(String reason) implements Event {}
  public record LinkJob() implements Event {}
  /** Both facts must come from server-side settings and the current invoice mirror. */
  public record MarkSent(boolean fallbackEnabled, boolean invoiceAuthorised) implements Event {}
  /** A null event denotes no audit event; mirror fields can still need refreshing. */
  public record Result(State state, String event) {}

  private StageMachine() {}

  public static Optional<Result> create(QuoteStatus status, InitialContext context, Settings settings) {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(settings, "settings");
    if (!context.entityEnabled()) return Optional.empty();
    State initial = new State(WorkItemStage.QUOTED, null, null, null, false, false, Set.of());
    State created;
    switch (status) {
      case DRAFT, SENT -> created = handOff(initial, WorkItemStage.QUOTED, settings.defaultOwnerId(),
          settings.defaultOwnerActive(), "NO_DEFAULT_OWNER").state();
      case ACCEPTED -> {
        if (context.firstSync() && (!context.withinBackfillWindow() || context.matchingInvoice())) return Optional.empty();
        created = handOff(initial, WorkItemStage.TO_SCHEDULE, settings.serviceManagerId(),
            settings.serviceManagerActive(), "NO_SERVICE_MANAGER").state();
      }
      case DECLINED -> {
        if (context.firstSync()) return Optional.empty();
        created = close(initial, "LOST", "Declined in Xero", "ITEM_CREATED").state();
      }
      case INVOICED -> {
        if (context.firstSync()) return Optional.empty();
        created = withAttention(handOff(initial, WorkItemStage.TO_INVOICE, settings.defaultOwnerId(),
            settings.defaultOwnerActive(), "NO_DEFAULT_OWNER").state(), "QUOTE_INVOICED_IN_XERO");
      }
      case DELETED -> { return Optional.empty(); }
      default -> throw new IllegalStateException("Unknown quote status");
    }
    return Optional.of(new Result(created, "ITEM_CREATED"));
  }

  public static Result apply(State state, Event event, Settings settings) {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(settings, "settings");
    if (event instanceof NoInvoiceNeeded manual) return noInvoice(state, manual.reason());
    if (event instanceof CloseLost manual) return closeLost(state, manual.reason());
    if (event instanceof LinkJob) return linkJob(state);
    if (event instanceof MarkSent markSent) return markSent(state, markSent);
    if (state.stage() == WorkItemStage.CLOSED && !(event instanceof InvoiceObserved && "PAID".equals(state.closeReason())))
      return unchanged(state);
    if (event instanceof QuoteObserved quote) return quote(state, quote, settings);
    if (event instanceof JobObserved job) return job(state, job, settings);
    if (event instanceof InvoiceObserved invoice) return invoice(state, invoice, settings);
    throw new IllegalArgumentException("Unsupported event");
  }

  private static Result quote(State s, QuoteObserved observed, Settings settings) {
    QuoteStatus status = Objects.requireNonNull(observed.status());
    if (s.stage() == WorkItemStage.QUOTED) {
      return switch (status) {
        case ACCEPTED -> handOff(s, WorkItemStage.TO_SCHEDULE, settings.serviceManagerId(),
            settings.serviceManagerActive(), "NO_SERVICE_MANAGER");
        case DECLINED -> close(s, "LOST", "Declined in Xero", "STAGE_CHANGED");
        case DELETED -> close(s, "LOST", "Deleted in Xero", "STAGE_CHANGED");
        case INVOICED -> {
          Result handoff = handOff(s, WorkItemStage.TO_INVOICE, settings.defaultOwnerId(),
              settings.defaultOwnerActive(), "NO_DEFAULT_OWNER");
          yield new Result(withAttention(handoff.state(), "QUOTE_INVOICED_IN_XERO"), "STAGE_CHANGED");
        }
        case DRAFT, SENT -> unchanged(s);
      };
    }
    if (status == observed.previousStatus()) return unchanged(s);
    if (status == QuoteStatus.SENT || status == QuoteStatus.DECLINED
        || status == QuoteStatus.DELETED || status == QuoteStatus.DRAFT) {
      return attention(s, "XERO_STATUS_REGRESSED");
    }
    return unchanged(s);
  }

  private static Result job(State s, JobObserved event, Settings settings) {
    if (s.stage() != WorkItemStage.QUOTED && s.stage() != WorkItemStage.TO_SCHEDULE && s.stage() != WorkItemStage.SCHEDULED) return unchanged(s);
    if (!s.jobLinked()) throw new IllegalArgumentException("A job must be linked before observation");
    if (event.cancelled()) return attention(s, "VWORK_JOB_CANCELLED");
    if (event.completed()) return handOff(s, WorkItemStage.TO_INVOICE, settings.defaultOwnerId(),
        settings.defaultOwnerActive(), "NO_DEFAULT_OWNER");
    WorkItemStage next = event.hasTechnician() && event.hasStart() ? WorkItemStage.SCHEDULED
        : s.stage() == WorkItemStage.QUOTED ? WorkItemStage.QUOTED : WorkItemStage.TO_SCHEDULE;
    if (s.stage() == next) return unchanged(s);
    if (s.stage() == WorkItemStage.QUOTED && next == WorkItemStage.SCHEDULED)
      return handOff(s, next, settings.serviceManagerId(), settings.serviceManagerActive(), "NO_SERVICE_MANAGER");
    return new Result(copy(s, next, s.ownerId(), s.closeReason(), s.closingNote(), s.jobLinked(), s.invoiceLinked(), s.attention()), "STAGE_CHANGED");
  }

  private static Result invoice(State s, InvoiceObserved event, Settings settings) {
    InvoiceStatus status = Objects.requireNonNull(event.status(), "invoice status");
    if (s.stage() != WorkItemStage.CLOSED && s.stage() != WorkItemStage.QUOTED && s.stage() != WorkItemStage.TO_SCHEDULE
        && s.stage() != WorkItemStage.SCHEDULED && s.stage() != WorkItemStage.TO_INVOICE && s.stage() != WorkItemStage.INVOICE_DRAFTED
        && s.stage() != WorkItemStage.INVOICED) return unchanged(s);
    if (status == InvoiceStatus.VOIDED || status == InvoiceStatus.DELETED) {
      if (!s.invoiceLinked()) return unchanged(s);
      WorkItemStage afterVoid = (s.stage() == WorkItemStage.QUOTED || s.stage() == WorkItemStage.TO_SCHEDULE
          || s.stage() == WorkItemStage.SCHEDULED) ? s.stage()
          : event.stageAfterVoid() != null ? event.stageAfterVoid() : WorkItemStage.TO_INVOICE;
      if (afterVoid != WorkItemStage.QUOTED && afterVoid != WorkItemStage.TO_SCHEDULE
          && afterVoid != WorkItemStage.SCHEDULED && afterVoid != WorkItemStage.TO_INVOICE)
        throw new IllegalArgumentException("Invalid stage after void");
      State unlinked = copy(s, afterVoid, s.ownerId(), null, null, s.jobLinked(), false, s.attention());
      if (afterVoid != s.stage() && (afterVoid == WorkItemStage.TO_SCHEDULE || afterVoid == WorkItemStage.SCHEDULED))
        unlinked = handOff(unlinked, afterVoid, settings.serviceManagerId(), settings.serviceManagerActive(), "NO_SERVICE_MANAGER").state();
      return new Result(withAttention(unlinked, "INVOICE_VOIDED"), "INVOICE_UNLINKED");
    }
    if (status == InvoiceStatus.PAID || (status == InvoiceStatus.AUTHORISED
        && event.amountDue() != null && event.amountDue().compareTo(BigDecimal.ZERO) == 0)) {
      if (s.stage() == WorkItemStage.CLOSED) return unchanged(s);
      State linked = copy(s, s.stage(), s.ownerId(), null, null, s.jobLinked(), true, s.attention());
      return close(linked, "PAID", null, "STAGE_CHANGED");
    }
    if (s.stage() == WorkItemStage.CLOSED) {
      if (status != InvoiceStatus.AUTHORISED || event.amountDue() == null || event.amountDue().signum() <= 0)
        return unchanged(s);
      WorkItemStage reopened = event.sent() ? WorkItemStage.INVOICED : WorkItemStage.INVOICE_DRAFTED;
      return new Result(copy(s, reopened, s.ownerId(), null, null, s.jobLinked(), true, s.attention()), "STAGE_CHANGED");
    }
    WorkItemStage next = s.stage();
    if (s.stage() == WorkItemStage.TO_INVOICE
        && (status == InvoiceStatus.DRAFT || status == InvoiceStatus.SUBMITTED
        || (status == InvoiceStatus.AUTHORISED && !event.sent()))) {
      next = WorkItemStage.INVOICE_DRAFTED;
    } else if (s.stage() != WorkItemStage.INVOICED
        && status == InvoiceStatus.AUTHORISED && event.sent()) {
      next = WorkItemStage.INVOICED;
    }
    boolean newlyLinked = !s.invoiceLinked();
    State result = copy(s, next, s.ownerId(), null, null, s.jobLinked(), true, s.attention());
    if (next == WorkItemStage.INVOICED && (s.stage() == WorkItemStage.QUOTED
        || s.stage() == WorkItemStage.TO_SCHEDULE || s.stage() == WorkItemStage.SCHEDULED))
      result = handOff(result, next, settings.defaultOwnerId(), settings.defaultOwnerActive(), "NO_DEFAULT_OWNER").state();
    if (next != s.stage()) return new Result(result, next == WorkItemStage.INVOICE_DRAFTED ? "INVOICE_LINKED" : "STAGE_CHANGED");
    return newlyLinked ? new Result(result, "INVOICE_LINKED") : unchanged(s);
  }

  private static Result noInvoice(State s, String reason) {
    if (s.stage() != WorkItemStage.TO_INVOICE || s.invoiceLinked() || reason == null
        || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("No-invoice action is not valid");
    return close(s, "NO_INVOICE_NEEDED", reason, "CLOSED_NO_INVOICE");
  }

  private static Result closeLost(State s, String reason) {
    if (s.stage() != WorkItemStage.QUOTED || reason == null || reason.isBlank() || reason.length() > 500)
      throw new IllegalArgumentException("Close-lost action is not valid");
    return close(s, "LOST", reason, "CLOSED_LOST");
  }

  private static Result linkJob(State s) {
    if (s.stage() == WorkItemStage.CLOSED || s.jobLinked()) {
      throw new IllegalArgumentException("Job cannot be linked in this state");
    }
    return new Result(copy(s, s.stage(), s.ownerId(), null, null, true, s.invoiceLinked(), s.attention()), "JOB_LINKED");
  }

  private static Result markSent(State s, MarkSent event) {
    if (s.stage() != WorkItemStage.INVOICE_DRAFTED || !s.invoiceLinked()
        || !event.fallbackEnabled() || !event.invoiceAuthorised()) {
      throw new IllegalArgumentException("Invoice cannot be marked sent in this state");
    }
    return new Result(copy(s, WorkItemStage.INVOICED, s.ownerId(), null, null,
        s.jobLinked(), true, s.attention()), "MARKED_SENT");
  }

  private static Result handOff(State s, WorkItemStage stage, UUID owner, boolean active, String missingCode) {
    Set<String> codes = new HashSet<>(s.attention());
    codes.remove("NO_SERVICE_MANAGER");
    codes.remove("NO_DEFAULT_OWNER");
    codes.remove("OWNER_INACTIVE");
    if (owner == null) codes.add(missingCode);
    else if (!active) codes.add("OWNER_INACTIVE");
    UUID assigned = owner != null && active ? owner : null;
    return new Result(copy(s, stage, assigned, null, null, s.jobLinked(), s.invoiceLinked(), codes), "STAGE_CHANGED");
  }

  private static Result close(State s, String reason, String note, String event) {
    return new Result(copy(s, WorkItemStage.CLOSED, s.ownerId(), reason, note, s.jobLinked(), s.invoiceLinked(), s.attention()), event);
  }

  private static Result attention(State s, String code) {
    return s.attention().contains(code) ? unchanged(s) : new Result(withAttention(s, code), "ATTENTION_RAISED");
  }

  private static State withAttention(State s, String code) {
    Set<String> codes = new HashSet<>(s.attention());
    codes.add(code);
    return copy(s, s.stage(), s.ownerId(), s.closeReason(), s.closingNote(), s.jobLinked(), s.invoiceLinked(), codes);
  }

  private static Result unchanged(State s) { return new Result(s, null); }

  private static State copy(State s, WorkItemStage stage, UUID owner, String reason, String note,
      boolean job, boolean invoice, Set<String> attention) {
    return new State(stage, owner, reason, note, job, invoice, attention);
  }
}
