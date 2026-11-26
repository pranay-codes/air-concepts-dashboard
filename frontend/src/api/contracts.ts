import type { components, operations } from "./generated/schema";
import { isRecord } from "./client";

type Schemas = components["schemas"];
export type Profile = Schemas["MeResponse"];
export type Role = Profile["role"];
export type Entity = Schemas["Entity"];
export type Invoice = Schemas["InvoiceSummary"];
export type InvoiceType = Invoice["type"];
export type InvoiceQuery = NonNullable<operations["listInvoices"]["parameters"]["query"]>;
export type InvoicePage = Schemas["InvoicePage"];
export type InvoiceDetail = Schemas["InvoiceDetail"];
export type Owner = Schemas["InvoiceOwner"];
export type Assignment = Schemas["SalesAssignment"];
export type Attachment = Schemas["InvoiceAttachment"];
export type Connection = Schemas["XeroConnection"];
export type Authorization = Schemas["XeroAuthorizationStart"];
export type Attempt = Schemas["XeroAuthorizationAttempt"];
export type Staff = Schemas["StaffMember"];
export type StaffOption = Schemas["StaffOption"];
export type CreateStaff = Schemas["CreateStaff"];
export type UpdateStaff = Schemas["UpdateStaff"];
export type GreenLineSettings = Schemas["GreenLineSettings"];
export type GreenLineSettingsRequest = Schemas["SettingsRequest"];
export type VworkConnection = Schemas["VworkConnection"];
export type EntityVworkTemplate = Schemas["EntityVworkTemplate"];
export type EntityVworkTemplatesRequest = Schemas["EntityVworkTemplatesRequest"];
export type GreenLineSyncStatus = Schemas["GreenLineSyncStatus"];

const roles = new Set(["DIRECTOR", "FINANCIAL_ADMIN", "STANDARD_USER"]);
const statuses = new Set(["DRAFT", "SUBMITTED", "AUTHORISED", "PAID", "VOIDED", "DELETED"]);
const connectionStates = new Set(["CONNECTED", "NOT_CONNECTED", "NEEDS_RECONNECTION"]);
const quoteAccessStates = new Set(["NOT_ENABLED", "UNKNOWN", "READY", "RECONNECT_REQUIRED"]);
const attemptStates = new Set(["AWAITING_CALLBACK", "PROCESSING", "READY", "FAILED", "COMPLETED"]);
export const isUuid = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value);
const text = (value: unknown): value is string => typeof value === "string";
const name = (value: unknown): value is string =>
  text(value) && value.trim().length > 0 && value.length <= 500;
const optionalText = (value: unknown) => value === undefined || text(value);
const integer = (value: unknown): value is number =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
const money = (value: unknown) =>
  value === undefined ||
  (text(value) && value.length <= 256 && /^[+-]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(value));
const instant = (value: unknown) =>
  text(value) && value.endsWith("Z") && Number.isFinite(Date.parse(value));
function isGreenLineSyncRun(value: unknown): value is Schemas["GreenLineSyncRun"] {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    text(value.status) &&
    ["RUNNING", "SUCCEEDED", "FAILED"].includes(value.status) &&
    instant(value.startedAt) &&
    (value.finishedAt === undefined || instant(value.finishedAt)) &&
    optionalText(value.errorCode)
  );
}
function isGreenLineSystemSyncStatus(
  value: unknown,
): value is Schemas["GreenLineSystemSyncStatus"] {
  return (
    isRecord(value) &&
    (value.latest === undefined || isGreenLineSyncRun(value.latest)) &&
    (value.lastSuccess === undefined ||
      (isGreenLineSyncRun(value.lastSuccess) && value.lastSuccess.status === "SUCCEEDED")) &&
    (value.lastFailure === undefined ||
      (isGreenLineSyncRun(value.lastFailure) && value.lastFailure.status === "FAILED"))
  );
}
export function isGreenLineSyncStatus(value: unknown): value is GreenLineSyncStatus {
  return (
    isRecord(value) &&
    isUuid(value.entityId) &&
    isGreenLineSystemSyncStatus(value.xero) &&
    isGreenLineSystemSyncStatus(value.vwork)
  );
}
function date(value: unknown): value is string {
  if (!text(value) || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}
export function arrayOf<T>(
  check: (value: unknown) => value is T,
): (value: unknown) => value is T[] {
  return (value: unknown): value is T[] => Array.isArray(value) && value.every(check);
}
export function isProfile(value: unknown): value is Profile {
  return (
    isRecord(value) &&
    isUuid(value.subject) &&
    name(value.displayName) &&
    text(value.role) &&
    roles.has(value.role)
  );
}
export function isEntity(value: unknown): value is Entity {
  return isRecord(value) && isUuid(value.id) && name(value.displayName);
}
export function isEntities(value: unknown): value is Entity[] {
  return (
    arrayOf(isEntity)(value) && new Set(value.map((entity) => entity.id)).size === value.length
  );
}
export function isOwner(value: unknown): value is Owner {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    name(value.displayName) &&
    typeof value.active === "boolean"
  );
}
export function isInvoice(value: unknown): value is Invoice {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    (value.type === "ACCREC" || value.type === "ACCPAY") &&
    optionalText(value.number) &&
    optionalText(value.reference) &&
    text(value.contact) &&
    date(value.date) &&
    (value.dueDate === undefined || date(value.dueDate)) &&
    money(value.total) &&
    money(value.amountDue) &&
    (value.currency === undefined || (text(value.currency) && /^[A-Z]{3}$/.test(value.currency))) &&
    text(value.status) &&
    statuses.has(value.status) &&
    typeof value.overdue === "boolean" &&
    (value.owner === undefined || (value.type === "ACCREC" && isOwner(value.owner)))
  );
}
export function isPage(value: unknown): value is InvoicePage {
  if (
    !isRecord(value) ||
    !arrayOf(isInvoice)(value.items) ||
    !integer(value.page) ||
    value.page < 1 ||
    !integer(value.pageSize) ||
    value.pageSize < 1 ||
    value.pageSize > 100 ||
    !integer(value.itemCount) ||
    !integer(value.pageCount)
  )
    return false;
  const pages = Math.ceil(value.itemCount / value.pageSize);
  return (
    (value.pageCount === pages || (pages === 0 && value.pageCount === 1)) &&
    value.items.length ===
      Math.max(0, Math.min(value.pageSize, value.itemCount - (value.page - 1) * value.pageSize)) &&
    new Set(value.items.map((invoice) => invoice.id)).size === value.items.length
  );
}
function isLine(value: unknown): value is Schemas["InvoiceLine"] {
  return (
    isRecord(value) &&
    optionalText(value.description) &&
    money(value.quantity) &&
    money(value.unitAmount) &&
    money(value.lineAmount) &&
    money(value.taxAmount)
  );
}
function isPayment(value: unknown): value is Schemas["InvoicePayment"] {
  return (
    isRecord(value) &&
    (value.id === undefined || isUuid(value.id)) &&
    (value.date === undefined || date(value.date)) &&
    money(value.amount) &&
    optionalText(value.reference)
  );
}
export function isDetail(value: unknown): value is InvoiceDetail {
  return (
    isRecord(value) &&
    isInvoice(value.invoice) &&
    money(value.subtotal) &&
    money(value.tax) &&
    money(value.amountPaid) &&
    money(value.amountCredited) &&
    arrayOf(isLine)(value.lines) &&
    arrayOf(isPayment)(value.payments) &&
    (value.taxBasis === undefined ||
      value.taxBasis === "EXCLUSIVE" ||
      value.taxBasis === "INCLUSIVE" ||
      value.taxBasis === "NO_TAX")
  );
}
export function isAssignment(value: unknown): value is Assignment {
  return (
    isRecord(value) &&
    isUuid(value.invoiceId) &&
    (value.owner === undefined || isOwner(value.owner))
  );
}
export function isAttachment(value: unknown): value is Attachment {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    name(value.fileName) &&
    text(value.contentType) &&
    integer(value.size) &&
    typeof value.previewable === "boolean"
  );
}
export function isConnection(value: unknown): value is Connection {
  return (
    isRecord(value) &&
    isUuid(value.entityId) &&
    text(value.status) &&
    connectionStates.has(value.status) &&
    (value.quoteAccess === undefined ||
      (text(value.quoteAccess) && quoteAccessStates.has(value.quoteAccess))) &&
    optionalText(value.organisationName) &&
    optionalText(value.maskedTenantId) &&
    optionalText(value.connectedBy) &&
    optionalText(value.errorCode) &&
    (value.lastConfirmedAt === undefined || instant(value.lastConfirmedAt)) &&
    (value.lastCheckedAt === undefined || instant(value.lastCheckedAt))
  );
}
export function isAuthorization(value: unknown): value is Authorization {
  if (
    !isRecord(value) ||
    !isUuid(value.attemptId) ||
    !text(value.authorizationUrl) ||
    !instant(value.expiresAt)
  )
    return false;
  try {
    const url = new URL(value.authorizationUrl);
    const scopes = new Set(url.searchParams.get("scope")?.split(" "));
    return (
      url.origin === "https://login.xero.com" &&
      url.pathname === "/identity/connect/authorize" &&
      !url.username &&
      !url.password &&
      !url.hash &&
      url.searchParams.get("response_type") === "code" &&
      /^[a-zA-Z0-9_-]{43}$/.test(url.searchParams.get("state") ?? "") &&
      [...scopes].every((scope) =>
        [
          "offline_access",
          "accounting.invoices.read",
          "accounting.attachments.read",
          "accounting.contacts.read",
          "accounting.invoices",
          "accounting.attachments",
        ].includes(scope),
      ) &&
      ["offline_access", "accounting.invoices.read", "accounting.attachments.read"].every((scope) =>
        scopes.has(scope),
      )
    );
  } catch {
    return false;
  }
}
function isChoice(value: unknown): value is Schemas["XeroTenantChoice"] {
  return isRecord(value) && isUuid(value.connectionId) && name(value.name);
}
export function isAttempt(value: unknown): value is Attempt {
  return (
    isRecord(value) &&
    isUuid(value.id) &&
    isUuid(value.entityId) &&
    text(value.state) &&
    attemptStates.has(value.state) &&
    optionalText(value.errorCode) &&
    (value.upstreamStatus === undefined ||
      (integer(value.upstreamStatus) && value.upstreamStatus <= 599)) &&
    arrayOf(isChoice)(value.choices)
  );
}
export function isStaffOption(value: unknown): value is StaffOption {
  return isRecord(value) && isUuid(value.id) && name(value.displayName);
}
export function isDirectorGreenLineSettings(value: unknown): value is GreenLineSettings {
  return (
    isRecord(value) &&
    isUuid(value.entityId) &&
    typeof value.enabled === "boolean" &&
    (value.defaultOwnerId === undefined || isUuid(value.defaultOwnerId)) &&
    (value.serviceManagerId === undefined || isUuid(value.serviceManagerId)) &&
    integer(value.lateThresholdWorkingDays) &&
    value.lateThresholdWorkingDays >= 1 &&
    value.lateThresholdWorkingDays <= 30 &&
    integer(value.backfillDays) &&
    value.backfillDays <= 365 &&
    (value.automaticSyncEnabled === undefined || typeof value.automaticSyncEnabled === "boolean") &&
    (value.automaticSyncAvailable === undefined ||
      typeof value.automaticSyncAvailable === "boolean") &&
    (value.automaticSyncIntervalMinutes === undefined ||
      (integer(value.automaticSyncIntervalMinutes) &&
        value.automaticSyncIntervalMinutes >= 1 &&
        value.automaticSyncIntervalMinutes <= 1440))
  );
}
export function isVworkConnection(value: unknown): value is VworkConnection {
  return (
    isRecord(value) &&
    Object.keys(value).every((key) =>
      ["status", "configuredBy", "configuredAt", "verifiedAt", "errorCode"].includes(key),
    ) &&
    typeof value.status === "string" &&
    ["ACTIVE", "UNVERIFIED", "INVALID", "DISCONNECTED"].includes(value.status) &&
    (value.configuredBy === undefined || isUuid(value.configuredBy)) &&
    (value.configuredAt === undefined || instant(value.configuredAt)) &&
    (value.verifiedAt === undefined || instant(value.verifiedAt)) &&
    optionalText(value.errorCode)
  );
}
export function isVworkTemplateRef(value: unknown): value is string {
  return (
    typeof value === "string" &&
    value.length > 0 &&
    value.length <= 120 &&
    value === value.trim() &&
    !Array.from(value).some((char) => {
      const code = char.codePointAt(0)!;
      return code < 32 || (code >= 127 && code <= 159);
    })
  );
}
export function isEntityVworkTemplates(value: unknown): value is EntityVworkTemplate[] {
  if (!Array.isArray(value) || value.length > 20) return false;
  const seen = new Set<string>();
  let defaults = 0;
  for (const item of value) {
    if (
      !isRecord(item) ||
      !isVworkTemplateRef(item.templateRef) ||
      seen.has(item.templateRef) ||
      typeof item.isDefault !== "boolean"
    )
      return false;
    seen.add(item.templateRef);
    if (item.isDefault) defaults++;
  }
  return value.length === 0 || defaults === 1;
}
export function isStaff(value: unknown): value is Staff {
  return (
    isRecord(value) &&
    text(value.email) &&
    text(value.role) &&
    roles.has(value.role) &&
    typeof value.active === "boolean" &&
    isStaffOption(value)
  );
}
export function defaultQuery(type: InvoiceType): InvoiceQuery {
  return {
    type,
    search: "",
    overdue: false,
    dateField: "INVOICE_DATE",
    sort: "DATE",
    direction: "DESC",
    page: 1,
    pageSize: 25,
  };
}
