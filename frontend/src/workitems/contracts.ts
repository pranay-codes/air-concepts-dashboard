import { isRecord } from "../api/client";
import { isUuid } from "../api/contracts";
import type { components, operations } from "../api/generated/schema";

type Summary = components["schemas"]["Summary"];
export type CreateJobDraft = components["schemas"]["CreateJobDraft"];
export type CreateJobRequest = components["schemas"]["CreateJobRequest"];
export type CreateJobResult = components["schemas"]["CreateJobResult"];
export type InvoiceDraftReview = components["schemas"]["InvoiceDraftReview"];
export type OperationView = components["schemas"]["OperationView"] &
  Required<
    Pick<
      components["schemas"]["OperationView"],
      "id" | "kind" | "status" | "requestedAt" | "allowLookup"
    >
  > & { allowRetry: boolean };
export type LookupResult = components["schemas"]["LookupResult"] & {
  outcome: "LINKED_EXISTING" | "NOT_FOUND";
};
export type WorkItem = Summary &
  Required<
    Pick<
      Summary,
      | "id"
      | "entityId"
      | "entityName"
      | "xeroQuoteId"
      | "quoteNumber"
      | "xeroQuoteStatus"
      | "stage"
      | "stageEnteredAt"
      | "daysInStage"
      | "late"
      | "flags"
      | "attention"
      | "version"
    >
  >;
export type WorkItemQuery = NonNullable<operations["listWorkItems"]["parameters"]["query"]>;
export type WorkItemPage = {
  items: WorkItem[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};
export type MyWork = {
  needsAction: WorkItem[];
  waiting: WorkItem[];
  needsAttention: WorkItem[];
  generatedAt: string;
  truncated: boolean;
};
export type WorkItemDetail = Omit<
  components["schemas"]["Detail"],
  "summary" | "events" | "operations" | "allowedActions"
> & {
  summary: WorkItem;
  events: components["schemas"]["EventView"][];
  operations: OperationView[];
  allowedActions: NonNullable<components["schemas"]["Detail"]["allowedActions"]>;
};
const stages = new Set([
  "QUOTED",
  "TO_SCHEDULE",
  "SCHEDULED",
  "TO_INVOICE",
  "INVOICE_DRAFTED",
  "INVOICED",
  "CLOSED",
]);
const quotes = new Set(["DRAFT", "SENT", "ACCEPTED", "DECLINED", "DELETED", "INVOICED"]);
const int = (v: unknown): v is number => typeof v === "number" && Number.isSafeInteger(v) && v >= 0;
const optionalText = (v: unknown) => v === undefined || typeof v === "string";
const strings = (v: unknown): v is string[] =>
  Array.isArray(v) && v.every((x) => typeof x === "string");
const nullableText = (v: unknown) => v === null || v === undefined || typeof v === "string";
export function isInvoiceDraftReview(v: unknown): v is InvoiceDraftReview {
  return (
    isRecord(v) &&
    isUuid(v.entityId) &&
    isUuid(v.itemId) &&
    int(v.version) &&
    typeof v.quoteNumber === "string" &&
    v.quoteNumber.length > 0 &&
    typeof v.organisationName === "string" &&
    v.organisationName.length > 0 &&
    typeof v.customer === "string" &&
    v.customer.length > 0 &&
    typeof v.reference === "string" &&
    v.reference.length > 0 &&
    int(v.lineCount) &&
    v.lineCount > 0 &&
    typeof v.total === "string" &&
    /^[+-]?\d+(?:\.\d+)?$/.test(v.total) &&
    typeof v.currency === "string" &&
    /^[A-Z]{3}$/.test(v.currency) &&
    typeof v.fingerprint === "string" &&
    /^[0-9a-f]{64}$/.test(v.fingerprint)
  );
}
export function isLookupResult(v: unknown): v is LookupResult {
  return isRecord(v) && (v.outcome === "LINKED_EXISTING" || v.outcome === "NOT_FOUND");
}
function isOperation(v: unknown): v is OperationView {
  return (
    isRecord(v) &&
    isUuid(v.id) &&
    typeof v.kind === "string" &&
    ["VWORK_CREATE_JOB", "XERO_CREATE_INVOICE", "XERO_ATTACH_REPORT"].includes(v.kind) &&
    typeof v.status === "string" &&
    ["PENDING", "UNKNOWN", "FAILED", "SUCCEEDED"].includes(v.status) &&
    (v.outcome === undefined ||
      (typeof v.outcome === "string" &&
        ["CREATED", "LINKED_EXISTING", "NOT_FOUND"].includes(v.outcome))) &&
    typeof v.requestedAt === "string" &&
    v.requestedAt.endsWith("Z") &&
    Number.isFinite(Date.parse(v.requestedAt)) &&
    typeof v.allowLookup === "boolean" &&
    typeof v.allowRetry === "boolean"
  );
}
export function isCreateJobDraft(v: unknown): v is CreateJobDraft {
  return (
    isRecord(v) &&
    isUuid(v.entityId) &&
    isUuid(v.itemId) &&
    int(v.version) &&
    typeof v.quoteNumber === "string" &&
    v.quoteNumber.length > 0 &&
    nullableText(v.reference) &&
    nullableText(v.customer) &&
    nullableText(v.contactPerson) &&
    nullableText(v.phone) &&
    nullableText(v.siteAddress) &&
    nullableText(v.defaultTemplate) &&
    Array.isArray(v.scope) &&
    v.scope.every(
      (line) => isRecord(line) && nullableText(line.description) && nullableText(line.quantity),
    ) &&
    Array.isArray(v.templates) &&
    v.templates.every(
      (t) =>
        isRecord(t) &&
        typeof t.templateRef === "string" &&
        t.templateRef.length > 0 &&
        typeof t.isDefault === "boolean",
    ) &&
    strings(v.missingFields)
  );
}
export function isCreateJobResult(v: unknown): v is CreateJobResult {
  return (
    isRecord(v) &&
    isUuid(v.operationId) &&
    (v.outcome === "CREATED" || v.outcome === "LINKED_EXISTING")
  );
}
export function isWorkItem(v: unknown): v is WorkItem {
  return (
    isRecord(v) &&
    isUuid(v.id) &&
    isUuid(v.entityId) &&
    isUuid(v.xeroQuoteId) &&
    typeof v.entityName === "string" &&
    v.entityName.trim().length > 0 &&
    typeof v.quoteNumber === "string" &&
    v.quoteNumber.length > 0 &&
    typeof v.xeroQuoteStatus === "string" &&
    quotes.has(v.xeroQuoteStatus) &&
    typeof v.stage === "string" &&
    stages.has(v.stage) &&
    typeof v.stageEnteredAt === "string" &&
    v.stageEnteredAt.endsWith("Z") &&
    Number.isFinite(Date.parse(v.stageEnteredAt)) &&
    int(v.daysInStage) &&
    typeof v.late === "boolean" &&
    int(v.version) &&
    strings(v.flags) &&
    strings(v.attention) &&
    optionalText(v.jobId) &&
    optionalText(v.customerName) &&
    optionalText(v.followUpDate) &&
    optionalText(v.nextAction) &&
    (v.currency === undefined ||
      (typeof v.currency === "string" && /^[A-Z]{3}$/.test(v.currency))) &&
    (v.quoteTotal === undefined ||
      (typeof v.quoteTotal === "string" && /^[+-]?\d+(?:\.\d+)?$/.test(v.quoteTotal))) &&
    (v.owner === undefined ||
      (isRecord(v.owner) && isUuid(v.owner.id) && typeof v.owner.displayName === "string"))
  );
}
export function isWorkItemPage(v: unknown): v is WorkItemPage {
  if (
    !isRecord(v) ||
    !Array.isArray(v.items) ||
    !v.items.every(isWorkItem) ||
    !int(v.page) ||
    v.page < 1 ||
    !int(v.pageSize) ||
    v.pageSize < 1 ||
    v.pageSize > 100 ||
    !int(v.total) ||
    !int(v.totalPages)
  )
    return false;
  return (
    v.totalPages === Math.ceil(v.total / v.pageSize) &&
    v.items.length === Math.max(0, Math.min(v.pageSize, v.total - (v.page - 1) * v.pageSize)) &&
    new Set(v.items.map((item: WorkItem) => item.id)).size === v.items.length
  );
}
export function isWorkItemDetail(v: unknown): v is WorkItemDetail {
  const allowed = new Set([
    "EDIT_NOTES",
    "CLOSE_LOST",
    "CREATE_JOB",
    "PREPARE_INVOICE",
    "NO_INVOICE_NEEDED",
    "MARK_SENT",
  ]);
  return (
    isRecord(v) &&
    isWorkItem(v.summary) &&
    optionalText(v.notes) &&
    optionalText(v.closingNote) &&
    isRecord(v.vwork) &&
    isRecord(v.invoice) &&
    isRecord(v.links) &&
    Array.isArray(v.operations) &&
    v.operations.every(isOperation) &&
    strings(v.allowedActions) &&
    v.allowedActions.every((action) => allowed.has(action)) &&
    Array.isArray(v.events) &&
    v.events.every(
      (event: unknown) =>
        isRecord(event) &&
        int(event.id) &&
        typeof event.at === "string" &&
        event.at.endsWith("Z") &&
        Number.isFinite(Date.parse(event.at)) &&
        typeof event.source === "string" &&
        typeof event.eventType === "string" &&
        (event.actor === undefined ||
          (isRecord(event.actor) &&
            isUuid(event.actor.id) &&
            typeof event.actor.displayName === "string")),
    )
  );
}
export function isMyWork(v: unknown): v is MyWork {
  if (
    !isRecord(v) ||
    !Array.isArray(v.needsAction) ||
    !Array.isArray(v.waiting) ||
    !Array.isArray(v.needsAttention) ||
    !v.needsAction.every(isWorkItem) ||
    !v.waiting.every(isWorkItem) ||
    !v.needsAttention.every(isWorkItem) ||
    typeof v.generatedAt !== "string" ||
    !v.generatedAt.endsWith("Z") ||
    !Number.isFinite(Date.parse(v.generatedAt)) ||
    typeof v.truncated !== "boolean"
  )
    return false;
  const rows: WorkItem[] = [...v.needsAction, ...v.waiting, ...v.needsAttention];
  return rows.length <= 500 && new Set(rows.map((row) => row.id)).size === rows.length;
}
