package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.time.BusinessTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyWorkService {
  public record MyWork(List<WorkItemReadService.Summary> needsAction, List<WorkItemReadService.Summary> waiting,
      List<WorkItemReadService.Summary> needsAttention, Instant generatedAt, boolean truncated) {}
  record Candidate(WorkItemReadService.Summary summary, WorkItemStage stage, UUID owner, LocalDate followUp,
      LocalDate invoiceDue, BigDecimal amountDue, Set<String> attention, boolean designated) {}
  enum Bucket { NEEDS_ACTION, WAITING, ATTENTION, IGNORE }
  private final JdbcTemplate jdbc;
  private final WorkItemReadService views;
  private final Clock clock;
  public MyWorkService(JdbcTemplate jdbc, WorkItemReadService views, Clock clock) {
    this.jdbc = jdbc; this.views = views; this.clock = clock;
  }

  @Transactional
  public MyWork get(CurrentUser user, UUID entity) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    TenantProvenance.requireValid(jdbc, entity);
    UUID actor;
    try { actor = UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Active user access is required."); }
    var rows = jdbc.query("""
        select w.*,e.display_name entity_name,owner.display_name owner_name,
          exists(select 1 from public.work_items other where other.entity_id=w.entity_id
            and other.id<>w.id and lower(other.job_id)=lower(w.job_id)) duplicate_job_id,
          (?=s.default_owner_id or ?=s.service_manager_id) designated
        from public.work_items w
        join public.entities e on e.id=w.entity_id and e.archived_at is null
        join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled
        join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id
        left join public.staff_users owner on owner.id=w.owner_id
        where w.stage<>'CLOSED' and (cast(? as uuid) is null or w.entity_id=?)
          and (w.owner_id=? or (cardinality(w.attention_codes)>0 and
            (?='DIRECTOR' or (w.owner_id is null and (?=s.default_owner_id or ?=s.service_manager_id)))))
        order by w.stage_entered_at,w.id limit 501
        """, (rs, n) -> {
          var summary = views.row(rs, user).summary();
          return new Candidate(summary, summary.stage(), summary.owner() == null ? null : summary.owner().id(),
              summary.followUpDate(), rs.getObject("invoice_due_date", LocalDate.class), rs.getBigDecimal("invoice_amount_due"),
              summary.attention(), rs.getBoolean("designated"));
        }, actor, actor, entity, entity, actor, user.role().name(), actor, actor);
    var action = new ArrayList<WorkItemReadService.Summary>();
    var waiting = new ArrayList<WorkItemReadService.Summary>();
    var attention = new ArrayList<WorkItemReadService.Summary>();
    var today = BusinessTime.today(clock);
    for (var row : rows.subList(0, Math.min(rows.size(), 500))) {
      switch (classify(row, actor, user.role(), today)) {
        case NEEDS_ACTION -> action.add(row.summary());
        case WAITING -> waiting.add(row.summary());
        case ATTENTION -> attention.add(row.summary());
        case IGNORE -> { }
      }
    }
    return new MyWork(List.copyOf(action), List.copyOf(waiting), List.copyOf(attention), clock.instant(), rows.size() > 500);
  }

  static Bucket classify(Candidate item, UUID actor, Role role, LocalDate today) {
    if (item.stage() == WorkItemStage.CLOSED) return Bucket.IGNORE;
    boolean owner = actor.equals(item.owner());
    if (!item.attention().isEmpty())
      return owner || role == Role.DIRECTOR || (item.owner() == null && item.designated()) ? Bucket.ATTENTION : Bucket.IGNORE;
    if (!owner) return Bucket.IGNORE;
    boolean action = switch (item.stage()) {
      case QUOTED -> item.followUp() == null || !item.followUp().isAfter(today);
      case TO_SCHEDULE, JOB_COMPLETED, INVOICE_CREATED -> true;
      case INVOICED -> item.invoiceDue() != null && item.invoiceDue().isBefore(today)
          && item.amountDue() != null && item.amountDue().signum() > 0;
      case SCHEDULED, WORK_IN_PROGRESS, CLOSED -> false;
    };
    return action ? Bucket.NEEDS_ACTION : Bucket.WAITING;
  }
}
