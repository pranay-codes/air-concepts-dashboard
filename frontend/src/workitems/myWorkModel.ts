import type { components } from "../api/generated/schema";
import type { MyWork, WorkItem } from "./contracts";

/** Display model for My Work. Business categories come from the server; this file only groups, filters and orders them. */
export type WorkCategory = "ACTION" | "ATTENTION" | "WAITING";
export type CategoryFilter = "ALL" | WorkCategory;
export type StageFilter = "ALL" | WorkItem["stage"];
export type AttentionFilter = "ANY" | "FLAGGED" | "LATE" | "NONE";
export type WorkSort = "PRIORITY" | "OVERDUE" | "FOLLOW_UP" | "TIME_IN_STAGE";
export type WorkFilters = {
  category: CategoryFilter;
  stage: StageFilter;
  attention: AttentionFilter;
  sort: WorkSort;
};
export type WorkRow = { item: WorkItem; category: WorkCategory; order: number };

export const defaultFilters: WorkFilters = {
  category: "ALL",
  stage: "ALL",
  attention: "ANY",
  sort: "PRIORITY",
};

/**
 * Joins the three server queues in priority order: attention, then your action, then waiting.
 * The API rejects a response that repeats an item across queues, so each item has one category.
 */
export function collectRows(work: MyWork): WorkRow[] {
  const sources: [WorkCategory, WorkItem[]][] = [
    ["ATTENTION", work.needsAttention],
    ["ACTION", work.needsAction],
    ["WAITING", work.waiting],
  ];
  return sources
    .flatMap(([category, items]) => items.map((item) => ({ item, category })))
    .map((row, order) => ({ ...row, order }));
}

/** Counts per summary tile. Each queue keeps its server count, and "All work" is the joined total. */
export function categoryCounts(work: MyWork, rows: WorkRow[]) {
  return {
    ALL: rows.length,
    ACTION: work.needsAction.length,
    ATTENTION: work.needsAttention.length,
    WAITING: work.waiting.length,
  } satisfies Record<CategoryFilter, number>;
}

function attentionMatches(item: WorkItem, filter: AttentionFilter): boolean {
  switch (filter) {
    case "FLAGGED":
      return item.attention.length > 0;
    case "LATE":
      return item.late;
    case "NONE":
      return item.attention.length === 0 && !item.late;
    default:
      return true;
  }
}

/** Items with no follow-up date sort last. ISO dates compare correctly as text. */
function compareFollowUp(a: WorkItem, b: WorkItem): number {
  const left = a.followUpDate;
  const right = b.followUpDate;
  if (left === right) return 0;
  if (!left) return 1;
  if (!right) return -1;
  return left < right ? -1 : 1;
}

function comparator(sort: WorkSort): (a: WorkRow, b: WorkRow) => number {
  switch (sort) {
    case "OVERDUE":
      return (a, b) =>
        Number(b.item.late) - Number(a.item.late) ||
        compareFollowUp(a.item, b.item) ||
        b.item.daysInStage - a.item.daysInStage ||
        a.order - b.order;
    case "FOLLOW_UP":
      return (a, b) => compareFollowUp(a.item, b.item) || a.order - b.order;
    case "TIME_IN_STAGE":
      return (a, b) => b.item.daysInStage - a.item.daysInStage || a.order - b.order;
    default:
      return (a, b) => a.order - b.order;
  }
}

export function visibleRows(rows: WorkRow[], filters: WorkFilters): WorkRow[] {
  return rows
    .filter((row) => filters.category === "ALL" || row.category === filters.category)
    .filter((row) => filters.stage === "ALL" || row.item.stage === filters.stage)
    .filter((row) => attentionMatches(row.item, filters.attention))
    .sort(comparator(filters.sort));
}

/** The business date in Pacific/Auckland as YYYY-MM-DD, used to tell whether a follow-up has passed. */
export function businessToday(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-NZ", {
    timeZone: "Pacific/Auckland",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return `${value("year")}-${value("month")}-${value("day")}`;
}

export function isPastFollowUp(item: WorkItem, today: string): boolean {
  return typeof item.followUpDate === "string" && item.followUpDate < today;
}

type SystemSync = components["schemas"]["GreenLineSystemSyncStatus"];
export type SyncView = {
  label: "Synced" | "Syncing" | "Sync failed" | "Not synced";
  tone: "success" | "information" | "error" | "warning";
  /** Set only when the backend has recorded a successful run. */
  lastSynced?: string;
};

/** Sync state comes from backend-recorded runs only. A successful sync is never assumed. */
export function syncView(system: SystemSync | undefined): SyncView {
  const lastSynced = system?.lastSuccess?.finishedAt;
  if (system?.latest?.status === "RUNNING")
    return { label: "Syncing", tone: "information", lastSynced };
  if (system?.latest?.status === "FAILED")
    return { label: "Sync failed", tone: "error", lastSynced };
  if (system?.lastSuccess) return { label: "Synced", tone: "success", lastSynced };
  return { label: "Not synced", tone: "warning" };
}
