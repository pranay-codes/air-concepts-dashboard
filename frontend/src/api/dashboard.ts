import { ApiClient, ApiError } from "./client";
import * as c from "./contracts";

function id(value: string): string {
  if (!c.isUuid(value)) throw new ApiError(400, "INVALID_REQUEST");
  return encodeURIComponent(value);
}
const root = (entity: string) => `/api/v1/entities/${id(entity)}`;
const document = (entity: string, invoice: string) => `${root(entity)}/invoices/${id(invoice)}`;

/** Typed feature interface. All tenant selection and financial calculations remain on the backend. */
export class DashboardApi {
  constructor(private readonly client: ApiClient) {}
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
