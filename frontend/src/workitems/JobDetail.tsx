import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiError, isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading } from "../ui/Feedback";
import { formatDate, formatMoney } from "../ui/format";
import { Modal } from "../ui/Modal";
import { ActivityTimeline } from "./ActivityTimeline";
import { CreateJobForm } from "./CreateJobForm";
import { InvoiceReviewDialog } from "./InvoiceReviewDialog";
import type { WorkItemDetail } from "./contracts";
import { attentionLabel, flagLabel, stageLabels } from "./stageText";

const quoteStatusLabels: Record<string, string> = {
  DRAFT: "Draft",
  SENT: "Sent",
  ACCEPTED: "Accepted",
  DECLINED: "Declined",
  INVOICED: "Invoiced",
  DELETED: "Deleted",
};

/** Outcomes where Xero may already have changed: the page must not offer another write until reviewed. */
const holdCodes = new Set(["EXTERNAL_OUTCOME_UNKNOWN", "QUOTE_ACCEPT_RECORD_FAILED"]);

type Dialog = "accept" | "close-lost" | "no-invoice-needed" | null;

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
  const [justSaved, setJustSaved] = useState(false);
  const [dialog, setDialog] = useState<Dialog>(null);
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
  const customer = current.summary.customerName || "Customer not recorded";
  const total = formatMoney(current.summary.quoteTotal, current.summary.currency);
  const holdAccept = action.error instanceof ApiError && holdCodes.has(action.error.code);
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
  function discardNotes() {
    setNotes(current.notes ?? "");
    setDate(current.summary.followUpDate ?? "");
    setJustSaved(false);
  }
  function failure(error: unknown) {
    if (isAccessFailure(error)) onAccessDenied();
    if (error instanceof ApiError && error.code === "WORK_ITEM_CHANGED") {
      setSaved(null);
      onReload();
    }
    if (error instanceof ApiError && holdCodes.has(error.code)) {
      // Show the held state in place of the stale view; the server, not the page, decides what happens next.
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
      setJustSaved(true);
    }
  }
  async function close(event: FormEvent) {
    event.preventDefault();
    if (dialog === null || dialog === "accept") return;
    if (!reason.trim() || reason.length > 500) {
      setReasonError(true);
      document.getElementById("job-close-reason")?.focus();
      return;
    }
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
  async function acceptQuote() {
    const result = await action.run(
      (signal) => api.markQuoteAccepted(entity.id, itemId, current.summary.version, signal),
      failure,
    );
    if (result) {
      setSaved(result);
      setDialog(null);
      setMessage("Quote accepted in Xero. The job is now To schedule.");
      onChanged();
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
  const attention = current.summary.attention;
  const followUp = current.summary.followUpDate;
  const owner = current.summary.owner?.displayName;
  const quoteStatus =
    quoteStatusLabels[current.summary.xeroQuoteStatus] ?? current.summary.xeroQuoteStatus;
  const hasPrimaryAction =
    allowed.has("MARK_ACCEPTED") ||
    (allowed.has("CREATE_JOB") && !jobSubmitted) ||
    (allowed.has("PREPARE_INVOICE") && !invoiceSubmitted) ||
    allowed.has("MARK_SENT");
  const noteStatus = action.busy
    ? "Saving…"
    : dirty
      ? "Unsaved changes"
      : justSaved
        ? "Changes saved."
        : "";
  return (
    <>
      <button type="button" className="text-button" onClick={back}>
        {backLabel}
      </button>

      <header className="job-header">
        <div className="job-title">
          <p className="supporting">{entity.displayName}</p>
          <h1 id="job-detail-heading" tabIndex={-1}>
            {current.summary.quoteNumber}
          </h1>
          <p>{customer}</p>
        </div>
        {hasPrimaryAction ? (
          <section className="header-actions" aria-label="Next action">
            {allowed.has("MARK_ACCEPTED") ? (
              <button
                type="button"
                className="primary"
                disabled={action.busy || dirty}
                onClick={() => {
                  setReason("");
                  setDialog("accept");
                }}
              >
                Accept quote in Xero
              </button>
            ) : null}
            {allowed.has("CREATE_JOB") && !jobSubmitted ? (
              <button
                type="button"
                className="primary"
                onClick={() => {
                  if (dirty && !window.confirm("Discard unsaved changes before creating a job?"))
                    return;
                  setNotes(current.notes ?? "");
                  setDate(current.summary.followUpDate ?? "");
                  setRetryOperationId(undefined);
                  setJobFormOpen(true);
                }}
              >
                Create vWork job
              </button>
            ) : null}
            {allowed.has("PREPARE_INVOICE") && !invoiceSubmitted ? (
              <button
                type="button"
                className="primary"
                disabled={action.busy || dirty}
                onClick={() => setInvoiceReview(true)}
              >
                Prepare invoice
              </button>
            ) : null}
            {allowed.has("MARK_SENT") ? (
              <button
                type="button"
                className="primary"
                disabled={action.busy || dirty}
                onClick={() => setConfirmSent(true)}
              >
                Mark invoice as sent
              </button>
            ) : null}
          </section>
        ) : null}
      </header>

      <dl className="status-strip" aria-label="Job summary">
        <div>
          <dt>Stage</dt>
          <dd>{stageLabels[current.summary.stage]}</dd>
        </div>
        <div>
          <dt>Xero quote</dt>
          <dd>{quoteStatus}</dd>
        </div>
        <div>
          <dt>Owner</dt>
          <dd>{owner ?? "Not assigned"}</dd>
        </div>
        <div>
          <dt>Follow-up</dt>
          <dd>{followUp ? formatDate(followUp) : "Not set"}</dd>
        </div>
        <div>
          <dt>Total</dt>
          <dd className="money">{total}</dd>
        </div>
        <div>
          <dt>Job ID</dt>
          <dd>{current.summary.jobId || "Not recorded"}</dd>
        </div>
      </dl>

      {current.summary.flags.length ? (
        <div className="badge-row" aria-label="Data checks">
          {current.summary.flags.map((flag) => (
            <span key={flag} className="badge warning">
              {flagLabel(flag)}
            </span>
          ))}
        </div>
      ) : null}

      {message ? (
        <p role="status" className="notice success">
          {message}
        </p>
      ) : null}
      {action.error && !dialog && !confirmSent ? <Failure error={action.error} /> : null}

      {attention.length ? (
        <section className="attention-panel" aria-labelledby="job-attention-title">
          <h2 id="job-attention-title">Needs attention</h2>
          <ul>
            {attention.map((code) => (
              <li key={code}>{attentionLabel(code, current.summary.stage)}</li>
            ))}
          </ul>
        </section>
      ) : null}
      {current.operations
        .filter(
          (operation) => operation.kind === "VWORK_CREATE_JOB" && operation.status === "UNKNOWN",
        )
        .map((operation) => (
          <section key={operation.id} aria-label="Unconfirmed vWork job" className="notice warning">
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
          <section key={operation.id} aria-label="Failed vWork job" className="notice warning">
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

      <div className="job-layout">
        <div className="job-main">
          <section className="card" aria-labelledby="job-notes-title">
            <h2 id="job-notes-title">Follow-up and notes</h2>
            {allowed.has("EDIT_NOTES") ? (
              <form onSubmit={save} className="detail-form">
                <div className="field">
                  <label htmlFor="job-follow-up">Follow-up date</label>
                  <input
                    id="job-follow-up"
                    className="date-compact"
                    type="date"
                    value={date}
                    onChange={(event) => {
                      setDate(event.target.value);
                      setJustSaved(false);
                    }}
                  />
                  <p className="supporting">Optional. Clear the date to remove the follow-up.</p>
                </div>
                <div className="field">
                  <label htmlFor="job-notes">Notes</label>
                  <textarea
                    id="job-notes"
                    rows={6}
                    maxLength={4000}
                    aria-describedby="job-notes-count"
                    value={notes}
                    onChange={(event) => {
                      setNotes(event.target.value);
                      setJustSaved(false);
                    }}
                  />
                  <p id="job-notes-count" className="supporting">
                    {notes.length} of 4,000 characters
                  </p>
                </div>
                <div className="form-footer">
                  <div className="form-actions">
                    <button type="submit" className="primary" disabled={!dirty || action.busy}>
                      {action.busy ? "Saving…" : "Save changes"}
                    </button>
                    <button type="button" disabled={!dirty || action.busy} onClick={discardNotes}>
                      Discard changes
                    </button>
                  </div>
                  <p role="status" className="supporting form-status">
                    {noteStatus}
                  </p>
                </div>
              </form>
            ) : (
              <>
                <p>Follow-up: {followUp ? formatDate(followUp) : "Not set"}</p>
                <p>{current.notes || "No notes recorded"}</p>
              </>
            )}
          </section>

          <section className="card" aria-labelledby="job-activity-title">
            <h2 id="job-activity-title">Activity</h2>
            <ActivityTimeline events={current.events} />
          </section>
        </div>

        <aside className="job-side">
          <section className="card" aria-labelledby="job-invoice-title">
            <h2 id="job-invoice-title">Invoice</h2>
            {current.invoice?.invoiceId ? (
              <dl className="compact-list">
                <div>
                  <dt>Number</dt>
                  <dd>{current.invoice.invoiceNumber || "Not recorded"}</dd>
                </div>
                <div>
                  <dt>Status</dt>
                  <dd>{current.invoice.status || "Not recorded"}</dd>
                </div>
                <div>
                  <dt>Total</dt>
                  <dd className="money">{current.invoice.total ?? "Not recorded"}</dd>
                </div>
                <div>
                  <dt>Amount due</dt>
                  <dd className="money">{current.invoice.amountDue ?? "Not recorded"}</dd>
                </div>
                <div>
                  <dt>Due date</dt>
                  <dd>{formatDate(current.invoice.dueDate)}</dd>
                </div>
              </dl>
            ) : (
              <p className="supporting">No invoice is linked to this job yet.</p>
            )}
          </section>

          {allowed.has("CLOSE_LOST") || allowed.has("NO_INVOICE_NEEDED") ? (
            <section className="card" aria-labelledby="job-close-title">
              <h2 id="job-close-title">Close this job</h2>
              <p className="supporting">Closing needs a reason.</p>
              <div className="stack-actions">
                {allowed.has("CLOSE_LOST") ? (
                  <button
                    type="button"
                    className="danger"
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
              </div>
            </section>
          ) : null}
        </aside>
      </div>

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
      {dialog === "accept" ? (
        <Modal
          title="Accept quote in Xero"
          onClose={() => {
            if (!action.busy) setDialog(null);
          }}
        >
          {action.error ? <Failure error={action.error} /> : null}
          <dl className="detail-metadata">
            <div>
              <dt>Organisation</dt>
              <dd>{entity.displayName}</dd>
            </div>
            <div>
              <dt>Quote</dt>
              <dd>{current.summary.quoteNumber}</dd>
            </div>
            <div>
              <dt>Customer</dt>
              <dd>{customer}</dd>
            </div>
            <div>
              <dt>Total</dt>
              <dd className="money">{total}</dd>
            </div>
          </dl>
          <p>
            Xero will show this quote as accepted. The job moves to To schedule, and its owner
            becomes the Service Manager.
          </p>
          <p className="supporting">
            If Xero&apos;s outcome cannot be confirmed, the job is held for review and is not
            retried automatically.
          </p>
          <button
            type="button"
            className="primary"
            disabled={action.busy || holdAccept}
            onClick={() => void acceptQuote()}
          >
            {action.busy ? "Accepting…" : "Accept in Xero"}
          </button>
          <button
            type="button"
            data-autofocus
            disabled={action.busy}
            onClick={() => setDialog(null)}
          >
            Cancel
          </button>
        </Modal>
      ) : null}
      {dialog === "close-lost" || dialog === "no-invoice-needed" ? (
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
