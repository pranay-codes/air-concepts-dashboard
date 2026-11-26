import { describe, expect, it } from "vitest";
import type { Overview, OverviewCycleTime, OverviewItem } from "./overviewContracts";
import {
  attentionItems,
  decisions,
  owners,
  periodPresets,
  periodProblem,
  slowestStep,
} from "./overviewModel";

const item = (id: string, summary: object = {}) =>
  ({
    summary: { id, daysInStage: 1, late: false, attention: [], ...summary },
  }) as unknown as OverviewItem;
const person = (id: string) => ({ owner: { id, displayName: id } });
const data = (overrides: Partial<Overview> = {}) =>
  ({
    stages: [],
    openItems: [],
    openTruncated: false,
    uninvoiced: [],
    unsent: [],
    ...overrides,
  }) as Overview;
const step = (name: OverviewCycleTime["step"], medianDays: number | null) =>
  ({ step: name, medianDays, sampleSize: medianDays === null ? 0 : 8 }) as OverviewCycleTime;

describe("GL-24 Director overview rules", () => {
  it("GL-DIR-02 lists only the decisions that apply, money first", () => {
    const found = decisions(
      data({
        stages: [
          { stage: "JOB_COMPLETED", count: 2, totals: [{ currency: "NZD", amount: "300.00" }] },
          { stage: "INVOICE_CREATED", count: 1, totals: [{ currency: "NZD", amount: "50.00" }] },
        ],
        uninvoiced: [item("a", { daysInStage: 9 }), item("b", { daysInStage: 2 })],
        unsent: [item("c", { daysInStage: 4 })],
        openItems: [item("a", { late: true, attention: ["X"] }), item("b", person("p"))],
      }),
    );
    expect(found.map((row) => [row.key, row.count])).toEqual([
      ["uninvoiced", "2"],
      ["unsent", "1"],
      ["flagged", "1"],
      ["late", "1"],
      ["unassigned", "1"],
    ]);
    // Money formatting joins the currency and amount with a non-breaking space.
    const detail = (n: number) => found[n]!.detail.replace(/\s/g, " ");
    expect(detail(0)).toBe("NZD 300.00 quoted · longest wait 9 days");
    expect(detail(1)).toBe("NZD 50.00 quoted · longest wait 4 days");
  });

  it("GL-DIR-02 has no decisions when nothing is waiting, late, flagged or unowned", () => {
    expect(decisions(data({ openItems: [item("a", person("p"))] }))).toEqual([]);
  });

  it("GL-DIR-06 says at least when the open list is cut off", () => {
    const [late] = decisions(
      data({ openItems: [item("a", { late: true, ...person("p") })], openTruncated: true }),
    );
    expect(late!.count).toBe("At least 1");
  });

  it("GL-DIR-01 puts flagged then late then longest waiting jobs first", () => {
    const rows = attentionItems([
      item("fine"),
      item("late-new", { late: true, daysInStage: 1 }),
      item("late-old", { late: true, daysInStage: 8 }),
      item("flagged", { attention: ["X"] }),
    ]);
    expect(rows.map((row) => row.summary.id)).toEqual(["flagged", "late-old", "late-new"]);
  });

  it("GL-DIR-01 orders people by jobs needing attention before workload", () => {
    const groups = owners([
      item("1", person("busy")),
      item("2", person("busy")),
      item("3", { late: true, ...person("stuck") }),
      item("4"),
    ]);
    expect(groups.map((g) => [g.name, g.items.length, g.late, g.flagged])).toEqual([
      ["stuck", 1, 1, 0],
      ["busy", 2, 0, 0],
      ["Unassigned", 1, 0, 0],
    ]);
  });

  it("GL-DIR-03 finds the slowest single step and ignores the combined span", () => {
    expect(
      slowestStep([
        step("QUOTE_TO_ACCEPTED", 4),
        step("QUOTE_TO_SCHEDULED", 20),
        step("SCHEDULED_TO_COMPLETED", 6),
        step("COMPLETED_TO_SENT", null),
      ]),
    ).toBe("SCHEDULED_TO_COMPLETED");
    expect(slowestStep([step("QUOTE_TO_ACCEPTED", 0)])).toBeNull();
  });

  it("GL-DIR-03 builds period presets from the Auckland date", () => {
    expect(periodPresets("2027-01-05").map((p) => [p.label, p.period.from, p.period.to])).toEqual([
      ["Last 7 days", "2026-12-30", "2027-01-05"],
      ["Last 30 days", "2026-12-07", "2027-01-05"],
      ["This month", "2027-01-01", "2027-01-05"],
      ["Last month", "2026-12-01", "2026-12-31"],
      ["Last 90 days", "2026-10-08", "2027-01-05"],
    ]);
  });

  it("GL-DIR-03 explains an impossible period", () => {
    expect(periodProblem({ from: "", to: "2026-11-01" })).toMatch(/both/);
    expect(periodProblem({ from: "2026-11-02", to: "2026-11-01" })).toMatch(/on or before/);
    expect(periodProblem({ from: "2025-01-01", to: "2026-11-01" })).toMatch(/366 days/);
    expect(periodProblem({ from: "2026-11-01", to: "2026-11-01" })).toBeNull();
  });
});
