package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.time.BusinessTime;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.operations.ExternalOperationRepository;
import com.airconcepts.dashboard.workitems.operations.OperationState;
import com.airconcepts.dashboard.workitems.operations.InvoiceGateway;
import com.airconcepts.dashboard.workitems.operations.QuoteAcceptGateway;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entity-scoped views of locally mirrored data; no provider calls are made here. */
@Service
public class WorkItemReadService {
  public record StaffRef(UUID id, String displayName) {}
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record Summary(UUID id, UUID entityId, String entityName, UUID xeroQuoteId, String quoteNumber,
      String jobId, String customerName, String xeroQuoteStatus, String quoteTotal, String currency,
      WorkItemStage stage, String closeReason, StaffRef owner, LocalDate followUpDate, Instant stageEnteredAt,
      long daysInStage, boolean late, String nextAction, Set<String> flags, Set<String> attention, int version) {}
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record VworkPanel(String jobId, String jobNumber, String status, String technician,
      Instant scheduledStart, Instant completedAt, String technicianNotes, String reportStatus, String searchState) {}
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record InvoicePanel(UUID invoiceId, String invoiceNumber, String status, Boolean sent,
      String total, String amountDue, LocalDate dueDate) {}
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record Links(String xeroQuoteUrl, String xeroInvoiceUrl, String vworkJobUrl) {}
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record EventJob(String status, String technician, Instant scheduledStart, Instant completedAt) {}
  /** Typed values read from the recorded event; anything the event did not record is absent. */
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record EventDetails(String invoiceNumber, String closeReason, String reason,
      LocalDate fromFollowUpDate, LocalDate toFollowUpDate, EventJob fromJob, EventJob toJob) {}
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record EventView(long id, Instant at, StaffRef actor, String source, String eventType,
      String fromStage, String toStage, UUID fromOwnerId, UUID toOwnerId, String before, String after,
      StaffRef fromOwner, StaffRef toOwner, EventDetails details) {}
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record OperationView(UUID id, ExternalOperationRepository.Kind kind, OperationState.Status status,
      OperationState.Result outcome, Instant requestedAt, boolean allowLookup, boolean allowRetry) {}
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record Detail(Summary summary, String notes, String closingNote, VworkPanel vwork, InvoicePanel invoice,
      Links links, List<EventView> events, List<OperationView> operations, Set<WorkItemAction> allowedActions) {}
  private record Loaded(Detail detail, boolean blockingOperation, boolean jobOperation, boolean invoiceOperation, UUID serviceManagerId) {}

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final InvoiceGateway invoiceGateway;
  private final QuoteAcceptGateway quoteAcceptGateway;
  public WorkItemReadService(JdbcTemplate jdbc, Clock clock) { this(jdbc,clock,null,null); }
  public WorkItemReadService(JdbcTemplate jdbc, Clock clock, InvoiceGateway invoiceGateway) { this(jdbc,clock,invoiceGateway,null); }
  @org.springframework.beans.factory.annotation.Autowired
  public WorkItemReadService(JdbcTemplate jdbc, Clock clock, InvoiceGateway invoiceGateway, QuoteAcceptGateway quoteAcceptGateway) {
    this.jdbc = jdbc; this.clock = clock; this.invoiceGateway=invoiceGateway; this.quoteAcceptGateway=quoteAcceptGateway;
  }

  @Transactional
  public Detail get(CurrentUser user, UUID entity, UUID id) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    TenantProvenance.requireValid(jdbc, entity);
    var rows = jdbc.query("""
        select w.*,e.display_name entity_name,owner.display_name owner_name,s.service_manager_id,
          exists(select 1 from public.work_items other where other.entity_id=w.entity_id
            and other.id<>w.id and lower(other.job_id)=lower(w.job_id)) duplicate_job_id,
          exists(select 1 from public.external_operations op where op.entity_id=w.entity_id and op.work_item_id=w.id
            and (op.status in ('PENDING','UNKNOWN') or (op.kind='XERO_CREATE_INVOICE' and op.status='SUCCEEDED'))) blocking_operation,
          exists(select 1 from public.external_operations op where op.entity_id=w.entity_id and op.work_item_id=w.id
            and op.kind='VWORK_CREATE_JOB') job_operation,
          exists(select 1 from public.external_operations op where op.entity_id=w.entity_id and op.work_item_id=w.id
            and op.kind='XERO_CREATE_INVOICE') invoice_operation
        from public.work_items w
        join public.entities e on e.id=w.entity_id and e.archived_at is null
        join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled
        join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id
        left join public.staff_users owner on owner.id=w.owner_id
        where w.entity_id=? and w.id=?
        """, (rs, n) -> new Loaded(row(rs, user), rs.getBoolean("blocking_operation"),rs.getBoolean("job_operation"),rs.getBoolean("invoice_operation"),
            rs.getObject("service_manager_id", UUID.class)), entity, id);
    if (rows.isEmpty()) throw new ApiFailure(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "This work item is unavailable.");
    var loaded = rows.getFirst();
    var data = loaded.detail();
    var allowed = EnumSet.noneOf(WorkItemAction.class);
    allowed.addAll(data.allowedActions());
    if (loaded.blockingOperation()) {
      allowed.remove(WorkItemAction.CLOSE_LOST);
      allowed.remove(WorkItemAction.NO_INVOICE_NEEDED);
    }
    boolean manager = loaded.serviceManagerId() != null && loaded.serviceManagerId().toString().equals(user.subject());
    if (!loaded.jobOperation() && !loaded.blockingOperation()
        && WorkItemPolicy.allowed(WorkItemAction.CREATE_JOB, user.role(), true, false, manager,
            data.summary().stage(), data.vwork().jobId() != null, data.invoice().invoiceId() != null))
      allowed.add(WorkItemAction.CREATE_JOB);
    if ("AUTHORISED".equals(data.invoice().status()) && WorkItemPolicy.allowed(WorkItemAction.MARK_SENT,user.role(),true,false,false,
        data.summary().stage(),data.vwork().jobId()!=null,data.invoice().invoiceId()!=null)) allowed.add(WorkItemAction.MARK_SENT);
    if (invoiceGateway!=null && invoiceGateway.available() && !loaded.invoiceOperation() && !loaded.blockingOperation()
        && WorkItemPolicy.allowed(WorkItemAction.PREPARE_INVOICE,user.role(),true,false,false,data.summary().stage(),
            data.vwork().jobId()!=null,data.invoice().invoiceId()!=null)) allowed.add(WorkItemAction.PREPARE_INVOICE);
    if (quoteAcceptGateway!=null && quoteAcceptGateway.available() && !loaded.blockingOperation()
        && "SENT".equals(data.summary().xeroQuoteStatus())
        && WorkItemPolicy.allowed(WorkItemAction.MARK_ACCEPTED,user.role(),true,false,false,data.summary().stage(),
            data.vwork().jobId()!=null,data.invoice().invoiceId()!=null)) allowed.add(WorkItemAction.MARK_ACCEPTED);
    // A manual owner change records the owner IDs only in its before and after values.
    var events = jdbc.query("""
        select ev.*,actor.display_name actor_name,
          previous.id previous_id,previous.display_name previous_name,
          assigned.id assigned_id,assigned.display_name assigned_name,
          coalesce(ev.after->>'invoiceNumber',ev.before->>'invoiceNumber') invoice_number,
          ev.after->>'closeReason' event_close_reason,
          coalesce(ev.after->>'reason',ev.after->>'closingNote') event_reason,
          case when ev.event_type='FOLLOW_UP_CHANGED' then ev.before->>'value' end from_follow_up,
          case when ev.event_type='FOLLOW_UP_CHANGED' then ev.after->>'value' end to_follow_up,
          ev.before->>'jobStatus' from_job_status,ev.before->>'technician' from_technician,
          ev.before->>'scheduledStart' from_scheduled,ev.before->>'completedAt' from_completed,
          ev.after->>'jobStatus' to_job_status,ev.after->>'technician' to_technician,
          ev.after->>'scheduledStart' to_scheduled,ev.after->>'completedAt' to_completed
        from public.work_item_events ev
        left join public.staff_users actor on actor.id=ev.actor_id
        left join public.staff_users previous on previous.id=coalesce(ev.from_owner_id,
          case when ev.event_type='OWNER_CHANGED' then cast(ev.before->>'value' as uuid) end)
        left join public.staff_users assigned on assigned.id=coalesce(ev.to_owner_id,
          case when ev.event_type='OWNER_CHANGED' then cast(ev.after->>'value' as uuid) end)
        where ev.entity_id=? and ev.work_item_id=? order by ev.at desc,ev.id desc
        """, (rs, n) -> new EventView(rs.getLong("id"), instant(rs,"at"),
            staff(rs.getObject("actor_id", UUID.class), rs.getString("actor_name")), rs.getString("source"),
            rs.getString("event_type"), rs.getString("from_stage"), rs.getString("to_stage"),
            rs.getObject("from_owner_id", UUID.class), rs.getObject("to_owner_id", UUID.class),
            rs.getString("before"), rs.getString("after"),
            staff(rs.getObject("previous_id", UUID.class), rs.getString("previous_name")),
            staff(rs.getObject("assigned_id", UUID.class), rs.getString("assigned_name")), details(rs)), entity, id);
    boolean mayLookup = user.role() == Role.DIRECTOR || manager;
    var operations = jdbc.query("""
        select op.id,op.kind,op.status,op.outcome,op.requested_at,op.attempt,
          not exists(select 1 from public.external_operations newer where newer.entity_id=op.entity_id
            and newer.work_item_id=op.work_item_id and newer.kind=op.kind and newer.attempt>op.attempt) latest
        from public.external_operations op
        where op.entity_id=? and op.work_item_id=? order by op.requested_at desc,op.id desc
        """, (rs, n) -> {
      var kind = ExternalOperationRepository.Kind.valueOf(rs.getString("kind"));
      var status = OperationState.Status.valueOf(rs.getString("status"));
      var outcome = rs.getString("outcome");
      return new OperationView(rs.getObject("id", UUID.class), kind, status,
          outcome == null ? null : OperationState.Result.valueOf(outcome), instant(rs,"requested_at"),
          mayLookup && kind == ExternalOperationRepository.Kind.VWORK_CREATE_JOB && status == OperationState.Status.UNKNOWN,
          mayLookup && kind == ExternalOperationRepository.Kind.VWORK_CREATE_JOB && status == OperationState.Status.FAILED
              && rs.getBoolean("latest") && rs.getInt("attempt") < 20 && !loaded.blockingOperation()
              && data.summary().stage() == WorkItemStage.TO_SCHEDULE && data.vwork().jobId() == null);
    }, entity, id);
    return new Detail(data.summary(), data.notes(), data.closingNote(), data.vwork(), data.invoice(),
        new Links(null, null, null), List.copyOf(events), List.copyOf(operations), Set.copyOf(allowed));
  }

  Detail row(ResultSet rs, CurrentUser user) throws SQLException {
    var today = BusinessTime.today(clock);
    var entered = instant(rs, "stage_entered_at");
    var stage = WorkItemStage.valueOf(rs.getString("stage"));
    var lateAfter = rs.getObject("late_after", LocalDate.class);
    boolean late = lateAfter != null && lateAfter.isBefore(today) && stage != WorkItemStage.CLOSED;
    var flags = new LinkedHashSet<String>();
    var jobId = rs.getString("job_id");
    var reference = rs.getString("xero_quote_reference");
    if (jobId == null && reference != null && reference.strip().length() > 255) flags.add("JOB_ID_TOO_LONG");
    else if (jobId == null || jobId.isBlank()) flags.add("JOB_ID_MISSING");
    if (rs.getBoolean("duplicate_job_id")) flags.add("JOB_ID_DUPLICATE");
    var reportStatus = rs.getString("report_status");
    if ("PENDING".equals(reportStatus) || "NOT_ATTACHED".equals(reportStatus)) flags.add("REPORT_NOT_ATTACHED");
    if (late) flags.add("LATE");
    var codes = rs.getArray("attention_codes");
    var attention = codes == null ? Set.<String>of() : Set.copyOf(Arrays.asList((String[]) codes.getArray()));
    var ownerId = rs.getObject("owner_id", UUID.class);
    var invoiceId = rs.getObject("xero_invoice_id", UUID.class);
    var vworkId = rs.getString("vwork_job_id");
    var actions = EnumSet.noneOf(WorkItemAction.class);
    boolean owner = ownerId != null && ownerId.toString().equals(user.subject());
    for (var action : List.of(WorkItemAction.EDIT_NOTES, WorkItemAction.CLOSE_LOST, WorkItemAction.NO_INVOICE_NEEDED))
      if (WorkItemPolicy.allowed(action, user.role(), true, owner, false, stage, vworkId != null, invoiceId != null)) actions.add(action);
    var summary = new Summary(rs.getObject("id", UUID.class), rs.getObject("entity_id", UUID.class), rs.getString("entity_name"),
        rs.getObject("xero_quote_id", UUID.class), rs.getString("quote_number"), jobId, rs.getString("customer_name"),
        rs.getString("xero_quote_status"), decimal(rs,"quote_total"), rs.getString("quote_currency"), stage,
        rs.getString("close_reason"), staff(ownerId, rs.getString("owner_name")), rs.getObject("follow_up_date", LocalDate.class),
        entered, Math.max(0, ChronoUnit.DAYS.between(BusinessTime.dateOf(entered), today)), late,
        nextAction(stage, vworkId != null, rs.getObject("invoice_due_date", LocalDate.class), today, attention),
        Set.copyOf(flags), attention, rs.getInt("version"));
    var vwork = new VworkPanel(vworkId, rs.getString("vwork_job_number"), rs.getString("vwork_status"),
        rs.getString("technician_name"), instant(rs,"scheduled_start"), instant(rs,"completed_at"),
        rs.getString("technician_notes"), reportStatus, rs.getString("vwork_search_state"));
    var invoice = new InvoicePanel(invoiceId, rs.getString("invoice_number"), rs.getString("invoice_status"),
        rs.getObject("invoice_sent", Boolean.class), decimal(rs,"invoice_total"), decimal(rs,"invoice_amount_due"),
        rs.getObject("invoice_due_date", LocalDate.class));
    return new Detail(summary, rs.getString("notes"), rs.getString("closing_note"), vwork, invoice, null, List.of(), List.of(), Set.copyOf(actions));
  }

  static String nextAction(WorkItemStage stage, boolean job, LocalDate due, LocalDate today, Set<String> attention) {
    if (!attention.isEmpty()) return "RESOLVE";
    return switch(stage) {
      case QUOTED -> "FOLLOW_UP";
      case TO_SCHEDULE -> job ? "SCHEDULE_IN_VWORK" : "CREATE_JOB";
      case SCHEDULED, WORK_IN_PROGRESS -> "AWAIT_COMPLETION";
      case JOB_COMPLETED -> "PREPARE_INVOICE";
      case INVOICE_CREATED -> "REVIEW_AND_SEND";
      case INVOICED -> due != null && due.isBefore(today) ? "CHASE_PAYMENT" : "AWAIT_PAYMENT";
      case CLOSED -> null;
    };
  }
  private static StaffRef staff(UUID id, String name) { return id == null ? null : new StaffRef(id, name); }
  private static EventDetails details(ResultSet rs) throws SQLException {
    var details = new EventDetails(rs.getString("invoice_number"), rs.getString("event_close_reason"),
        rs.getString("event_reason"), date(rs.getString("from_follow_up")), date(rs.getString("to_follow_up")),
        eventJob(rs, "from_job_status", "from_technician", "from_scheduled", "from_completed"),
        eventJob(rs, "to_job_status", "to_technician", "to_scheduled", "to_completed"));
    return details.equals(new EventDetails(null, null, null, null, null, null, null)) ? null : details;
  }
  private static EventJob eventJob(ResultSet rs, String status, String technician, String scheduled, String completed)
      throws SQLException {
    var job = new EventJob(rs.getString(status), rs.getString(technician),
        parsedInstant(rs.getString(scheduled)), parsedInstant(rs.getString(completed)));
    return job.equals(new EventJob(null, null, null, null)) ? null : job;
  }
  private static LocalDate date(String value) { return value == null ? null : LocalDate.parse(value); }
  private static Instant parsedInstant(String value) { return value == null ? null : Instant.parse(value); }
  private static Instant instant(ResultSet rs, String name) throws SQLException {
    var time = rs.getTimestamp(name); return time == null ? null : time.toInstant();
  }
  private static String decimal(ResultSet rs, String name) throws SQLException {
    BigDecimal value = rs.getBigDecimal(name); return value == null ? null : value.toPlainString();
  }
}
