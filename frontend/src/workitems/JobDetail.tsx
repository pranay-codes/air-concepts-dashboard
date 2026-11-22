import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiError, isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading } from "../ui/Feedback";
import { formatDate, formatFetchTime, formatMoney } from "../ui/format";
import { Modal } from "../ui/Modal";
import { CreateJobForm } from "./CreateJobForm";
import { InvoiceReviewDialog } from "./InvoiceReviewDialog";
import type { WorkItemDetail } from "./contracts";
import { eventLabel, flagLabel, stageLabels } from "./stageText";

export function JobDetail({
  api,
  entity,
  profile,
  itemId,
  onBack,
  onDirty,
  onChanged,
  onAccessDenied,
  backLabel = "Back to jobs",
  focusFollowUp = false,
}: {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  itemId: string;
  onBack(): void;
  onDirty(value: boolean): void;
  onChanged(): void;
  onAccessDenied(): void;
  backLabel?: string;
  focusFollowUp?: boolean;
}) {
  const scope = `${profile.subject}:${profile.role}:${entity.id}:${itemId}`;
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.workItem(entity.id, itemId, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, itemId, onAccessDenied],
  );
  const resource = useResource(scope, load);
  return (
    <section aria-label="Job detail">
      {resource.status === "loading" ? <Loading label="Loading job…" /> : null}
      {resource.status === "error" ? (
        <>
          <button type="button" onClick={onBack}>
            {backLabel}
          </button>
          <Failure error={resource.error} onRetry={resource.refresh} label="Retry job" />
        </>
      ) : null}
      {resource.data ? (
        <DetailContent
          api={api}
          entity={entity}
          profile={profile}
          itemId={itemId}
          value={resource.data}
          onBack={onBack}
          onDirty={onDirty}
          onChanged={onChanged}
          onReload={resource.refresh}
          onAccessDenied={onAccessDenied}
          backLabel={backLabel}
          focusFollowUp={focusFollowUp}
        />
      ) : null}
    </section>
  );
}

function DetailContent({
  api,
  entity,
  profile,
  itemId,
  value,
  onBack,
  onDirty,
  onChanged,
  onReload,
  onAccessDenied,
  backLabel,
  focusFollowUp,
}: {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  itemId: string;
  value: WorkItemDetail;
  onBack(): void;
  onDirty(value: boolean): void;
  onChanged(): void;
  onReload(): void;
  onAccessDenied(): void;
  backLabel: string;
  focusFollowUp: boolean;
}) {
  const [saved, setSaved] = useState<WorkItemDetail | null>(null);
  const current = saved && saved.summary.version >= value.summary.version ? saved : value;
  const [notes, setNotes] = useState(current.notes ?? "");
  const [date, setDate] = useState(current.summary.followUpDate ?? "");
  const [dialog, setDialog] = useState<"close-lost" | "no-invoice-needed" | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState(false);
  const [confirmSent, setConfirmSent] = useState(false);
  const [invoiceReview, setInvoiceReview] = useState(false);
  const [invoiceSubmitted, setInvoiceSubmitted] = useState(false);
  const [message, setMessage] = useState("");
  const [jobFormOpen, setJobFormOpen] = useState(false);
  const [retryOperationId, setRetryOperationId] = useState<string | undefined>();
  const [jobFormDirty, setJobFormDirty] = useState(false);
  const [jobSubmitted, setJobSubmitted] = useState(false);
  const action = useAction(`${entity.id}:${itemId}`);
  const dirty = notes !== (current.notes ?? "") || date !== (current.summary.followUpDate ?? "");
  useEffect(() => {
    const target =
      (focusFollowUp ? document.getElementById("job-follow-up") : null) ??
      document.getElementById("job-detail-heading");
    target?.focus();
  }, [focusFollowUp]);
  useEffect(() => {
    onDirty(dirty || jobFormDirty);
    return () => onDirty(false);
  }, [dirty, jobFormDirty, onDirty]);
  function back() {
    if (dirty && !window.confirm("Discard unsaved changes before returning to jobs?")) return;
    onDirty(false);
    onBack();
  }
  function failure(error: unknown) {
    if (isAccessFailure(error)) onAccessDenied();
    if (error instanceof ApiError && error.code === "WORK_ITEM_CHANGED") {
      setSaved(null);
      onReload();
    }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    const result = await action.run(
      (signal) =>
        api.updateWorkItem(
          entity.id,
          itemId,
          {
            version: current.summary.version,
            notes,
            ...(date ? { followUpDate: date } : { clearFollowUp: true }),
          },
          signal,
        ),
      failure,
    );
    if (result) {
      setSaved(result);
      setNotes(result.notes ?? "");
      setDate(result.summary.followUpDate ?? "");
      onDirty(false);
      onChanged();
      setMessage("Changes saved.");
    }
  }
  async function close(event: FormEvent) {
    event.preventDefault();
    if (!reason.trim() || reason.length > 500) {
      setReasonError(true);
      document.getElementById("job-close-reason")?.focus();
      return;
    }
    if (!dialog) return;
    const result = await action.run(
      (signal) =>
        api.closeWorkItem(
          entity.id,
          itemId,
          dialog,
          { version: current.summary.version, reason },
          signal,
        ),
      failure,
    );
    if (result) {
      setSaved(result);
      setDialog(null);
      onChanged();
      setMessage("Job closed.");
      requestAnimationFrame(() => document.getElementById("job-detail-heading")?.focus());
    }
  }
  async function lookup(operation: string) {
    const result = await action.run(
      (signal) => api.lookupVworkOperation(entity.id, itemId, operation, signal),
      failure,
    );
    if (result) {
      setSaved(null);
      setMessage(
        result.outcome === "LINKED_EXISTING"
          ? "vWork job linked."
          : "No matching vWork job was found. You can review the details and retry.",
      );
      onReload();
      onChanged();
    }
  }
  async function markSent() {
    const result = await action.run(
      (signal) => api.markInvoiceSent(entity.id, itemId, current.summary.version, signal),
      failure,
    );
    if (result) {
      setSaved(result);
      setConfirmSent(false);
      setMessage("Invoice marked as sent.");
      onChanged();
      requestAnimationFrame(() => document.getElementById("job-detail-heading")?.focus());
    }
  }
  const allowed = new Set(current.allowedActions);
  if (jobFormOpen)
    return (
      <CreateJobForm
        api={api}
        entity={entity}
        itemId={itemId}
        profile={profile}
        onDirty={setJobFormDirty}
        onAccessDenied={onAccessDenied}
        retryOperationId={retryOperationId}
        onCancel={() => {
          if (jobFormDirty && !window.confirm("Discard unsaved job details?")) return;
          setJobFormDirty(false);
          setJobFormOpen(false);
          onReload();
        }}
        onCompleted={() => {
          setJobFormDirty(false);
          setJobFormOpen(false);
          setJobSubmitted(true);
          setMessage("vWork job linked.");
          onReload();
          onChanged();
        }}
      />
    );
  return (
    <>
      <button type="button" onClick={back}>
        {backLabel}
      </button>
      <h1 id="job-detail-heading" tabIndex={-1}>
        {current.summary.quoteNumber}
      </h1>
      <p>
        {entity.displayName} · {current.summary.customerName || "Customer not recorded"}
      </p>
      <p>Job ID: {current.summary.jobId || "Not recorded"}</p>
      <p>
        Stage: {stageLabels[current.summary.stage]} · Quote: {current.summary.xeroQuoteStatus}
      </p>
      <p>Total: {formatMoney(current.summary.quoteTotal, current.summary.currency)}</p>
      {current.summary.flags.map((flag) => (
        <span key={flag} className="badge warning">
          {flagLabel(flag)}
        </span>
      ))}
      {current.summary.attention.length ? <p className="badge warning">Needs attention</p> : null}
      {current.summary.attention.includes("INVOICE_MULTIPLE_MATCHES") ? (
        <p role="status">
          Multiple Xero invoices match this job. Review them in Xero before continuing.
        </p>
      ) : null}
      {current.operations
        .filter(
          (operation) => operation.kind === "VWORK_CREATE_JOB" && operation.status === "UNKNOWN",
        )
        .map((operation) => (
          <section key={operation.id} aria-label="Unconfirmed vWork job">
            <p>
              The vWork job creation outcome is unconfirmed. Check for the job before trying again.
            </p>
            {operation.allowLookup ? (
              <button
                type="button"
                disabled={action.busy || dirty}
                onClick={() => void lookup(operation.id)}
              >
                {action.busy ? "Checking…" : "Look up vWork job"}
              </button>
            ) : null}
          </section>
        ))}
      {current.operations
        .filter((operation) => operation.allowRetry)
        .map((operation) => (
          <section key={operation.id} aria-label="Failed vWork job">
            <p>The previous job creation failed. Review the details before trying again.</p>
            <button
              type="button"
              disabled={action.busy || dirty}
              onClick={() => {
                setRetryOperationId(operation.id);
                setJobFormOpen(true);
              }}
            >
              Retry vWork job
            </button>
          </section>
        ))}
      {allowed.has("CREATE_JOB") && !jobSubmitted ? (
        <button
          type="button"
          onClick={() => {
            if (dirty && !window.confirm("Discard unsaved changes before creating a job?")) return;
            setNotes(current.notes ?? "");
            setDate(current.summary.followUpDate ?? "");
            setRetryOperationId(undefined);
            setJobFormOpen(true);
          }}
        >
          Create vWork job
        </button>
      ) : null}
      {message ? <p role="status">{message}</p> : null}
      {action.error && !dialog && !confirmSent ? <Failure error={action.error} /> : null}
      <h3>Invoice</h3>
      {current.invoice?.invoiceId ? (
        <dl>
          <dt>Invoice number</dt>
          <dd>{current.invoice.invoiceNumber || "Not recorded"}</dd>
          <dt>Status</dt>
          <dd>{current.invoice.status || "Not recorded"}</dd>
          <dt>Total</dt>
          <dd>{current.invoice.total ?? "Not recorded"}</dd>
          <dt>Amount due</dt>
          <dd>{current.invoice.amountDue ?? "Not recorded"}</dd>
          <dt>Due date</dt>
          <dd>{formatDate(current.invoice.dueDate)}</dd>
        </dl>
      ) : (
        <p>No invoice linked.</p>
      )}
      {allowed.has("MARK_SENT") ? (
        <button type="button" disabled={action.busy || dirty} onClick={() => setConfirmSent(true)}>
          Mark invoice as sent
        </button>
      ) : null}
      {allowed.has("PREPARE_INVOICE") && !invoiceSubmitted ? (
        <button
          type="button"
          disabled={action.busy || dirty}
          onClick={() => setInvoiceReview(true)}
        >
          Prepare invoice
        </button>
      ) : null}
      <h3>Notes and follow-up</h3>
      {allowed.has("EDIT_NOTES") ? (
        <form onSubmit={save}>
          <label>
            Follow-up date
            <input
              id="job-follow-up"
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
            />
          </label>
          <label>
            Notes
            <textarea
              value={notes}
              maxLength={4000}
              onChange={(event) => setNotes(event.target.value)}
            />
          </label>
          <button type="submit" disabled={!dirty || action.busy}>
            {action.busy ? "Saving…" : "Save changes"}
          </button>
        </form>
      ) : (
        <>
          <p>Follow-up: {formatDate(current.summary.followUpDate)}</p>
          <p>{current.notes || "No notes recorded"}</p>
        </>
      )}
      {allowed.has("CLOSE_LOST") ? (
        <button
          type="button"
          disabled={action.busy || dirty}
          onClick={() => {
            setReason("");
            setReasonError(false);
            setDialog("close-lost");
          }}
        >
          Close as lost
        </button>
      ) : null}
      {allowed.has("NO_INVOICE_NEEDED") ? (
        <button
          type="button"
          disabled={action.busy || dirty}
          onClick={() => {
            setReason("");
            setReasonError(false);
            setDialog("no-invoice-needed");
          }}
        >
          No invoice needed
        </button>
      ) : null}
      <h3>Activity</h3>
      {current.events.length ? (
        <ol>
          {current.events.map((event) => (
            <li key={event.id}>
              {eventLabel(event.eventType ?? "")} · {event.actor?.displayName ?? "System"} ·{" "}
              {formatFetchTime(event.at)}
            </li>
          ))}
        </ol>
      ) : (
        <p>No activity recorded.</p>
      )}
      {invoiceReview ? (
        <InvoiceReviewDialog
          api={api}
          entity={entity}
          profile={profile}
          itemId={itemId}
          onClose={() => setInvoiceReview(false)}
          onAccessDenied={onAccessDenied}
          onCompleted={(outcome) => {
            setSaved(null);
            setInvoiceReview(false);
            setInvoiceSubmitted(true);
            setMessage(
              outcome === "CREATED" ? "Test invoice created." : "Existing test invoice linked.",
            );
            onReload();
            onChanged();
            requestAnimationFrame(() => document.getElementById("job-detail-heading")?.focus());
          }}
        />
      ) : null}
      {confirmSent ? (
        <Modal
          title="Mark invoice as sent"
          onClose={() => {
            if (!action.busy) setConfirmSent(false);
          }}
        >
          {action.error ? <Failure error={action.error} /> : null}
          <p>Confirm that the authorised invoice was sent to the customer from Xero.</p>
          <button type="button" disabled={action.busy} onClick={() => void markSent()}>
            {action.busy ? "Saving…" : "Confirm sent"}
          </button>
          <button type="button" disabled={action.busy} onClick={() => setConfirmSent(false)}>
            Cancel
          </button>
        </Modal>
      ) : null}
      {dialog ? (
        <Modal
          title={dialog === "close-lost" ? "Close as lost" : "No invoice needed"}
          onClose={() => {
            if (!action.busy) setDialog(null);
          }}
        >
          {action.error ? <Failure error={action.error} /> : null}
          <p>
            {entity.displayName} · {current.summary.quoteNumber} · Job ID:{" "}
            {current.summary.jobId || "Not recorded"}
          </p>
          <form onSubmit={close}>
            <label htmlFor="job-close-reason">Reason</label>
            <textarea
              id="job-close-reason"
              data-autofocus
              value={reason}
              maxLength={500}
              onChange={(event) => {
                setReason(event.target.value);
                setReasonError(false);
              }}
            />
            {reasonError ? <p role="alert">Enter a reason of 1–500 characters.</p> : null}
            <button type="submit" disabled={action.busy}>
              {action.busy ? "Closing…" : "Confirm closure"}
            </button>
            <button type="button" disabled={action.busy} onClick={() => setDialog(null)}>
              Cancel
            </button>
          </form>
        </Modal>
      ) : null}
    </>
  );
}
