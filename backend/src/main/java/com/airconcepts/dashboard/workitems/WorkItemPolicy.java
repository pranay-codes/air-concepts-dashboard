package com.airconcepts.dashboard.workitems;

import com.airconcepts.dashboard.security.Role;

/** Pure role and state checks; callers must also verify the item belongs to the requested entity. */
public final class WorkItemPolicy {
  private WorkItemPolicy() {}

  public static boolean allowed(WorkItemAction action, Role role, boolean active, boolean owner,
      boolean serviceManager, WorkItemStage stage, boolean hasJob, boolean hasInvoice) {
    if (!active || role == null || action == null || stage == null) return false;
    boolean finance = role == Role.DIRECTOR || role == Role.FINANCIAL_ADMIN;
    return switch (action) {
      case EDIT_NOTES -> stage != WorkItemStage.CLOSED && (finance || owner);
      case CLOSE_LOST -> stage == WorkItemStage.QUOTED && finance;
      case CREATE_JOB -> stage == WorkItemStage.TO_SCHEDULE && !hasJob
          && (role == Role.DIRECTOR || serviceManager);
      case PREPARE_INVOICE, NO_INVOICE_NEEDED -> stage == WorkItemStage.TO_INVOICE && !hasInvoice && finance;
      case MARK_SENT -> stage == WorkItemStage.INVOICE_DRAFTED && hasInvoice && finance;
      case MARK_ACCEPTED -> stage == WorkItemStage.QUOTED && !hasJob && !hasInvoice && finance;
    };
  }
}
