import { useCallback, useEffect, useRef } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, InvoiceType, Profile, StaffOption } from "../api/contracts";
import { AssignmentPicker } from "./AssignmentPicker";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { Failure, InvoiceStatus, Loading, Notice } from "../ui/Feedback";
import { formatDate, formatDecimal, formatFetchTime, formatMoney } from "../ui/format";

interface Props {
  assignmentVersion?: number;
  assignmentOptions?: StaffOption[];
  assignmentDisabled?: boolean;
  onAssign?(id: string, target: string | null): void;
  onReady?(): void;
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  type: InvoiceType;
  invoiceId: string;
  onBack(): void;
  onAccessDenied(): void;
}
export function InvoiceDetailPanel({
  api,
  entity,
  profile,
  type,
  invoiceId,
  onBack,
  onAccessDenied,
  assignmentVersion = 0,
  assignmentOptions = [],
  assignmentDisabled = true,
  onAssign,
  onReady,
}: Props) {
  const allowed = type === "ACCREC" || profile.role !== "STANDARD_USER";
  const title = useRef<HTMLHeadingElement>(null);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.invoice(entity.id, type, invoiceId, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, type, invoiceId, onAccessDenied],
  );
  const resource = useResource(
    `${profile.subject}:${profile.role}:${entity.id}:${type}:${invoiceId}:${assignmentVersion}`,
    load,
    allowed,
  );
  useEffect(() => {
    title.current?.focus();
  }, [invoiceId]);
  const data = resource.data;
  useEffect(() => {
    if (data) onReady?.();
  }, [data, onReady]);
  return (
    <section
      id="invoice-detail"
      className="card detail-panel"
      aria-labelledby="invoice-detail-title"
      aria-busy={resource.status === "loading" || resource.status === "refreshing"}
    >
      <div className="section-heading">
        <h2 id="invoice-detail-title" tabIndex={-1} ref={title}>
          {type === "ACCREC" ? "Invoice" : "Bill"} {data?.invoice.number || "details"}
        </h2>
        <button type="button" onClick={onBack}>
          Back to invoices
        </button>
      </div>
      <p>
        <strong>Entity:</strong> {entity.displayName}
      </p>
      <p className="supporting">
        Read-only Xero record. Dashboard fetch: {formatFetchTime(resource.loadedAt)}.
      </p>
      <button
        type="button"
        disabled={resource.status === "loading" || resource.status === "refreshing"}
        onClick={resource.refresh}
      >
        Refresh details
      </button>
      {!allowed ? <Notice tone="warning">You do not have access to supplier bills.</Notice> : null}
      {resource.status === "loading" ? <Loading label="Fetching document from Xero…" /> : null}
      {resource.status === "refreshing" ? (
        <Notice tone="warning">
          Refreshing. The displayed values are from the last successful fetch.
        </Notice>
      ) : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} label="Retry details" />
      ) : null}
      {data ? (
        <>
          <InvoiceStatus invoice={data.invoice} />
          <dl className="detail-metadata">
            <div>
              <dt>Contact</dt>
              <dd>{data.invoice.contact || "Not recorded"}</dd>
            </div>
            <div>
              <dt>Number</dt>
              <dd>{data.invoice.number || "Not recorded"}</dd>
            </div>
            <div>
              <dt>Reference</dt>
              <dd>{data.invoice.reference || "Not recorded"}</dd>
            </div>
            <div>
              <dt>Invoice date</dt>
              <dd>{formatDate(data.invoice.date)}</dd>
            </div>
            <div>
              <dt>Due date</dt>
              <dd>{formatDate(data.invoice.dueDate)}</dd>
            </div>
          </dl>
          <h3>Line items</h3>
          <p className="supporting">
            {data.taxBasis === "INCLUSIVE"
              ? "Recorded unit prices and line amounts include GST."
              : data.taxBasis === "EXCLUSIVE"
                ? "Recorded unit prices and line amounts exclude GST."
                : data.taxBasis === "NO_TAX"
                  ? "Xero records these line amounts with no tax."
                  : "The line-price GST basis is not recorded."}
          </p>
          <div
            className="detail-table-wrap"
            role="region"
            aria-label="Line item amounts"
            tabIndex={0}
          >
            <table className="financial-table line-items">
              <caption>Line items as recorded in Xero</caption>
              <thead>
                <tr>
                  <th scope="col">Description</th>
                  <th scope="col" className="money">
                    Quantity
                  </th>
                  <th scope="col" className="money">
                    Unit price
                  </th>
                  <th scope="col" className="money">
                    Line amount
                  </th>
                  <th scope="col" className="money">
                    GST
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.lines.map((line, index) => (
                  <tr key={index}>
                    <th scope="row">{line.description || "Not recorded"}</th>
                    <td className="money">{formatDecimal(line.quantity)}</td>
                    <td className="money">{formatMoney(line.unitAmount, data.invoice.currency)}</td>
                    <td className="money">{formatMoney(line.lineAmount, data.invoice.currency)}</td>
                    <td className="money">{formatMoney(line.taxAmount, data.invoice.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data.lines.length === 0 ? <p>No line items were recorded.</p> : null}
          <dl className="financial-totals">
            <div>
              <dt>Subtotal (excluding GST)</dt>
              <dd>{formatMoney(data.subtotal, data.invoice.currency)}</dd>
            </div>
            <div>
              <dt>GST</dt>
              <dd>{formatMoney(data.tax, data.invoice.currency)}</dd>
            </div>
            <div className="total">
              <dt>Total (including GST)</dt>
              <dd>{formatMoney(data.invoice.total, data.invoice.currency)}</dd>
            </div>
            <div>
              <dt>Payments recorded</dt>
              <dd>{formatMoney(data.amountPaid, data.invoice.currency)}</dd>
            </div>
            <div>
              <dt>Credits applied</dt>
              <dd>{formatMoney(data.amountCredited, data.invoice.currency)}</dd>
            </div>
            <div className="total">
              <dt>Remaining</dt>
              <dd>{formatMoney(data.invoice.amountDue, data.invoice.currency)}</dd>
            </div>
          </dl>
          <h3>Payments</h3>
          {data.payments.length ? (
            <ul className="payment-list">
              {data.payments.map((payment, index) => (
                <li key={payment.id ?? index}>
                  <span>
                    {formatDate(payment.date)} · {payment.reference || "Reference not recorded"}
                  </span>
                  <strong>{formatMoney(payment.amount, data.invoice.currency)}</strong>
                </li>
              ))}
            </ul>
          ) : (
            <p>No payments were recorded.</p>
          )}
          {type === "ACCREC" && onAssign && profile.role !== "STANDARD_USER" ? (
            <div>
              <h3>Sales invoice owner</h3>
              <AssignmentPicker
                invoice={data.invoice}
                role={profile.role}
                options={assignmentOptions}
                disabled={assignmentDisabled}
                onAssign={onAssign}
              />
            </div>
          ) : null}
          {type === "ACCREC" ? (
            <p>
              <strong>Owner:</strong>{" "}
              {data.invoice.owner
                ? `${data.invoice.owner.displayName}${data.invoice.owner.active ? "" : " (Inactive)"}`
                : "— (Unassigned)"}
            </p>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
