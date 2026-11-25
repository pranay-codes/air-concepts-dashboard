import { isRecord } from "../api/client";
import type { components } from "../api/generated/schema";
import { isWorkItem, type WorkItem } from "./contracts";

type Schemas = components["schemas"];
export type OverviewItem = Omit<Schemas["OverviewItem"], "summary"> & { summary: WorkItem };
export type OverviewMoney = Schemas["OverviewMoney"] & { amount: string };
export type OverviewStageTotal = {
  stage: WorkItem["stage"];
  count: number;
  totals: OverviewMoney[];
};
export type OverviewClosedTotal = Required<Schemas["OverviewClosedTotal"]>;
export type OverviewCycleTime = Required<
  Omit<Schemas["OverviewCycleTime"], "medianDays" | "averageDays">
> & { medianDays?: number | null; averageDays?: number | null };
export type OverviewPersonCount = { id?: string; name: string; count: number };
export type Overview = {
  generatedAt: string;
  from: string;
  to: string;
  stages: OverviewStageTotal[];
  closed: OverviewClosedTotal[];
  openItems: OverviewItem[];
  openTruncated: boolean;
  uninvoiced: OverviewItem[];
  uninvoicedTruncated: boolean;
  unsent: OverviewItem[];
  unsentTruncated: boolean;
  cycleTimes: OverviewCycleTime[];
  completedTotal: number;
  completedAlreadyWhenFirstSeen: number;
  completedByOwner: OverviewPersonCount[];
  completedByTechnician: OverviewPersonCount[];
  invoicesSent: {
    count: number;
    totals: OverviewMoney[];
    items: OverviewItem[];
    truncated: boolean;
    paidNotSeenSent: number;
  };
};

const count = (v: unknown) => typeof v === "number" && Number.isSafeInteger(v) && v >= 0;
const days = (v: unknown) =>
  v === undefined || v === null || (typeof v === "number" && Number.isFinite(v) && v >= 0);
const date = (v: unknown) => typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v);
const optionalText = (v: unknown) => v === undefined || typeof v === "string";
const list = (v: unknown, each: (row: Record<string, unknown>) => boolean) =>
  Array.isArray(v) && v.every((row: unknown) => isRecord(row) && each(row));
const money = (v: unknown) =>
  list(
    v,
    (m) =>
      optionalText(m.currency) &&
      typeof m.amount === "string" &&
      /^[+-]?\d+(?:\.\d+)?$/.test(m.amount),
  );
const items = (v: unknown) =>
  list(
    v,
    (item) =>
      isWorkItem(item.summary) &&
      optionalText(item.technician) &&
      optionalText(item.invoiceNumber) &&
      optionalText(item.invoiceTotal) &&
      optionalText(item.invoiceSentSeenAt),
  );
const people = (v: unknown) => list(v, (p) => typeof p.name === "string" && count(p.count));

export function isOverview(v: unknown): v is Overview {
  if (!isRecord(v) || !isRecord(v.invoicesSent)) return false;
  const sent = v.invoicesSent;
  return (
    typeof v.generatedAt === "string" &&
    date(v.from) &&
    date(v.to) &&
    list(v.stages, (s) => typeof s.stage === "string" && count(s.count) && money(s.totals)) &&
    list(v.closed, (c) => typeof c.reason === "string" && count(c.count)) &&
    [v.openItems, v.uninvoiced, v.unsent, sent.items].every(items) &&
    [v.openTruncated, v.uninvoicedTruncated, v.unsentTruncated, sent.truncated].every(
      (flag) => typeof flag === "boolean",
    ) &&
    list(
      v.cycleTimes,
      (c) =>
        typeof c.step === "string" &&
        days(c.medianDays) &&
        days(c.averageDays) &&
        [
          c.sampleSize,
          c.excludedBackfilled,
          c.excludedNoStart,
          c.excludedAlreadyComplete,
          c.excludedOutOfOrder,
        ].every(count),
    ) &&
    count(v.completedTotal) &&
    count(v.completedAlreadyWhenFirstSeen) &&
    people(v.completedByOwner) &&
    people(v.completedByTechnician) &&
    count(sent.count) &&
    count(sent.paidNotSeenSent) &&
    money(sent.totals)
  );
}
