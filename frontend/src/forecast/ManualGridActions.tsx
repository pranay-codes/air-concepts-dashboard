import { useState } from "react";
import type { DashboardApi } from "../api/dashboard";
import { isAccessFailure } from "../api/client";
import { useAction } from "../api/useAction";
import { Failure } from "../ui/Feedback";
import { Modal } from "../ui/Modal";
import { formatMoney } from "../ui/format";
import { cents, decimal, type ManualItem } from "./contracts";

export function ManualGridActions({
  api,
  entityId,
  itemId,
  label,
  onSaved,
  onAccessDenied,
}: {
  api: DashboardApi;
  entityId: string;
  itemId: string;
  label: string;
  onSaved(): void;
  onAccessDenied(): void;
}) {
  const [dialog, setDialog] = useState<{ mode: "edit" | "remove"; item: ManualItem } | null>(null);
  const [missing, setMissing] = useState(false);
  const action = useAction(`${entityId}:${itemId}:manual-grid`);
  async function open(mode: "edit" | "remove") {
    setMissing(false);
    const items = await action.run(
      (signal) => api.forecastManualItems(entityId, false, signal),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (!items) return;
    const item = items.find((entry) => entry.id === itemId && entry.active);
    if (!item) {
      setMissing(true);
      return;
    }
    setDialog({ mode, item });
  }
  return (
    <>
      <button
        type="button"
        className="forecast-grid-manual-action"
        disabled={action.busy}
        onClick={() => void open("edit")}
        aria-label={`Edit ${label}`}
      >
        Edit
      </button>
      <button
        type="button"
        className="forecast-grid-manual-action"
        disabled={action.busy}
        onClick={() => void open("remove")}
        aria-label={`Remove ${label}`}
      >
        Remove
      </button>
      {missing ? (
        <span role="status">This line is no longer active. Refresh the forecast.</span>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
      {dialog ? (
        <ManualGridDialog
          key={`${itemId}:${dialog.mode}`}
          {...{ api, entityId, onSaved, onAccessDenied }}
          {...dialog}
          onClose={() => setDialog(null)}
        />
      ) : null}
    </>
  );
}

function ManualGridDialog({
  api,
  entityId,
  item,
  mode,
  onSaved,
  onAccessDenied,
  onClose,
}: {
  api: DashboardApi;
  entityId: string;
  item: ManualItem;
  mode: "edit" | "remove";
  onSaved(): void;
  onAccessDenied(): void;
  onClose(): void;
}) {
  const [description, setDescription] = useState(item.description);
  const [amount, setAmount] = useState(item.amount);
  const [recurrence, setRecurrence] = useState(item.recurrence);
  const [startMonth, setStartMonth] = useState(item.startMonth);
  const [endMonth, setEndMonth] = useState(item.endMonth ?? "");
  const [dirty, setDirty] = useState(false);
  const [validation, setValidation] = useState("");
  const action = useAction(`${entityId}:${item.id}:${mode}`);
  const close = () => {
    if (!action.busy && (!dirty || window.confirm("Discard your changes to this manual line?")))
      onClose();
  };
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const value = decimal(amount);
    if (
      mode === "edit" &&
      (!description.trim() ||
        value === null ||
        (cents(value) ?? 0n) <= 0n ||
        (endMonth && endMonth < startMonth))
    ) {
      setValidation("Enter a description, an amount greater than zero, and a valid month range.");
      return;
    }
    setValidation("");
    const result = await action.run(
      (signal) =>
        mode === "remove"
          ? api.deactivateForecastManual(entityId, item.id, signal)
          : api.saveForecastManual(
              entityId,
              {
                description: description.trim(),
                amount: value!,
                direction: item.direction,
                category: item.category,
                recurrence,
                startMonth,
                endMonth: endMonth || null,
                dayOfMonth: item.dayOfMonth ?? undefined,
                active: true,
              },
              item.id,
              signal,
            ),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) {
      onSaved();
      onClose();
    }
  }
  return (
    <Modal title={`${mode === "edit" ? "Edit" : "Remove"} manual line`} onClose={close}>
      <form
        className="forecast-form"
        onSubmit={(event) => void submit(event)}
        onChange={() => setDirty(true)}
      >
        {mode === "edit" ? (
          <>
            <p>Changes apply to the current and future months. Past months will be kept.</p>
            <p>Individual month overrides remain in place.</p>
            <label>
              Description
              <input
                data-autofocus
                required
                maxLength={200}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
            <label>
              Amount (NZD)
              <input
                required
                inputMode="decimal"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </label>
            <label>
              Frequency
              <select value={recurrence} onChange={(e) => setRecurrence(e.target.value)}>
                <option value="ONE_OFF">One-off</option>
                <option value="MONTHLY">Monthly</option>
              </select>
            </label>
            <label>
              Start month
              <input
                required
                type="month"
                value={startMonth}
                onChange={(e) => setStartMonth(e.target.value)}
              />
            </label>
            <label>
              End month (optional)
              <input
                type="month"
                min={startMonth}
                value={endMonth}
                onChange={(e) => setEndMonth(e.target.value)}
              />
            </label>
          </>
        ) : (
          <p>
            Remove {item.description} ({formatMoney(item.amount, "NZD")}) from the current and
            future forecast? Past months will be kept.
          </p>
        )}
        {validation ? <p role="alert">{validation}</p> : null}
        {action.error ? <Failure error={action.error} /> : null}
        <div className="forecast-actions">
          <button type="submit" disabled={action.busy}>
            {action.busy ? "Saving…" : mode === "edit" ? "Save changes" : "Remove line"}
          </button>
          <button type="button" disabled={action.busy} onClick={close}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
