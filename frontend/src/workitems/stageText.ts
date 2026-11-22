import type { WorkItem } from "./contracts";

export const stageLabels: Record<WorkItem["stage"], string> = {
  QUOTED: "Quoted",
  TO_SCHEDULE: "To schedule",
  SCHEDULED: "Scheduled",
  TO_INVOICE: "To invoice",
  INVOICE_DRAFTED: "Invoice drafted",
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
  };
  return labels[event] ?? event.replaceAll("_", " ").toLowerCase();
}
