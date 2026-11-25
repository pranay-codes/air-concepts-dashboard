package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.time.BusinessTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Director overview (GL-24, GL-DIR-01 to 06). Reads stored mirrors only; no provider calls and no writes. */
@Service
public class OverviewService {
  static final int LIMIT = 500;

  public record OverviewMoney(@JsonInclude(JsonInclude.Include.NON_NULL) String currency, String amount) {}
  public record OverviewStageTotal(WorkItemStage stage, int count, List<OverviewMoney> totals) {}
  public record OverviewClosedTotal(String reason, int count) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OverviewItem(WorkItemReadService.Summary summary, String technician, String invoiceNumber,
      String invoiceTotal, Instant invoiceSentSeenAt) {}
  /** A null id is the "Unattributed" owner, or a technician, who is identified by name only. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OverviewPersonCount(UUID id, String name, int count) {}
  public record OverviewInvoicesSent(int count, List<OverviewMoney> totals, List<OverviewItem> items, boolean truncated,
      int paidNotSeenSent) {}
  public record Overview(Instant generatedAt, LocalDate from, LocalDate to, List<OverviewStageTotal> stages,
      List<OverviewClosedTotal> closed, List<OverviewItem> openItems, boolean openTruncated,
      List<OverviewItem> uninvoiced, boolean uninvoicedTruncated, List<OverviewItem> unsent, boolean unsentTruncated,
      List<OverviewMetrics.OverviewCycleTime> cycleTimes, int completedTotal, int completedAlreadyWhenFirstSeen,
      List<OverviewPersonCount> completedByOwner, List<OverviewPersonCount> completedByTechnician,
      OverviewInvoicesSent invoicesSent) {}

  private record Row(WorkItemReadService.Detail detail, boolean backfilled, LocalDate quoteDate,
      Instant providerCompletedAt) {
    WorkItemReadService.Summary summary() { return detail.summary(); }
  }
  private record Measured(Row row, OverviewMetrics.Milestones milestones) {}

  /** Same visibility rules as My Work: live organisation, workflow enabled and the current Xero tenant. */
  private static final String VISIBLE = """
      from public.work_items w
      join public.entities e on e.id=w.entity_id and e.archived_at is null
      join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled
      join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id
      """;
  /** Open items, plus closed items that changed inside the period. */
  private static final String IN_SCOPE = """
      (cast(? as uuid) is null or w.entity_id=?) and (w.stage<>'CLOSED'
        or exists(select 1 from public.work_item_events recent where recent.entity_id=w.entity_id
          and recent.work_item_id=w.id and recent.at>=? and recent.at<?)
        or (w.completed_at>=? and w.completed_at<?))
      """;

  private final JdbcTemplate jdbc;
  private final WorkItemReadService views;
  private final Clock clock;
  public OverviewService(JdbcTemplate jdbc, WorkItemReadService views, Clock clock) {
    this.jdbc = jdbc; this.views = views; this.clock = clock;
  }

  @Transactional
  public Overview get(CurrentUser user, UUID entity, LocalDate from, LocalDate to) {
    user.requireRole(Role.DIRECTOR);
    var period = OverviewMetrics.period(from, to, BusinessTime.today(clock));
    TenantProvenance.requireValid(jdbc, entity);
    var start = Timestamp.from(period.from().atStartOfDay(BusinessTime.ZONE).toInstant());
    var end = Timestamp.from(period.to().plusDays(1).atStartOfDay(BusinessTime.ZONE).toInstant());
    Object[] scope = {entity, entity, start, end, start, end};

    var rows = jdbc.query("""
        select w.*,e.display_name entity_name,owner.display_name owner_name,
          exists(select 1 from public.work_items other where other.entity_id=w.entity_id
            and other.id<>w.id and lower(other.job_id)=lower(w.job_id)) duplicate_job_id
        """ + VISIBLE + " left join public.staff_users owner on owner.id=w.owner_id where " + IN_SCOPE
        + " order by w.stage_entered_at,w.id", (rs, n) -> {
          var completed = rs.getTimestamp("completed_at");
          return new Row(views.row(rs, user), "BACKFILL".equals(rs.getString("origin")),
              rs.getObject("quote_date", LocalDate.class), completed == null ? null : completed.toInstant());
        }, scope);

    var events = new HashMap<UUID, List<OverviewMetrics.Event>>();
    var names = new HashMap<UUID, String>();
    jdbc.query("""
        select ev.work_item_id,ev.at,ev.source,ev.from_stage,ev.to_stage,ev.from_owner_id,previous.display_name,
          ev.before->>'jobStatus' from_job_status,ev.after->>'jobStatus' to_job_status
        """ + VISIBLE + """
        join public.work_item_events ev on ev.entity_id=w.entity_id and ev.work_item_id=w.id
        left join public.staff_users previous on previous.id=ev.from_owner_id
        where """ + " " + IN_SCOPE + " order by ev.work_item_id,ev.at,ev.id", rs -> {
          var owner = rs.getObject("from_owner_id", UUID.class);
          if (owner != null) names.put(owner, rs.getString("display_name"));
          events.computeIfAbsent(rs.getObject("work_item_id", UUID.class), id -> new ArrayList<>())
              .add(new OverviewMetrics.Event(rs.getTimestamp("at").toInstant(), rs.getString("source"),
                  rs.getString("from_stage"), rs.getString("to_stage"), owner,
                  rs.getString("from_job_status"), rs.getString("to_job_status")));
        }, scope);

    var measured = rows.stream().map(row -> new Measured(row, OverviewMetrics.milestones(
        events.getOrDefault(row.summary().id(), List.of()), row.providerCompletedAt()))).toList();
    return assemble(period, measured, names);
  }

  private Overview assemble(OverviewMetrics.Period period, List<Measured> all, Map<UUID, String> names) {
    var open = all.stream().filter(m -> m.row().summary().stage() != WorkItemStage.CLOSED).toList();
    var stages = Arrays.stream(WorkItemStage.values()).filter(stage -> stage != WorkItemStage.CLOSED).map(stage -> {
      var inStage = open.stream().filter(m -> m.row().summary().stage() == stage).toList();
      return new OverviewStageTotal(stage, inStage.size(), totals(inStage, m -> m.row().summary().quoteTotal()));
    }).toList();
    var closed = new TreeMap<String, Integer>();
    for (var m : all)
      if (m.row().summary().stage() == WorkItemStage.CLOSED && period.contains(m.milestones().closed()))
        closed.merge(m.row().summary().closeReason(), 1, Integer::sum);
    var uninvoiced = open.stream().filter(m -> m.row().summary().stage() == WorkItemStage.JOB_COMPLETED
        && m.row().detail().invoice().invoiceId() == null).toList();
    var unsent = open.stream().filter(m -> m.row().summary().stage() == WorkItemStage.INVOICE_CREATED).toList();

    var completed = all.stream().filter(m -> period.contains(m.milestones().completed())).toList();
    var byOwner = counts(completed, m -> m.milestones().creditedOwner(),
        id -> id == null ? "Unattributed" : names.getOrDefault(id, "Unattributed"), (id, name, count) ->
            new OverviewPersonCount(id, name, count));
    var byTechnician = counts(completed, m -> technician(m.row()), name -> name == null ? "Not recorded" : name,
        (key, name, count) -> new OverviewPersonCount(null, name, count));

    Predicate<Measured> sentInPeriod = m -> period.contains(m.milestones().sent())
        && m.row().detail().invoice().invoiceId() != null;
    var sent = all.stream().filter(sentInPeriod)
        .sorted(Comparator.comparing((Measured m) -> m.milestones().sent()).reversed()).toList();
    int paidNotSeen = (int) all.stream().filter(m -> "PAID".equals(m.row().summary().closeReason())
        && m.milestones().sent() == null && period.contains(m.milestones().closed())).count();

    var cycles = OverviewMetrics.cycleTimes(all.stream().map(m -> new OverviewMetrics.Item(m.row().backfilled(),
        m.row().quoteDate(), m.milestones())).toList(), period);

    return new Overview(clock.instant(), period.from(), period.to(), stages,
        closed.entrySet().stream().map(e -> new OverviewClosedTotal(e.getKey(), e.getValue())).toList(),
        items(open), open.size() > LIMIT, items(uninvoiced), uninvoiced.size() > LIMIT, items(unsent), unsent.size() > LIMIT,
        cycles, completed.size(),
        (int) completed.stream().filter(m -> m.milestones().completionTimeUnknown()).count(), byOwner, byTechnician,
        new OverviewInvoicesSent(sent.size(), totals(sent, m -> m.row().detail().invoice().total()), items(sent),
            sent.size() > LIMIT, paidNotSeen));
  }

  private static String technician(Row row) {
    var name = row.detail().vwork().technician();
    return name == null || name.isBlank() ? null : name.strip();
  }

  private static List<OverviewItem> items(List<Measured> list) {
    return list.stream().limit(LIMIT).map(m -> new OverviewItem(m.row().summary(), technician(m.row()),
        m.row().detail().invoice().invoiceNumber(), m.row().detail().invoice().total(), m.milestones().sent())).toList();
  }

  /** Amounts in different currencies are never added together. Items without an amount are left out. */
  private static List<OverviewMoney> totals(List<Measured> list, Function<Measured, String> amount) {
    var sums = new TreeMap<String, BigDecimal>();
    for (var m : list) {
      var value = amount.apply(m);
      if (value != null) sums.merge(Objects.toString(m.row().summary().currency(), ""), new BigDecimal(value), BigDecimal::add);
    }
    return sums.entrySet().stream().map(e -> new OverviewMoney(e.getKey().isEmpty() ? null : e.getKey(),
        e.getValue().toPlainString())).toList();
  }

  private interface PersonFactory<K> { OverviewPersonCount create(K key, String name, int count); }

  /** Most completions first, then by name. A null key is a person who could not be identified. */
  private static <K> List<OverviewPersonCount> counts(List<Measured> list, Function<Measured, K> key,
      Function<K, String> name, PersonFactory<K> factory) {
    var counts = new HashMap<K, Integer>();
    for (var m : list) counts.merge(key.apply(m), 1, Integer::sum);
    return counts.entrySet().stream().map(e -> factory.create(e.getKey(), name.apply(e.getKey()), e.getValue()))
        .sorted(Comparator.comparingInt(OverviewPersonCount::count).reversed().thenComparing(OverviewPersonCount::name))
        .toList();
  }
}
