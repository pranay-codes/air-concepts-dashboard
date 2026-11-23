import { describe, expect, it } from "vitest";
import type { WorkItem } from "./contracts";
import {
  businessToday,
  categoryCounts,
  collectRows,
  defaultFilters,
  isPastFollowUp,
  syncView,
  visibleRows,
  type WorkFilters,
} from "./myWorkModel";

const uuid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, "0")}`;
const item = (n: number, overrides: Partial<WorkItem> = {}): WorkItem =>
  ({
    id: uuid(n),
    entityId: uuid(1),
    entityName: "First entity",
    xeroQuoteId: uuid(n + 500),
    quoteNumber: `Q-${n}`,
    xeroQuoteStatus: "SENT",
    stage: "QUOTED",
    stageEnteredAt: "2026-11-19T00:00:00Z",
    daysInStage: 1,
    late: false,
    flags: [],
    attention: [],
    version: 0,
    ...overrides,
  }) as WorkItem;
const work = (
  parts: Partial<{ needsAction: WorkItem[]; waiting: WorkItem[]; needsAttention: WorkItem[] }>,
) => ({
  needsAction: [],
  waiting: [],
  needsAttention: [],
  generatedAt: "2026-11-19T00:00:00Z",
  truncated: false,
  ...parts,
});
const run = (status: "RUNNING" | "SUCCEEDED" | "FAILED", finishedAt?: string) => ({
  id: uuid(900),
  status,
  startedAt: "2026-11-20T00:00:00Z",
  ...(finishedAt ? { finishedAt } : {}),
});
const filters = (patch: Partial<WorkFilters>): WorkFilters => ({ ...defaultFilters, ...patch });

describe("GL-10 My Work model", () => {
  it("joins the queues in priority order with one category per item", () => {
    const rows = collectRows(
      work({
        waiting: [item(3)],
        needsAction: [item(2)],
        needsAttention: [item(1)],
      }),
    );
    expect(rows.map((row) => [row.item.quoteNumber, row.category, row.order])).toEqual([
      ["Q-1", "ATTENTION", 0],
      ["Q-2", "ACTION", 1],
      ["Q-3", "WAITING", 2],
    ]);
  });

  it("counts each queue with its server total and all work once per item", () => {
    const data = work({ needsAction: [item(1), item(2)], waiting: [item(3)] });
    const rows = collectRows(data);
    expect(categoryCounts(data, rows)).toEqual({ ALL: 3, ACTION: 2, ATTENTION: 0, WAITING: 1 });
  });

  it("filters by category, stage and attention status", () => {
    const rows = collectRows(
      work({
        needsAction: [item(1, { stage: "QUOTED" }), item(2, { stage: "SCHEDULED", late: true })],
        needsAttention: [item(3, { attention: ["NO_DEFAULT_OWNER"] })],
      }),
    );
    expect(
      visibleRows(rows, filters({ category: "ACTION" })).map((r) => r.item.quoteNumber),
    ).toEqual(["Q-1", "Q-2"]);
    expect(
      visibleRows(rows, filters({ stage: "SCHEDULED" })).map((r) => r.item.quoteNumber),
    ).toEqual(["Q-2"]);
    expect(
      visibleRows(rows, filters({ attention: "LATE" })).map((r) => r.item.quoteNumber),
    ).toEqual(["Q-2"]);
    expect(
      visibleRows(rows, filters({ attention: "FLAGGED" })).map((r) => r.item.quoteNumber),
    ).toEqual(["Q-3"]);
    expect(
      visibleRows(rows, filters({ attention: "NONE" })).map((r) => r.item.quoteNumber),
    ).toEqual(["Q-1"]);
  });

  it("sorts follow-up dates with missing dates last and time in stage longest first", () => {
    const rows = collectRows(
      work({
        needsAction: [
          item(1, { followUpDate: "2026-12-02", daysInStage: 2 }),
          item(2, { daysInStage: 9 }),
          item(3, { followUpDate: "2026-11-30", daysInStage: 4 }),
        ],
      }),
    );
    expect(
      visibleRows(rows, filters({ sort: "FOLLOW_UP" })).map((r) => r.item.quoteNumber),
    ).toEqual(["Q-3", "Q-1", "Q-2"]);
    expect(
      visibleRows(rows, filters({ sort: "TIME_IN_STAGE" })).map((r) => r.item.quoteNumber),
    ).toEqual(["Q-2", "Q-3", "Q-1"]);
  });

  it("derives a sync label from backend-recorded runs only", () => {
    expect(syncView(undefined)).toEqual({ label: "Not synced", tone: "warning" });
    expect(syncView({})).toEqual({ label: "Not synced", tone: "warning" });
    expect(syncView({ latest: run("RUNNING") })).toEqual({ label: "Syncing", tone: "information" });
    expect(syncView({ latest: run("FAILED") })).toEqual({ label: "Sync failed", tone: "error" });
    expect(
      syncView({ latest: run("FAILED"), lastSuccess: run("SUCCEEDED", "2026-11-20T01:00:00Z") }),
    ).toEqual({ label: "Sync failed", tone: "error", lastSynced: "2026-11-20T01:00:00Z" });
    expect(syncView({ lastSuccess: run("SUCCEEDED", "2026-11-20T01:00:00Z") })).toEqual({
      label: "Synced",
      tone: "success",
      lastSynced: "2026-11-20T01:00:00Z",
    });
  });

  it("uses the Pacific/Auckland business date and treats only earlier dates as past due", () => {
    expect(businessToday(new Date("2026-11-23T10:00:00Z"))).toBe("2026-11-23");
    expect(businessToday(new Date("2026-11-23T11:30:00Z"))).toBe("2026-11-24");
    expect(isPastFollowUp(item(1, { followUpDate: "2026-11-22" }), "2026-11-23")).toBe(true);
    expect(isPastFollowUp(item(1, { followUpDate: "2026-11-23" }), "2026-11-23")).toBe(false);
    expect(isPastFollowUp(item(1), "2026-11-23")).toBe(false);
  });
});
