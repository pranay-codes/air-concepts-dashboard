package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.time.BusinessTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemListService {
  @io.swagger.v3.oas.annotations.media.Schema(name = "WorkItemPage")
  public record Page(List<WorkItemReadService.Summary> items, int page, int pageSize, long total, long totalPages) {}
  private final JdbcTemplate jdbc;
  private final WorkItemReadService views;
  private final Clock clock;
  public WorkItemListService(JdbcTemplate jdbc, WorkItemReadService views, Clock clock) {
    this.jdbc = jdbc; this.views = views; this.clock = clock;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public Page list(CurrentUser user, WorkItemListQuery query) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    TenantProvenance.requireValid(jdbc, query.entityId());
    var args = new ArrayList<Object>();
    var where = new StringBuilder(" where e.archived_at is null and s.enabled");
    if (query.entityId() != null) { where.append(" and w.entity_id=?"); args.add(query.entityId()); }
    if (query.stage() != null) { where.append(" and w.stage=?"); args.add(query.stage().name()); }
    if (query.xeroStatus() != null) { where.append(" and w.xero_quote_status=?"); args.add(query.xeroStatus().name()); }
    if (query.ownerId() != null) { where.append(" and w.owner_id=?"); args.add(query.ownerId()); }
    if (query.followUpDueBy() != null) { where.append(" and w.follow_up_date<=?"); args.add(query.followUpDueBy()); }
    if (query.late() != null) {
      where.append(" and (coalesce(w.late_after<?,false) and w.stage<>'CLOSED')=?");
      args.add(BusinessTime.today(clock)); args.add(query.late());
    }
    if (!query.search().isEmpty()) {
      where.append(" and (w.job_id ilike ? escape '!' or w.quote_number ilike ? escape '!' or w.customer_name ilike ? escape '!')");
      var pattern = WorkItemListQuery.likePattern(query.search());
      args.add(pattern); args.add(pattern); args.add(pattern);
    }
    String from = " from public.work_items w join public.entities e on e.id=w.entity_id"
        + " join public.green_line_entity_settings s on s.entity_id=w.entity_id and s.enabled"
        + " join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id";
    long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
    String sort = switch (query.sort()) {
      case stageEnteredAt -> "w.stage_entered_at";
      case quoteNumber -> "w.quote_number";
      case customer -> "w.customer_name";
      case followUpDate -> "w.follow_up_date";
      case total -> "w.quote_total";
    };
    var pageArgs = new ArrayList<>(args);
    pageArgs.add(query.pageSize()); pageArgs.add(query.offset());
    var rows = jdbc.query("""
        select w.*,e.display_name entity_name,owner.display_name owner_name,
          exists(select 1 from public.work_items other where other.entity_id=w.entity_id
            and other.id<>w.id and lower(other.job_id)=lower(w.job_id)) duplicate_job_id
        """ + from + " left join public.staff_users owner on owner.id=w.owner_id" + where
        + " order by " + sort + " " + query.direction().name() + " nulls last,w.id asc limit ? offset ?",
        (rs, n) -> views.row(rs, user).summary(), pageArgs.toArray());
    return new Page(List.copyOf(rows), query.page(), query.pageSize(), total, (total + query.pageSize() - 1) / query.pageSize());
  }
}
