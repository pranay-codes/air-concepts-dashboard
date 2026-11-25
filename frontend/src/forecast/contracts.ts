import type { components } from "../api/generated/schema";
import { isRecord } from "../api/client";
import { isUuid } from "../api/contracts";
type S = components["schemas"];
type Present<T> = { [K in keyof T]-?: Exclude<T[K], undefined> };
type Nullable<T, K extends keyof T> = Omit<T, K> & { [P in K]: T[P] | null };
export type ForecastMonth = Nullable<
  Present<S["MonthRow"]>,
  "opening" | "closing" | "receipts" | "payments" | "net" | "generatedAt"
>;
/** GENERATED = frozen lines; PREVIEW = the one month that can be generated now; NOT_GENERATED = no figures. */
export type ForecastMonthState = "GENERATED" | "PREVIEW" | "NOT_GENERATED";
/** MANUAL = entered for a month with no generated month before it; OVERRIDE = entered over a carried closing; CARRIED_FORWARD = previous month's closing. */
export type OpeningSource = "MANUAL" | "OVERRIDE" | "CARRIED_FORWARD";
const openingSources = new Set<string>(["MANUAL", "OVERRIDE", "CARRIED_FORWARD"]);
export type ForecastView = Omit<
  Nullable<Present<S["ForecastView"]>, "lastSuccessfulSyncAt" | "lastRun" | "openingBalanceSource">,
  "months"
> & { months: ForecastMonth[] };
export type Contribution = Nullable<
  Present<S["Contribution"]>,
  "number" | "splitSequence" | "splitCount"
>;
/** Internal note for one forecast transaction. A null note means none is saved. Never sent to Xero. */
export type TransactionNote = Nullable<Present<S["TransactionNote"]>, "note" | "updatedAt">;
export type MonthItems = Omit<S["MonthItems"], "items" | "notes"> & {
  items: Contribution[];
  notes: TransactionNote[];
};
export type Generation = S["GenerationResult"];
export type Adjustment = Nullable<
  Present<S["AdjustmentState"]>,
  | "expectedDateOverride"
  | "exclusionReason"
  | "dateConflictSince"
  | "previousXeroDate"
  | "newXeroDate"
>;
export type StoredDocument = Omit<
  Nullable<Present<S["StoredDocument"]>, "number" | "dueDate" | "xeroExpectedDate">,
  "adjustment"
> & { adjustment: Adjustment };
export type DocumentPage = Omit<Present<S["DocumentPage"]>, "items"> & { items: StoredDocument[] };
export type ManualItem = Nullable<Present<S["ManualItem"]>, "dayOfMonth" | "endMonth">;
export type StaffingLine = Nullable<Present<S["StaffingLine"]>, "payDayOfMonth" | "endMonth">;
export type EntitySummary = Omit<Present<S["EntitySummary"]>, "months"> & {
  months: ForecastMonth[];
};
export type Combined = Omit<Present<S["CombinedView"]>, "months" | "entities"> & {
  months: ForecastMonth[];
  entities: EntitySummary[];
};
export type SyncRun = Nullable<
  Present<S["SyncRun"]>,
  | "finishedAt"
  | "errorCode"
  | "upstreamStatus"
  | "dayLimitRemaining"
  | "minuteLimitRemaining"
  | "appMinuteLimitRemaining"
>;
export type AuditPage = Present<S["AuditPage"]>;
export type ManualInput = S["ManualRequest"];
export type StaffingInput = S["StaffingRequest"];
const text = (v: unknown): v is string => typeof v === "string" && v.length <= 2000;
export const money = (v: unknown): v is string =>
  typeof v === "string" && /^-?\d{1,32}\.\d{2}$/.test(v);
const nullable = (v: unknown, check: (v: unknown) => boolean) => v === null || check(v);
const month = (v: unknown): v is string =>
  typeof v === "string" && /^\d{4}-(0[1-9]|1[0-2])$/.test(v);
const date = (v: unknown) =>
  typeof v === "string" &&
  /^\d{4}-\d{2}-\d{2}$/.test(v) &&
  Number.isFinite(Date.parse(`${v}T00:00:00Z`)) &&
  new Date(`${v}T00:00:00Z`).toISOString().slice(0, 10) === v;
const count = (v: unknown) => typeof v === "number" && Number.isSafeInteger(v) && v >= 0;
const monthState = (v: unknown): v is ForecastMonthState =>
  v === "GENERATED" || v === "PREVIEW" || v === "NOT_GENERATED";
const array = <T>(v: unknown, check: (v: unknown) => v is T): v is T[] =>
  Array.isArray(v) && v.every(check);
export function isMonth(v: unknown): v is ForecastMonth {
  return (
    isRecord(v) &&
    month(v.month) &&
    nullable(v.opening, money) &&
    nullable(v.closing, money) &&
    nullable(v.receipts, money) &&
    nullable(v.payments, money) &&
    nullable(v.net, money) &&
    count(v.receiptCount) &&
    count(v.paymentCount) &&
    typeof v.containsOverdue === "boolean" &&
    monthState(v.state) &&
    typeof v.generatable === "boolean" &&
    nullable(v.generatedAt, text) &&
    count(v.pendingNewItems) &&
    (v.openingSource === null ||
      v.openingSource === undefined ||
      (typeof v.openingSource === "string" && openingSources.has(v.openingSource)))
  );
}
export function isForecast(v: unknown): v is ForecastView {
  return (
    isRecord(v) &&
    isUuid(v.entityId) &&
    text(v.entityName) &&
    month(v.financialYearStart) &&
    text(v.financialYearLabel) &&
    array(v.months, isMonth) &&
    v.months.length === 12 &&
    isRecord(v.totals) &&
    nullable(v.totals.receipts, money) &&
    nullable(v.totals.payments, money) &&
    nullable(v.totals.closing, money) &&
    isRecord(v.beyondWindow) &&
    money(v.beyondWindow.receipts) &&
    money(v.beyondWindow.payments) &&
    Array.isArray(v.exceptions) &&
    v.exceptions.every(
      (e) => isRecord(e) && text(e.kind) && text(e.message) && nullable(e.documentId, isUuid),
    ) &&
    Array.isArray(v.exclusions) &&
    v.exclusions.every((e) => isRecord(e) && isUuid(e.documentId) && text(e.reason)) &&
    nullable(v.lastSuccessfulSyncAt, text) &&
    typeof v.syncStale === "boolean" &&
    nullable(v.openingBalanceSource, text) &&
    (v.lastRun === null || isRecord(v.lastRun))
  );
}
export function isContribution(v: unknown): v is Contribution {
  return (
    isRecord(v) &&
    isUuid(v.sourceId) &&
    ["DOCUMENT", "MANUAL", "STAFFING"].includes(String(v.source)) &&
    ["IN", "OUT"].includes(String(v.direction)) &&
    text(v.category) &&
    nullable(v.number, text) &&
    text(v.label) &&
    date(v.date) &&
    month(v.bucket) &&
    money(v.amount) &&
    [
      "SPLIT",
      "OVERRIDE",
      "XERO_EXPECTED_DATE",
      "DUE_DATE",
      "ISSUE_DATE",
      "MANUAL",
      "STAFFING",
    ].includes(String(v.rule)) &&
    typeof v.overdue === "boolean" &&
    nullable(v.splitSequence, count) &&
    nullable(v.splitCount, count)
  );
}
/** Notes may run to 2000 code points, which can be up to 4000 UTF-16 units. */
export function isTransactionNote(v: unknown): v is TransactionNote {
  return (
    isRecord(v) &&
    ["DOCUMENT", "MANUAL", "STAFFING"].includes(String(v.source)) &&
    isUuid(v.sourceId) &&
    nullable(v.note, (n) => typeof n === "string" && n.length >= 1 && n.length <= 4000) &&
    nullable(v.updatedAt, (d) => typeof d === "string" && !Number.isNaN(Date.parse(d)))
  );
}
export function isMonthItems(v: unknown): v is MonthItems {
  return (
    isRecord(v) &&
    month(v.month) &&
    monthState(v.state) &&
    typeof v.generatable === "boolean" &&
    array(v.items, isContribution) &&
    array(v.notes, isTransactionNote)
  );
}
export function isGeneration(v: unknown): v is Generation {
  return (
    isRecord(v) &&
    month(v.month) &&
    monthState(v.state) &&
    typeof v.generatable === "boolean" &&
    count(v.generationCount) &&
    count(v.addedLines) &&
    money(v.addedReceipts) &&
    money(v.addedPayments)
  );
}
export function isAdjustment(v: unknown): v is Adjustment {
  return (
    isRecord(v) &&
    isUuid(v.documentId) &&
    typeof v.excluded === "boolean" &&
    nullable(v.expectedDateOverride, date) &&
    nullable(v.exclusionReason, text) &&
    nullable(v.dateConflictSince, text) &&
    nullable(v.previousXeroDate, date) &&
    nullable(v.newXeroDate, date) &&
    Array.isArray(v.lines) &&
    v.lines.every(
      (l) => isRecord(l) && count(l.sequence) && date(l.expectedDate) && money(l.amount),
    )
  );
}
export function isDocument(v: unknown): v is StoredDocument {
  return (
    isRecord(v) &&
    isUuid(v.id) &&
    ["ACCREC", "ACCPAY"].includes(String(v.type)) &&
    nullable(v.number, text) &&
    text(v.contact) &&
    text(v.status) &&
    typeof v.outstanding === "boolean" &&
    nullable(v.dueDate, date) &&
    nullable(v.xeroExpectedDate, date) &&
    money(v.amountDue) &&
    isAdjustment(v.adjustment) &&
    v.adjustment.documentId === v.id
  );
}
export function isDocumentPage(v: unknown): v is DocumentPage {
  return (
    isRecord(v) &&
    array(v.items, isDocument) &&
    count(v.page) &&
    count(v.pageSize) &&
    count(v.itemCount)
  );
}
export function isManual(v: unknown): v is ManualItem {
  return (
    isRecord(v) &&
    isUuid(v.id) &&
    ["IN", "OUT"].includes(String(v.direction)) &&
    text(v.category) &&
    text(v.description) &&
    money(v.amount) &&
    ["ONE_OFF", "MONTHLY"].includes(String(v.recurrence)) &&
    nullable(v.dayOfMonth, count) &&
    month(v.startMonth) &&
    nullable(v.endMonth, month) &&
    typeof v.active === "boolean"
  );
}
export function isStaff(v: unknown): v is StaffingLine {
  return (
    isRecord(v) &&
    isUuid(v.id) &&
    text(v.label) &&
    money(v.monthlyCost) &&
    nullable(v.payDayOfMonth, count) &&
    month(v.startMonth) &&
    nullable(v.endMonth, month) &&
    nullable(v.hourlyRate, money) &&
    nullable(v.weeklyHours, money) &&
    (v.paymentFrequency === null ||
      ["WEEKLY", "FORTNIGHTLY", "MONTHLY"].includes(String(v.paymentFrequency))) &&
    nullable(v.firstPayday, date) &&
    nullable(v.employmentStart, date) &&
    nullable(v.employmentEnd, date) &&
    typeof v.active === "boolean"
  );
}
export function isCombined(v: unknown): v is Combined {
  return (
    isRecord(v) &&
    month(v.financialYearStart) &&
    text(v.financialYearLabel) &&
    array(v.months, isMonth) &&
    v.months.length === 12 &&
    typeof v.complete === "boolean" &&
    Array.isArray(v.entities) &&
    v.entities.every(
      (e) =>
        isRecord(e) &&
        isUuid(e.entityId) &&
        text(e.entityName) &&
        array(e.months, isMonth) &&
        Array.isArray(e.exceptions) &&
        e.exceptions.every(text) &&
        typeof e.complete === "boolean",
    )
  );
}
export function isSync(v: unknown): v is SyncRun {
  return (
    isRecord(v) &&
    isUuid(v.id) &&
    text(v.startedAt) &&
    nullable(v.finishedAt, text) &&
    ["MANUAL", "SCHEDULED"].includes(String(v.trigger)) &&
    ["RUNNING", "SUCCEEDED", "FAILED"].includes(String(v.status)) &&
    count(v.documentsSeen) &&
    count(v.documentsInserted) &&
    count(v.documentsUpdated) &&
    count(v.documentsSettled) &&
    count(v.conflictsFlagged) &&
    nullable(v.errorCode, text)
  );
}
export function isAudit(v: unknown): v is AuditPage {
  return (
    isRecord(v) &&
    count(v.page) &&
    count(v.pageSize) &&
    count(v.itemCount) &&
    Array.isArray(v.items) &&
    v.items.every(
      (r) =>
        isRecord(r) &&
        count(r.id) &&
        text(r.at) &&
        text(r.recordTable) &&
        text(r.recordKey) &&
        text(r.action) &&
        isRecord(r.before) &&
        isRecord(r.after),
    )
  );
}
export const isManualList = (v: unknown): v is ManualItem[] => array(v, isManual);
export const isStaffList = (v: unknown): v is StaffingLine[] => array(v, isStaff);
export const isSyncList = (v: unknown): v is SyncRun[] => array(v, isSync);

export function cents(value: string): bigint | null {
  if (!/^-?\d{1,16}(?:\.\d{0,2})?$/.test(value)) return null;
  const negative = value.startsWith("-");
  const [whole = "0", fraction = ""] = value.replace("-", "").split(".");
  return (BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"))) * (negative ? -1n : 1n);
}
export function decimal(value: string): string | null {
  const n = cents(value);
  if (n === null) return null;
  const abs = n < 0n ? -n : n;
  return `${n < 0n ? "-" : ""}${abs / 100n}.${(abs % 100n).toString().padStart(2, "0")}`;
}
