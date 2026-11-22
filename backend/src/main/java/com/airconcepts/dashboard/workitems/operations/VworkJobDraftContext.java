package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.vwork.VworkTemplatesRepository;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.workitems.WorkItemRepository;
import com.airconcepts.dashboard.workitems.WorkItemStage;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authorises and snapshots the item before and after any external read. */
@Service
public class VworkJobDraftContext {
  public record Context(UUID quoteId, String quoteNumber, String reference, int version,
      List<VworkTemplatesRepository.Template> templates) {
    public Context { templates = List.copyOf(templates); }
  }
  private record Quote(UUID id, String number, String reference) {}
  private final WorkItemRepository items;
  private final ExternalOperationRepository operations;
  private final VworkTemplatesRepository templates;
  private final JdbcTemplate jdbc;
  public VworkJobDraftContext(WorkItemRepository items, ExternalOperationRepository operations,
      VworkTemplatesRepository templates, JdbcTemplate jdbc) {
    this.items = items; this.operations = operations; this.templates = templates; this.jdbc = jdbc;
  }
  @Transactional
  public Context load(CurrentUser user, UUID entity, UUID itemId) {
    user.requireRole(Role.DIRECTOR, Role.FINANCIAL_ADMIN, Role.STANDARD_USER);
    UUID actor;
    try { actor = UUID.fromString(user.subject()); }
    catch (IllegalArgumentException | NullPointerException invalid) { throw new AccessDeniedException("Active user access is required."); }
    var role = items.lockActor(actor).orElseThrow(() -> new AccessDeniedException("Active user access is required."));
    templates.lockAssignments();
    var item = items.lockItem(entity, itemId).orElseThrow(VworkJobDraftContext::unavailable);
    var relation = operations.context(entity, itemId);
    if (role != Role.DIRECTOR && !actor.equals(relation.serviceManagerId())) throw new AccessDeniedException("Service Manager access is required.");
    if (item.stage() != WorkItemStage.TO_SCHEDULE || relation.jobId() != null) throw changed();
    var latest = operations.latest(entity, itemId, ExternalOperationRepository.Kind.VWORK_CREATE_JOB);
    if (latest.isPresent() && latest.get().status() != OperationState.Status.FAILED)
      throw new ApiFailure(HttpStatus.CONFLICT, "OPERATION_ALREADY_EXISTS", "Review the existing job operation first.");
    var quote = jdbc.query("select xero_quote_id,quote_number,job_id from public.work_items where entity_id=? and id=?",
        (r,n) -> new Quote(r.getObject("xero_quote_id", UUID.class), r.getString("quote_number"), r.getString("job_id")), entity, itemId)
        .stream().findFirst().orElseThrow(VworkJobDraftContext::unavailable);
    return new Context(quote.id(), quote.number(), quote.reference(), item.version(), templates.read(entity));
  }
  private static ApiFailure unavailable() { return new ApiFailure(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "This work item is unavailable."); }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT, "STAGE_ACTION_NOT_ALLOWED", "A job cannot be created for this item."); }
}
