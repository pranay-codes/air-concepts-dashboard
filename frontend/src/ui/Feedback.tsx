import { useEffect, useState, type ReactNode } from "react";
import { ApiError, apiMessage } from "../api/client";
import type { Invoice, Role } from "../api/contracts";

export function Loading({ label = "Loading…" }: { label?: string }) {
  return (
    <div role="status" className="loading-state">
      <p>{label}</p>
      <div aria-hidden="true">
        <div className="skeleton" />
        <div className="skeleton" />
        <div className="skeleton" />
      </div>
    </div>
  );
}
export function Notice({
  children,
  tone = "information",
}: {
  children: ReactNode;
  tone?: "information" | "success" | "warning";
}) {
  return (
    <div className={`notice ${tone}`} role="status">
      {children}
    </div>
  );
}
export function Failure({
  error,
  onRetry,
  label = "Retry",
}: {
  error: unknown;
  onRetry?: () => void;
  label?: string;
}) {
  const [now, setNow] = useState(Date.now);
  const retryAt = error instanceof ApiError ? error.retryAt : undefined;
  useEffect(() => {
    if (retryAt === undefined) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [retryAt]);
  const remaining = retryAt === undefined ? 0 : Math.max(0, Math.ceil((retryAt - now) / 1000));
  return (
    <div className="notice error">
      <p role="alert">{apiMessage(error)}</p>
      {onRetry ? (
        <button type="button" disabled={remaining > 0} onClick={onRetry}>
          {label}
          {remaining > 0 ? ` (${remaining}s)` : ""}
        </button>
      ) : null}
    </div>
  );
}
const statusNames: Record<Invoice["status"], string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  AUTHORISED: "Authorised",
  PAID: "Paid",
  VOIDED: "Voided",
  DELETED: "Deleted",
};
export function InvoiceStatus({ invoice }: { invoice: Invoice }) {
  return (
    <span className="status-group">
      <span className={`badge ${invoice.status === "PAID" ? "success" : "information"}`}>
        {statusNames[invoice.status]}
      </span>
      {invoice.overdue ? <span className="badge error">Overdue</span> : null}
    </span>
  );
}
export const roleNames: Record<Role, string> = {
  DIRECTOR: "Director",
  FINANCIAL_ADMIN: "Financial Admin",
  STANDARD_USER: "Standard user",
};
