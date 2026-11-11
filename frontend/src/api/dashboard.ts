import { ApiClient, ApiError, isRecord } from "./client";
import * as c from "./contracts";
import * as f from "../forecast/contracts";

function id(value: string): string {
  if (!c.isUuid(value)) throw new ApiError(400, "INVALID_REQUEST");
  return encodeURIComponent(value);
}
const root = (entity: string) => `/api/v1/entities/${id(entity)}`;
const document = (entity: string, invoice: string) => `${root(entity)}/invoices/${id(invoice)}`;
function isOpening(value: unknown): value is { openingBalance: string; source?: string } {
  return isRecord(value) && f.money(value.openingBalance);
}
/** The calendar year of a YYYY-MM month string. */
export function yearOf(month: string): number {
  return Number(month.slice(0, 4));
}

function matchesPeriod(rows: f.ForecastMonth[], start: string): boolean {
  const [year = "0", month = "1"] = start.split("-");
  return (
    rows.length === 12 &&
    rows.every(
      (row, index) =>
        row.month ===
        new Date(Date.UTC(Number(year), Number(month) - 1 + index, 1)).toISOString().slice(0, 7),
    )
  );
}

/** Typed feature interface. All tenant selection and financial calculations remain on the backend. */
export class DashboardApi {
  constructor(private readonly client: ApiClient) {}
  /** `financialYear` is the calendar year the financial year starts in; null asks for the server's current year. */
  async forecast(entity: string, financialYear: number | null, signal?: AbortSignal) {
    const query = financialYear === null ? "" : `?financialYearStart=${financialYear}`;
    const result = await this.client.json(`${root(entity)}/forecast${query}`, f.isForecast, signal);
    if (
      result.entityId !== entity ||
      (financialYear !== null && yearOf(result.financialYearStart) !== financialYear) ||
      !matchesPeriod(result.months, result.financialYearStart)
    )
      throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  async forecastItems(entity: string, month: string, signal?: AbortSignal) {
    const result = await this.client.json(
      `${root(entity)}/forecast/months/${encodeURIComponent(month)}/items`,
      f.isMonthItems,
      signal,
    );
    if (result.month !== month || result.items.some((item) => item.bucket !== month))
      throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  /** Freezes a month's planned lines. The server appends only lines not already in a forecast. */
  async generateForecastMonth(entity: string, month: string, signal?: AbortSignal) {
    const result = await this.client.json(
      `${root(entity)}/forecast/months/${encodeURIComponent(month)}/generate`,
      f.isGeneration,
      signal,
      { method: "POST" },
    );
    if (result.month !== month) throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  async forecastDocument(entity: string, documentId: string, signal?: AbortSignal) {
    const result = await this.client.json(
      `${root(entity)}/forecast/documents/${id(documentId)}`,
      f.isDocument,
      signal,
    );
    if (result.id !== documentId) throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  forecastDocuments(
    entity: string,
    page: number,
    type: string,
    includeSettled: boolean,
    signal?: AbortSignal,
  ) {
    return this.client.json(
      `${root(entity)}/forecast/documents?page=${page}&pageSize=25&type=${type}&includeSettled=${includeSettled}`,
      f.isDocumentPage,
      signal,
    );
  }
  forecastAdjustment(
    entity: string,
    documentId: string,
    body: { expectedDateOverride?: string | null; excluded?: boolean; exclusionReason?: string },
    signal?: AbortSignal,
  ) {
    return this.client.json(
      `${root(entity)}/forecast/documents/${id(documentId)}/adjustment`,
      f.isAdjustment,
      signal,
      { method: "PUT", body },
    );
  }
  forecastSplits(
    entity: string,
    documentId: string,
    lines: { expectedDate: string; amount: string }[],
    signal?: AbortSignal,
  ) {
    return this.client.json(
      `${root(entity)}/forecast/documents/${id(documentId)}/splits`,
      f.isAdjustment,
      signal,
      { method: "PUT", body: { lines } },
    );
  }
  forecastConflict(
    entity: string,
    documentId: string,
    resolution: "KEEP_SCHEDULE" | "DISCARD_SCHEDULE",
    signal?: AbortSignal,
  ) {
    return this.client.json(
      `${root(entity)}/forecast/documents/${id(documentId)}/conflict`,
      f.isAdjustment,
      signal,
      { method: "POST", body: { resolution } },
    );
  }
  saveForecastOpening(
    entity: string,
    month: string,
    openingBalance: string,
    source: string,
    signal?: AbortSignal,
  ) {
    return this.client.json(
      `${root(entity)}/forecast/periods/${encodeURIComponent(month)}/opening-balance`,
      (v): v is { openingBalance: string; source?: string } => isOpening(v),
      signal,
      { method: "PUT", body: { openingBalance, source } },
    );
  }
  forecastManualItems(entity: string, inactive: boolean, signal?: AbortSignal) {
    return this.client.json(
      `${root(entity)}/forecast/manual-items?includeInactive=${inactive}`,
      f.isManualList,
      signal,
    );
  }
  saveForecastManual(entity: string, item: f.ManualInput, itemId?: string, signal?: AbortSignal) {
    return this.client.json(
      `${root(entity)}/forecast/manual-items${itemId ? `/${id(itemId)}` : ""}`,
      f.isManual,
      signal,
      { method: itemId ? "PATCH" : "POST", body: item },
    );
  }
  deactivateForecastManual(entity: string, itemId: string, signal?: AbortSignal) {
    return this.client.json(
      `${root(entity)}/forecast/manual-items/${id(itemId)}/deactivate`,
      f.isManual,
      signal,
      { method: "POST" },
    );
  }
  forecastStaffing(entity: string, inactive: boolean, signal?: AbortSignal) {
    return this.client.json(
      `${root(entity)}/forecast/staffing?includeInactive=${inactive}`,
      f.isStaffList,
      signal,
    );
  }
  saveForecastStaffing(
    entity: string,
    item: f.StaffingInput,
    itemId?: string,
    signal?: AbortSignal,
  ) {
    return this.client.json(
      `${root(entity)}/forecast/staffing${itemId ? `/${id(itemId)}` : ""}`,
      f.isStaff,
      signal,
      { method: itemId ? "PATCH" : "POST", body: item },
    );
  }
  deactivateForecastStaffing(entity: string, itemId: string, signal?: AbortSignal) {
    return this.client.json(
      `${root(entity)}/forecast/staffing/${id(itemId)}/deactivate`,
      f.isStaff,
      signal,
      { method: "POST" },
    );
  }
  forecastSyncHistory(entity: string, signal?: AbortSignal) {
    return this.client.json(`${root(entity)}/forecast/sync`, f.isSyncList, signal);
  }
  syncForecast(entity: string, signal?: AbortSignal) {
    return this.client.json(`${root(entity)}/forecast/sync`, f.isSync, signal, {
      method: "POST",
      timeoutMs: 600000,
    });
  }
  forecastAudit(entity: string, page: number, signal?: AbortSignal) {
    return this.client.json(
      `${root(entity)}/forecast/audit?page=${page}&pageSize=25`,
      f.isAudit,
      signal,
    );
  }
  exportForecast(entity: string, financialYear: number, file: File, signal?: AbortSignal) {
    return this.client.forecastExport(
      `${root(entity)}/forecast/export?financialYearStart=${financialYear}`,
      file,
      signal,
    );
  }
  async combinedForecast(entities: string[], financialYear: number | null, signal?: AbortSignal) {
    const year = financialYear === null ? "" : `&financialYearStart=${financialYear}`;
    const result = await this.client.json(
      `/api/v1/forecast/combined?entityIds=${entities.map(id).join(",")}${year}`,
      f.isCombined,
      signal,
    );
    if (
      (financialYear !== null && yearOf(result.financialYearStart) !== financialYear) ||
      !matchesPeriod(result.months, result.financialYearStart) ||
      result.entities.length !== entities.length ||
      new Set(result.entities.map((e) => e.entityId)).size !== entities.length ||
      result.entities.some(
        (e) =>
          !e.entityId ||
          !entities.includes(e.entityId) ||
          !matchesPeriod(e.months, result.financialYearStart),
      )
    )
      throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  me(signal?: AbortSignal) {
    return this.client.json("/api/v1/me", c.isProfile, signal);
  }
  entities(signal?: AbortSignal) {
    return this.client.json("/api/v1/entities", c.isEntities, signal);
  }
  createEntity(displayName: string, signal?: AbortSignal) {
    return this.client.json("/api/v1/entities", c.isEntity, signal, {
      method: "POST",
      body: { displayName },
    });
  }
  async renameEntity(entity: string, displayName: string, signal?: AbortSignal): Promise<c.Entity> {
    const result = await this.client.json(root(entity), c.isEntity, signal, {
      method: "PATCH",
      body: { displayName },
    });
    if (result.id !== entity) throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  archiveEntity(entity: string, signal?: AbortSignal) {
    return this.client.noContent(`${root(entity)}/archive`, signal, { method: "POST" });
  }
  async invoices(
    entity: string,
    query: c.InvoiceQuery,
    signal?: AbortSignal,
  ): Promise<c.InvoicePage> {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query))
      if (value !== undefined && value !== "") params.set(key, String(value));
    const page = await this.client.json(`${root(entity)}/invoices?${params}`, c.isPage, signal);
    if (
      page.page !== (query.page ?? 1) ||
      page.pageSize !== (query.pageSize ?? 25) ||
      page.items.some((row) => row.type !== (query.type ?? "ACCREC"))
    )
      throw new ApiError(502, "INVALID_RESPONSE");
    return page;
  }
  async invoice(
    entity: string,
    type: c.InvoiceType,
    invoice: string,
    signal?: AbortSignal,
  ): Promise<c.InvoiceDetail> {
    const detail = await this.client.json(
      `${document(entity, invoice)}?type=${type}`,
      c.isDetail,
      signal,
    );
    if (detail.invoice.id !== invoice || detail.invoice.type !== type)
      throw new ApiError(502, "INVALID_RESPONSE");
    return detail;
  }
  attachments(entity: string, type: c.InvoiceType, invoice: string, signal?: AbortSignal) {
    return this.client.json(
      `${document(entity, invoice)}/attachments?type=${type}`,
      c.arrayOf(c.isAttachment),
      signal,
    );
  }
  file(
    entity: string,
    type: c.InvoiceType,
    invoice: string,
    attachment: string,
    preview: boolean,
    signal?: AbortSignal,
  ) {
    return this.client.download(
      `${document(entity, invoice)}/attachments/${id(attachment)}/file?type=${type}&preview=${preview}`,
      signal,
    );
  }
  async assign(
    entity: string,
    invoice: string,
    assigneeId: string | null,
    signal?: AbortSignal,
  ): Promise<c.Assignment> {
    if (assigneeId !== null) id(assigneeId);
    const result = await this.client.json(
      `${document(entity, invoice)}/assignment?type=ACCREC`,
      c.isAssignment,
      signal,
      { method: "PUT", body: { assigneeId } },
    );
    if (result.invoiceId !== invoice) throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  async connection(entity: string, signal?: AbortSignal): Promise<c.Connection> {
    const result = await this.client.json(`${root(entity)}/xero`, c.isConnection, signal);
    if (result.entityId !== entity) throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  startConnection(entity: string, signal?: AbortSignal) {
    return this.client.json(`${root(entity)}/xero/authorization`, c.isAuthorization, signal, {
      method: "POST",
    });
  }
  async attempt(attempt: string, signal?: AbortSignal): Promise<c.Attempt> {
    const result = await this.client.json(
      `/api/v1/xero/attempts/${id(attempt)}`,
      c.isAttempt,
      signal,
    );
    if (result.id !== attempt) throw new ApiError(502, "INVALID_RESPONSE");
    return result;
  }
  confirmConnection(attempt: string, connectionId: string, signal?: AbortSignal) {
    id(connectionId);
    return this.client.json(
      `/api/v1/xero/attempts/${id(attempt)}/connection`,
      c.isConnection,
      signal,
      { method: "PUT", body: { connectionId } },
    );
  }
  staff(signal?: AbortSignal) {
    return this.client.json("/api/v1/staff", c.arrayOf(c.isStaffOption), signal);
  }
  users(signal?: AbortSignal) {
    return this.client.json("/api/v1/users", c.arrayOf(c.isStaff), signal);
  }
  createUser(body: c.CreateStaff, signal?: AbortSignal) {
    return this.client.json("/api/v1/users", c.isStaff, signal, { method: "POST", body });
  }
  updateUser(user: string, body: c.UpdateStaff, signal?: AbortSignal) {
    return this.client.json(`/api/v1/users/${id(user)}`, c.isStaff, signal, {
      method: "PATCH",
      body,
    });
  }
}
