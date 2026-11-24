import { useState } from "react";
import { useAction } from "../api/useAction";
import { Failure } from "../ui/Feedback";
import { Modal } from "../ui/Modal";
import { decimal } from "./contracts";
import { type ForecastContext, handleAccess, useDirty } from "./context";
export function OpeningBalanceForm(
  props: ForecastContext & {
    /** First month of the financial year; the opening balance is used only when the month before has no generated forecast. */
    month: string;
    initial?: string | null;
    source?: string | null;
    requireChange?: boolean;
    requireAmountChange?: boolean;
    onClose(): void;
  },
) {
  const [amount, setAmount] = useState(props.initial ?? "");
  const [source, setSource] = useState(props.source ?? "");
  const [error, setError] = useState("");
  const [dirty, setDirty] = useState(false);
  const action = useAction(`${props.subject}:${props.entity.id}:${props.month}:opening`);
  useDirty(dirty, props.onDirty);
  const close = () => {
    if (!dirty || window.confirm("Discard this opening balance edit?")) props.onClose();
  };
  return (
    <Modal title="Enter opening balance" onClose={close}>
      <p>
        {props.entity.displayName} · {props.month} · NZD
      </p>
      <form
        className="forecast-form"
        onSubmit={async (event) => {
          event.preventDefault();
          if (props.requireChange && !dirty) return;
          if (props.requireAmountChange && decimal(amount) === decimal(props.initial ?? "")) return;
          const value = decimal(amount);
          if (value === null) {
            setError("Enter a cash balance with at most two decimal places.");
            return;
          }
          setError("");
          const result = await action.run(
            (signal) =>
              props.api.saveForecastOpening(props.entity.id, props.month, value, source, signal),
            (error) => handleAccess(props.onAccessDenied, error),
          );
          if (result) {
            props.onChanged();
            props.onClose();
          }
        }}
      >
        <label>
          Opening cash balance (NZD)
          <input
            data-autofocus
            inputMode="decimal"
            required
            value={amount}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? "opening-error" : undefined}
            onChange={(e) => {
              setAmount(e.target.value);
              setDirty(true);
            }}
          />
        </label>
        <label>
          Balance source (optional)
          <input
            value={source}
            maxLength={500}
            onChange={(e) => {
              setSource(e.target.value);
              setDirty(true);
            }}
          />
        </label>
        {error ? (
          <p id="opening-error" role="alert">
            {error}
          </p>
        ) : null}
        {action.error ? <Failure error={action.error} /> : null}
        <div className="forecast-actions">
          <button
            disabled={
              action.busy ||
              (props.requireChange && !dirty) ||
              (props.requireAmountChange && decimal(amount) === decimal(props.initial ?? ""))
            }
            type="submit"
          >
            {action.busy ? "Saving…" : "Save opening balance"}
          </button>
          <button type="button" onClick={close}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
