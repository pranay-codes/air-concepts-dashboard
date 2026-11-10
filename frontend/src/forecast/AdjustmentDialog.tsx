import { useCallback, useState } from "react";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading } from "../ui/Feedback";
import { Modal } from "../ui/Modal";
import { formatDate, formatMoney } from "../ui/format";
import { type StoredDocument, cents, decimal } from "./contracts";
import { type ForecastContext, handleAccess, useDirty } from "./context";
export type AdjustmentMode = "date" | "split" | "exclude" | "conflict";
const titles = {
  date: "Change forecast date",
  split: "Split payment schedule",
  exclude: "Review forecast exclusion",
  conflict: "Review Xero change",
};
export function DocumentEditor(
  props: ForecastContext & { documentId: string; mode: AdjustmentMode; onClose(): void },
) {
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await props.api.forecastDocument(props.entity.id, props.documentId, signal);
      } catch (e) {
        if (!signal.aborted) handleAccess(props.onAccessDenied, e);
        throw e;
      }
    },
    [props.api, props.entity.id, props.documentId, props.onAccessDenied],
  );
  const resource = useResource(`${props.subject}:${props.entity.id}:${props.documentId}`, load);
  if (resource.data) return <AdjustmentDialog {...props} document={resource.data} />;
  return (
    <Modal title={titles[props.mode]} onClose={props.onClose}>
      {resource.error ? (
        <Failure error={resource.error} onRetry={resource.refresh} />
      ) : (
        <Loading label="Loading stored document…" />
      )}
      <button type="button" onClick={props.onClose}>
        Cancel
      </button>
    </Modal>
  );
}
export function AdjustmentDialog(
  props: ForecastContext & { document: StoredDocument; mode: AdjustmentMode; onClose(): void },
) {
  const doc = props.document;
  const [date, setDate] = useState(doc.adjustment.expectedDateOverride ?? "");
  const [reason, setReason] = useState(doc.adjustment.exclusionReason ?? "");
  const [resolution, setResolution] = useState<"KEEP_SCHEDULE" | "DISCARD_SCHEDULE">(
    "KEEP_SCHEDULE",
  );
  const [dirty, setDirty] = useState(false);
  const [lines, setLines] = useState(() =>
    doc.adjustment.lines.length
      ? doc.adjustment.lines.map((l, i) => ({
          key: String(i),
          date: l.expectedDate ?? "",
          amount: l.amount ?? "",
        }))
      : [
          {
            key: "initial",
            date: doc.xeroExpectedDate ?? doc.dueDate ?? "",
            amount: doc.amountDue,
          },
        ],
  );
  const action = useAction(`${props.subject}:${props.entity.id}:${doc.id}:${props.mode}`);
  useDirty(dirty, props.onDirty);
  const sum = lines.reduce((total, line) => total + (cents(line.amount) ?? 0n), 0n);
  const mismatch =
    lines.some((l) => cents(l.amount) === null || (cents(l.amount) ?? 0n) <= 0n) ||
    sum !== cents(doc.amountDue);
  const close = () => {
    if (!dirty || window.confirm("Discard this forecast edit?")) props.onClose();
  };
  async function save(remove = false) {
    const result = await action.run(
      (signal) =>
        props.mode === "date"
          ? props.api.forecastAdjustment(
              props.entity.id,
              doc.id,
              { expectedDateOverride: date || null },
              signal,
            )
          : props.mode === "exclude"
            ? props.api.forecastAdjustment(
                props.entity.id,
                doc.id,
                {
                  excluded: !doc.adjustment.excluded,
                  ...(!doc.adjustment.excluded ? { exclusionReason: reason.trim() } : {}),
                },
                signal,
              )
            : props.mode === "conflict"
              ? props.api.forecastConflict(props.entity.id, doc.id, resolution, signal)
              : props.api.forecastSplits(
                  props.entity.id,
                  doc.id,
                  remove
                    ? []
                    : lines.map((l) => ({
                        expectedDate: l.date,
                        amount: decimal(l.amount) ?? l.amount,
                      })),
                  signal,
                ),
      (error) => handleAccess(props.onAccessDenied, error),
    );
    if (result) {
      props.onChanged();
      props.onClose();
    }
  }
  const label =
    props.mode === "split"
      ? "Save schedule"
      : props.mode === "exclude"
        ? doc.adjustment.excluded
          ? "Include in forecast"
          : "Exclude from forecast"
        : props.mode === "conflict"
          ? "Confirm resolution"
          : "Save forecast date";
  return (
    <Modal title={titles[props.mode]} onClose={close}>
      <p>
        <strong>{props.entity.displayName}</strong> · {doc.number ?? doc.id.slice(0, 8)} ·{" "}
        {doc.contact}
      </p>
      <p>Outstanding balance: {formatMoney(doc.amountDue, "NZD")}</p>
      {!doc.outstanding ? (
        <p role="alert">This document is no longer outstanding. Refresh the list.</p>
      ) : null}
      <form
        className="forecast-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        {props.mode === "date" ? (
          <>
            <label>
              Forecast date override (optional)
              <input
                type="date"
                value={date}
                onChange={(e) => {
                  setDate(e.target.value);
                  setDirty(true);
                }}
              />
            </label>
            <p>
              Leave blank to use the Xero expected date, then the due date. This edit stays in the
              dashboard.
            </p>
          </>
        ) : null}
        {props.mode === "exclude" ? (
          <>
            {doc.adjustment.excluded ? (
              <p>Include this document again using its forecast date and schedule?</p>
            ) : (
              <>
                <label>
                  Reason for exclusion
                  <textarea
                    required
                    maxLength={500}
                    value={reason}
                    onChange={(e) => {
                      setReason(e.target.value);
                      setDirty(true);
                    }}
                  />
                </label>
                <p>
                  The invoice remains in Xero. It will be omitted from forecast totals until you
                  include it again.
                </p>
              </>
            )}
          </>
        ) : null}
        {props.mode === "conflict" ? (
          <>
            <p>
              Xero expected date changed from {formatDate(doc.adjustment.previousXeroDate)} to{" "}
              {formatDate(doc.adjustment.newXeroDate)}, or its outstanding balance changed.
            </p>
            <label>
              Schedule decision
              <select
                value={resolution}
                onChange={(e) => {
                  setResolution(
                    e.target.value === "DISCARD_SCHEDULE" ? "DISCARD_SCHEDULE" : "KEEP_SCHEDULE",
                  );
                  setDirty(true);
                }}
              >
                <option value="KEEP_SCHEDULE">Keep the split schedule</option>
                <option value="DISCARD_SCHEDULE">Discard schedule and use the forecast date</option>
              </select>
            </label>
          </>
        ) : null}
        {props.mode === "split" ? (
          <>
            {lines.map((line, index) => (
              <div className="forecast-split-line" key={line.key}>
                <label>
                  Split {index + 1} date
                  <input
                    type="date"
                    required
                    value={line.date}
                    onChange={(e) => {
                      setLines((items) =>
                        items.map((l, i) => (i === index ? { ...l, date: e.target.value } : l)),
                      );
                      setDirty(true);
                    }}
                  />
                </label>
                <label>
                  Split {index + 1} amount (NZD)
                  <input
                    inputMode="decimal"
                    required
                    value={line.amount}
                    onChange={(e) => {
                      setLines((items) =>
                        items.map((l, i) => (i === index ? { ...l, amount: e.target.value } : l)),
                      );
                      setDirty(true);
                    }}
                  />
                </label>
                <button
                  type="button"
                  disabled={lines.length === 1}
                  onClick={() => {
                    setLines((items) => items.filter((_, i) => i !== index));
                    setDirty(true);
                  }}
                >
                  Remove split {index + 1}
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => {
                setLines((items) => [...items, { key: crypto.randomUUID(), date: "", amount: "" }]);
                setDirty(true);
              }}
            >
              Add split
            </button>
            {mismatch ? (
              <p role="alert">
                Split amounts must equal the outstanding balance of{" "}
                {formatMoney(doc.amountDue, "NZD")}.
              </p>
            ) : (
              <p role="status">Split amounts match the outstanding balance.</p>
            )}
            {doc.adjustment.lines.length ? (
              <button
                type="button"
                disabled={action.busy}
                onClick={() => {
                  if (
                    window.confirm("Remove the split schedule and use the document forecast date?")
                  )
                    void save(true);
                }}
              >
                Remove schedule
              </button>
            ) : null}
          </>
        ) : null}
        {action.error ? <Failure error={action.error} /> : null}
        <div className="forecast-actions">
          <button
            type="submit"
            disabled={
              !doc.outstanding ||
              action.busy ||
              (props.mode === "split" && mismatch) ||
              (props.mode === "exclude" && !doc.adjustment.excluded && !reason.trim())
            }
          >
            {action.busy ? "Saving…" : label}
          </button>
          <button type="button" onClick={close}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
