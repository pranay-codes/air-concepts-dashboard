import type { WorkItem } from "./contracts";

/** Labels follow the approved stage set: Quoted, To schedule, Job scheduled, Work in progress, Job completed, Invoice created, Invoiced. */
export const stageLabels: Record<WorkItem["stage"], string> = {
  QUOTED: "Quoted",
  TO_SCHEDULE: "To schedule",
  SCHEDULED: "Job scheduled",
  WORK_IN_PROGRESS: "Work in progress",
  JOB_COMPLETED: "Job completed",
  INVOICE_CREATED: "Invoice created",
  INVOICED: "Invoiced",
  CLOSED: "Closed",
};
const flags: Record<string, string> = {
  JOB_ID_MISSING: "Needs a Job ID",
  JOB_ID_TOO_LONG: "Job ID too long",
  JOB_ID_DUPLICATE: "Duplicate Job ID",
  JOB_ID_MISMATCH: "Job ID mismatch",
  REPORT_NOT_ATTACHED: "Report not attached",
  LATE: "Late",
};
export function flagLabel(flag: string): string {
  return flags[flag] ?? flag.replaceAll("_", " ").toLowerCase();
}
export function eventLabel(event: string): string {
  const labels: Record<string, string> = {
    ITEM_CREATED: "Work item created",
    STAGE_CHANGED: "Stage changed",
    FOLLOW_UP_CHANGED: "Follow-up changed",
    NOTES_CHANGED: "Notes changed",
    CLOSED_LOST: "Closed as lost",
    CLOSED_NO_INVOICE: "Closed without invoice",
    JOB_LINKED: "Job linked",
    INVOICE_LINKED: "Invoice linked",
    INVOICE_UNLINKED: "Invoice unlinked",
    ATTENTION_RAISED: "Attention required",
    MARKED_SENT: "Invoice marked sent",
    QUOTE_ACCEPTED: "Quote accepted in Xero",
    QUOTE_ACCEPT_REQUESTED: "Quote acceptance requested",
    OPERATION_OUTCOME: "External write outcome recorded",
    OWNER_CHANGED: "Owner changed",
    OWNER_ASSIGNED: "Owner assigned",
    JOB_UPDATED: "Job updated",
    JOB_CREATE_REQUESTED: "Job creation requested",
    INVOICE_REQUESTED: "Invoice creation requested",
  };
  return labels[event] ?? event.replaceAll("_", " ").toLowerCase();
}

/** Plain-language reason for each attention code the server can set. Unknown codes get a safe generic reason. */
export function attentionLabel(code: string, stage?: WorkItem["stage"]): string {
  switch (code) {
    case "NO_DEFAULT_OWNER":
      return "No default owner is set for this entity. A Director can set one in Workflow settings.";
    case "NO_SERVICE_MANAGER":
      return "No Service Manager is set for this entity. Set one in Workflow settings before accepted quotes can be handed off.";
    case "OWNER_INACTIVE":
      return "The current owner is no longer active. Assign another owner.";
    case "QUOTE_INVOICED_IN_XERO":
      return "This quote was already invoiced in Xero. Check the invoice before continuing.";
    case "INVOICE_MULTIPLE_MATCHES":
      return "Multiple Xero invoices match this job. Review them in Xero before continuing.";
    case "INVOICE_VOIDED":
      return "The linked Xero invoice was voided. Review the job before continuing.";
    case "XERO_STATUS_REGRESSED":
      return "Xero now shows an earlier status for this quote. Check it in Xero.";
    case "VWORK_JOB_CANCELLED":
      return "The vWork job was cancelled. Check it in vWork before continuing.";
    case "OPERATION_UNKNOWN":
      return "A write to Xero needs review. Check the quote or invoice in Xero before trying again.";
    case "OPERATION_FAILED":
      return stage === "QUOTED"
        ? "The last acceptance in Xero did not apply. Review the quote, then try again."
        : "The last write did not apply. Review the job before trying again.";
    default:
      return "This job needs review.";
  }
}
