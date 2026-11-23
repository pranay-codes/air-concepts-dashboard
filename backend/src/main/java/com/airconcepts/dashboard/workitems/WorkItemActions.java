package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.workitems.operations.QuoteAcceptService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps each local mutation and its returned view in one transaction. */
@Service
public class WorkItemActions {
  private final WorkItemService writes;
  private final WorkItemReadService reads;
  private final QuoteAcceptService quoteAccept;
  public WorkItemActions(WorkItemService writes, WorkItemReadService reads, QuoteAcceptService quoteAccept) {
    this.writes = writes; this.reads = reads; this.quoteAccept = quoteAccept;
  }
  /** Not transactional: the Xero call happens between the service's own reservation and completion transactions. */
  public WorkItemReadService.Detail acceptQuote(CurrentUser user, UUID entity, UUID item, int version) {
    quoteAccept.submit(user, entity, item, version);
    return reads.get(user, entity, item);
  }

  @Transactional
  public WorkItemReadService.Detail edit(CurrentUser user, UUID entity, UUID item, int version,
      boolean datePresent, LocalDate date, boolean clear, boolean notesPresent, String notes,
      boolean ownerPresent, UUID ownerId, boolean clearOwner) {
    writes.editItem(user, entity, item, version, datePresent, date, clear, notesPresent, notes,
        ownerPresent, ownerId, clearOwner);
    return reads.get(user, entity, item);
  }
  @Transactional
  public WorkItemReadService.Detail closeLost(CurrentUser user, UUID entity, UUID item, int version, String reason) {
    writes.closeLost(user, entity, item, version, reason);
    return reads.get(user, entity, item);
  }
  @Transactional
  public WorkItemReadService.Detail closeNoInvoice(CurrentUser user, UUID entity, UUID item, int version, String reason) {
    writes.closeNoInvoice(user, entity, item, version, reason);
    return reads.get(user, entity, item);
  }
  @Transactional
  public WorkItemReadService.Detail markSent(CurrentUser user, UUID entity, UUID item, int version) {
    writes.markSent(user,entity,item,version);
    return reads.get(user,entity,item);
  }
}
