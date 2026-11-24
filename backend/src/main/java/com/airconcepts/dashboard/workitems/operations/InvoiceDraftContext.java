package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Locks and authorises the invoice candidate before and after external reads. */
@Service
public class InvoiceDraftContext {
  public record Context(UUID quoteId, String quoteNumber, String jobId, int version,
      UUID tenantId, UUID grantId, String organisationName) {}
  private final WorkItemRepository items;
  private final ExternalOperationRepository operations;
  private final JdbcTemplate jdbc;
  public InvoiceDraftContext(WorkItemRepository items, ExternalOperationRepository operations, JdbcTemplate jdbc) {
    this.items=items; this.operations=operations; this.jdbc=jdbc;
  }
  @Transactional
  public Context load(CurrentUser user, UUID entity, UUID id) {
    user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);
    UUID actor;
    try { actor=UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Active user access is required."); }
    var role=items.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Active user access is required."));
    if (role!=Role.DIRECTOR && role!=Role.FINANCIAL_ADMIN) throw new AccessDeniedException("Finance access is required.");
    var item=items.lockItem(entity,id).orElseThrow(InvoiceDraftContext::unavailable);
    if (item.stage()!=WorkItemStage.JOB_COMPLETED || operations.context(entity,id).invoiceId()!=null
        || items.hasBlockingOperation(entity,id) || operations.latest(entity,id,ExternalOperationRepository.Kind.XERO_CREATE_INVOICE).isPresent()) throw changed();
    return jdbc.query("""
        select w.xero_quote_id,w.quote_number,w.job_id,c.tenant_id,c.grant_id,c.tenant_name
        from public.work_items w join public.xero_entity_connections c on c.entity_id=w.entity_id and c.tenant_id=w.xero_tenant_id
        where w.entity_id=? and w.id=?
        """,(r,n) -> new Context(r.getObject("xero_quote_id",UUID.class),r.getString("quote_number"),r.getString("job_id"),
        item.version(),r.getObject("tenant_id",UUID.class),r.getObject("grant_id",UUID.class),r.getString("tenant_name")),entity,id)
        .stream().findFirst().orElseThrow(InvoiceDraftContext::unavailable);
  }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT,"STAGE_ACTION_NOT_ALLOWED","This invoice cannot be prepared. Refresh and review the job."); }
  private static ApiFailure unavailable() { return new ApiFailure(HttpStatus.NOT_FOUND,"WORK_ITEM_NOT_FOUND","This work item is unavailable."); }
}
