import { formatMoney } from "../ui/format";
import type { Overview, OverviewCycleTime, OverviewItem, OverviewMoney } from "./overviewContracts";

/**
 * Display rules for the Director overview (GL-24). Money is never added here: values are the server's totals.
 */
export type Period = { from: string; to: string };

export function daysBefore(date: string, count: number): string {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() - count);
  return value.toISOString().slice(0, 10);
}

export function periodProblem({ from, to }: Period): string | null {
  if (!from || !to) return "Enter both a start date and an end date.";
  if (from > to) return "The start date must be on or before the end date.";
  if (from <= daysBefore(to, 366)) return "Choose a period of 366 days or less.";
  return null;
}

export function periodPresets(today: string): { label: string; period: Period }[] {
  const monthStart = `${today.slice(0, 8)}01`;
  const lastMonthEnd = daysBefore(monthStart, 1);
  const recent = (days: number) => ({ from: daysBefore(today, days - 1), to: today });
  return [
    { label: "Last 7 days", period: recent(7) },
    { label: "Last 30 days", period: recent(30) },
    { label: "This month", period: { from: monthStart, to: today } },
    { label: "Last month", period: { from: `${lastMonthEnd.slice(0, 8)}01`, to: lastMonthEnd } },
    { label: "Last 90 days", period: recent(90) },
  ];
}

export const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? "" : "s"}`;
export const dayText = (days: number) => `${days} ${days === 1 ? "day" : "days"}`;
export const amounts = (totals: OverviewMoney[]) =>
  totals.length
    ? totals.map((total) => formatMoney(total.amount, total.currency)).join(" + ")
    : "No value recorded";

export const stageOf = (data: Overview, stage: Overview["stages"][number]["stage"]) =>
  data.stages.find((row) => row.stage === stage) ?? { stage, count: 0, totals: [] };
export const quoted = (totals: OverviewMoney[]) =>
  totals.length ? `${amounts(totals)} quoted` : "No value recorded";

const longestDays = (items: OverviewItem[]) =>
  items.reduce((most, item) => Math.max(most, item.summary.daysInStage), 0);
const isFlagged = (item: OverviewItem) => item.summary.attention.length > 0;

/** Open jobs that are late or flagged: flagged first, then late, then longest in stage. */
export function attentionItems(items: OverviewItem[]): OverviewItem[] {
  return items
    .filter((item) => item.summary.late || isFlagged(item))
    .sort(
      (a, b) =>
        Number(isFlagged(b)) - Number(isFlagged(a)) ||
        Number(b.summary.late) - Number(a.summary.late) ||
        b.summary.daysInStage - a.summary.daysInStage,
    );
}

export type Decision = {
  key: "uninvoiced" | "unsent" | "flagged" | "late" | "unassigned";
  count: string;
  title: string;
  detail: string;
  nextStep: string;
  target: string;
  linkLabel: string;
};

/** Only the rows that apply, in a fixed order: money waiting, then blocked, late and unowned work. */
export function decisions(data: Overview): Decision[] {
  const found: Decision[] = [];
  const add = (
    size: number,
    atLeast: boolean,
    nouns: [string, string],
    row: Omit<Decision, "count" | "title">,
  ) => {
    if (!size) return;
    const count = atLeast ? `At least ${size}` : String(size);
    found.push({ ...row, count, title: `${count} ${nouns[size === 1 ? 0 : 1]}` });
  };
  const money = (items: OverviewItem[], totals: OverviewMoney[]) =>
    `${quoted(totals)} · longest wait ${dayText(longestDays(items))}`;
  const completed = stageOf(data, "JOB_COMPLETED");
  add(completed.count, false, ["completed job not invoiced", "completed jobs not invoiced"], {
    key: "uninvoiced",
    detail: money(data.uninvoiced, completed.totals),
    nextStep: "Ask each job owner to raise the invoice.",
    target: "overview-uninvoiced",
    linkLabel: "See uninvoiced jobs",
  });
  const created = stageOf(data, "INVOICE_CREATED");
  add(created.count, false, ["invoice created but not sent", "invoices created but not sent"], {
    key: "unsent",
    detail: money(data.unsent, created.totals),
    nextStep: "Ask each job owner to review and send the invoice.",
    target: "overview-unsent",
    linkLabel: "See unsent invoices",
  });
  const openRow = (
    key: Decision["key"],
    items: OverviewItem[],
    nouns: [string, string],
    nextStep: string,
    target: string,
    linkLabel: string,
  ) =>
    add(items.length, data.openTruncated, nouns, {
      key,
      detail: `Longest ${dayText(longestDays(items))} in the current stage`,
      nextStep,
      target,
      linkLabel,
    });
  openRow(
    "flagged",
    data.openItems.filter(isFlagged),
    ["job flagged for attention", "jobs flagged for attention"],
    "Open each job to read the reason and what to do.",
    "overview-attention",
    "See flagged jobs",
  );
  openRow(
    "late",
    data.openItems.filter((item) => item.summary.late),
    ["late job", "late jobs"],
    "Agree the next step and date with each job owner.",
    "overview-attention",
    "See late jobs",
  );
  openRow(
    "unassigned",
    data.openItems.filter((item) => !item.summary.owner),
    ["job with no owner", "jobs with no owner"],
    "Set the entity's owners in Settings, under Workflow settings.",
    "overview-team",
    "See jobs by person",
  );
  return found;
}

/** People with late or flagged jobs first, then by open jobs, then by name. */
export function owners(items: OverviewItem[]) {
  const groups = new Map<string, { key: string; name: string; items: OverviewItem[] }>();
  for (const item of items) {
    const key = item.summary.owner?.id ?? "unassigned";
    const group = groups.get(key) ?? {
      key,
      name: item.summary.owner?.displayName ?? "Unassigned",
      items: [],
    };
    group.items.push(item);
    groups.set(key, group);
  }
  return [...groups.values()]
    .map((group) => ({
      ...group,
      late: group.items.filter((item) => item.summary.late).length,
      flagged: group.items.filter(isFlagged).length,
      atRisk: attentionItems(group.items).length,
    }))
    .sort(
      (a, b) =>
        b.atRisk - a.atRisk || b.items.length - a.items.length || a.name.localeCompare(b.name),
    );
}

/** Below this many measured jobs a median is shown with a caution. A reading aid, not a business rule. */
export const FEW_JOBS = 5;

/** The one single step with the longest median. The combined quote-to-scheduled span is not a single step. */
export function slowestStep(rows: OverviewCycleTime[]): OverviewCycleTime["step"] | null {
  const measured = rows
    .filter((row) => row.step !== "QUOTE_TO_SCHEDULED" && row.medianDays != null)
    .sort((a, b) => b.medianDays! - a.medianDays!);
  const [first, second] = measured;
  return first && second && first.medianDays! > second.medianDays! ? first.step : null;
}
