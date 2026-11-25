import { formatDate, formatFetchTime } from "../ui/format";
import type { WorkItemDetail } from "./contracts";
import { eventLabel, stageLabels } from "./stageText";

export type ActivityEvent = WorkItemDetail["events"][number];
type Job = NonNullable<NonNullable<ActivityEvent["details"]>["toJob"]>;
export type ActivityChange = { label: string; from?: string; to: string };
export type ActivityEntry = {
  key: number;
  at: string;
  title: string;
  actor: string;
  changes: ActivityChange[];
};
type Move = { from?: string; to: string };

const stageOrder = Object.keys(stageLabels);
const milestones: Record<string, string> = {
  TO_SCHEDULE: "Ready to schedule",
  SCHEDULED: "Job scheduled",
  WORK_IN_PROGRESS: "Work started",
  JOB_COMPLETED: "Job completed",
  INVOICE_CREATED: "Invoice created",
  INVOICED: "Invoice sent",
};
/** Events whose own name says more than the stage they moved to. */
const namedEvents = [
  "ITEM_CREATED",
  "QUOTE_ACCEPTED",
  "CLOSED_LOST",
  "CLOSED_NO_INVOICE",
  "MARKED_SENT",
  "INVOICE_UNLINKED",
];
const edits = new Set(["OWNER_CHANGED", "FOLLOW_UP_CHANGED", "NOTES_CHANGED"]);
const jobStatuses: Record<string, string> = {
  UNASSIGNED: "Unassigned",
  ASSIGNED: "Assigned",
  STARTED: "Started",
  PAUSED: "Paused",
  COMPLETED: "Completed",
  CANCELED: "Cancelled",
};
const syncSources: Record<string, string> = { XERO_SYNC: "Xero sync", VWORK_SYNC: "vWork sync" };

const stageName = (stage: string) => stageLabels[stage as keyof typeof stageLabels] ?? stage;
const time = (value: string | undefined) => (value ? formatFetchTime(value) : undefined);

function stageTitle({ from, to }: Move, closeReason: string | undefined): string {
  if (to === "CLOSED") {
    if (closeReason === "PAID") return "Invoice paid";
    return closeReason === "LOST" ? "Closed as lost" : "Job closed";
  }
  if (from === "CLOSED") return "Reopened";
  if (from && stageOrder.indexOf(to) < stageOrder.indexOf(from))
    return `Moved back to ${stageName(to)}`;
  return milestones[to] ?? "Stage changed";
}

function jobTitle(from: Job | undefined, to: Job): string | undefined {
  if (to.status && from?.status !== to.status) {
    const titles: Record<string, string | undefined> = {
      UNASSIGNED: from ? "Job unassigned" : undefined,
      ASSIGNED: "Job scheduled",
      STARTED: from?.status === "PAUSED" ? "Work resumed" : "Work started",
      PAUSED: "Work paused",
      COMPLETED: "Job completed",
      CANCELED: "Job cancelled",
    };
    return titles[to.status];
  }
  if (!from) return undefined;
  const before = time(from.scheduledStart);
  const after = time(to.scheduledStart);
  if (before !== after) {
    if (before && after) return "Job rescheduled";
    return after ? "Job scheduled" : "Schedule removed";
  }
  return from.technician !== to.technician ? "Technician changed" : undefined;
}

/** A value that changed shows both sides; a first or newly relevant value shows on its own. */
function jobValue(
  label: string,
  before: string | undefined,
  after: string | undefined,
  scheduling: boolean,
): ActivityChange[] {
  if (before && before !== after) return [{ label, from: before, to: after ?? "Not set" }];
  return after && (!before || scheduling) ? [{ label, to: after }] : [];
}

function jobChanges(from: Job | undefined, to: Job, move: Move | undefined): ActivityChange[] {
  const changes: ActivityChange[] = [];
  if (!move && from?.status && to.status && from.status !== to.status)
    changes.push({
      label: "vWork status",
      from: jobStatuses[from.status] ?? from.status,
      to: jobStatuses[to.status] ?? to.status,
    });
  const scheduling = move?.to === "SCHEDULED";
  changes.push(
    ...jobValue("Scheduled for", time(from?.scheduledStart), time(to.scheduledStart), scheduling),
    ...jobValue("Technician", from?.technician, to.technician, scheduling),
  );
  const completed = time(to.completedAt);
  if (completed && completed !== time(from?.completedAt))
    changes.push({ label: "Completed in vWork", to: completed });
  return changes;
}

function entry(group: ActivityEvent[]): ActivityEntry {
  const events = [...group].sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
  const first = events[0]!;
  const types = events.map((event) => event.eventType ?? "");
  const moves = events.filter((event) => event.toStage && event.fromStage !== event.toStage);
  const move: Move | undefined = moves.length
    ? { from: moves[0]!.fromStage, to: moves.at(-1)!.toStage! }
    : undefined;
  const details = events.map((event) => event.details);
  const job = details.find((value) => value?.toJob);
  const changes: ActivityChange[] = [];

  if (move)
    changes.push({
      label: "Stage",
      ...(move.from ? { from: stageName(move.from) } : {}),
      to: stageName(move.to),
    });
  const owners = events.filter((event) => event.fromOwner?.id !== event.toOwner?.id);
  if (owners.length) {
    const to = owners.at(-1)!.toOwner?.displayName ?? "Unassigned";
    changes.push(
      types.includes("ITEM_CREATED")
        ? { label: "Owner", to }
        : { label: "Owner", from: owners[0]!.fromOwner?.displayName ?? "Unassigned", to },
    );
  }
  const followUp = events.find((event) => event.eventType === "FOLLOW_UP_CHANGED")?.details;
  if (followUp) {
    const date = (value: string | undefined) => (value ? formatDate(value) : "Not set");
    changes.push({
      label: "Follow-up",
      from: date(followUp.fromFollowUpDate),
      to: date(followUp.toFollowUpDate),
    });
  }
  // Note text can be long and is already on the page, so only the fact of the edit is listed.
  if (events.length > 1 && types.includes("NOTES_CHANGED"))
    changes.push({ label: "Notes", to: "Updated" });
  if (job?.toJob) changes.push(...jobChanges(job.fromJob, job.toJob, move));
  const invoice = details.find((value) => value?.invoiceNumber)?.invoiceNumber;
  if (invoice) changes.push({ label: "Invoice", to: invoice });
  const reason = details.find((value) => value?.reason)?.reason;
  if (reason && move?.to === "CLOSED") changes.push({ label: "Reason", to: reason });

  const named = namedEvents.find((type) => types.includes(type));
  let title: string | undefined;
  if (named) title = eventLabel(named);
  else if (move) title = stageTitle(move, details.find((value) => value?.closeReason)?.closeReason);
  else if (types.includes("INVOICE_LINKED")) title = eventLabel("INVOICE_LINKED");
  else if (job?.toJob) title = jobTitle(job.fromJob, job.toJob);
  else if (new Set(types).size > 1 && types.every((type) => edits.has(type)))
    title = "Details updated";

  const person = events.find((event) => event.actor)?.actor?.displayName;
  const system = events.some((event) => event.source === "SYSTEM");
  return {
    key: first.id ?? 0,
    at: first.at ?? "",
    title: title ?? eventLabel(types[0]!),
    actor: person ?? (system ? "System" : (syncSources[first.source ?? ""] ?? "System")),
    changes,
  };
}

/** Rows written by one action share its recorded time and read as one entry. Input order is kept. */
export function activityEntries(events: ActivityEvent[]): ActivityEntry[] {
  const groups: ActivityEvent[][] = [];
  for (const event of events) {
    const current = groups.at(-1);
    if (current && current[0]!.at === event.at) current.push(event);
    else groups.push([event]);
  }
  return groups.map(entry);
}
