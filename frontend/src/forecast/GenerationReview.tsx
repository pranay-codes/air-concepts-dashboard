import { Failure } from "../ui/Feedback";
import { formatMoney } from "../ui/format";
import { monthName } from "./ForecastTable";
import type { ForecastMonth } from "./contracts";

/**
 * Review step before a month is frozen or extended. States what will be written, the amounts, and that the
 * action cannot be undone, so the user confirms the consequence rather than a bare button (UI/UX spec: consequential actions).
 */
export function GenerationReview(props: {
  month: ForecastMonth;
  busy: boolean;
  error: unknown;
  onConfirm(): void;
  onCancel(): void;
}) {
  const { month } = props;
  const name = monthName(month.month);
  const regenerate = month.state === "GENERATED";
  const count = regenerate ? month.pendingNewItems : month.receiptCount + month.paymentCount;
  const noun = count === 1 ? "item" : "items";
  return (
    <section className="forecast-review" aria-labelledby="generation-review-heading">
      <h2 id="generation-review-heading">
        {regenerate ? `Add new items to ${name}` : `Generate forecast for ${name}`}
      </h2>
      {regenerate ? (
        <p>
          {count} new {noun} will be added. Lines already in this forecast, including invoices since
          paid, are not changed.
        </p>
      ) : (
        <p>
          This freezes {count} planned {noun} for {name}: money in{" "}
          {formatMoney(month.receipts, "NZD")}, money out {formatMoney(month.payments, "NZD")}.
          Later Xero changes do not alter it.
        </p>
      )}
      <p className="supporting">This cannot be undone.</p>
      {count === 0 ? <p>There are no new items. Nothing will change.</p> : null}
      {props.error ? <Failure error={props.error} /> : null}
      <div className="forecast-actions">
        <button type="button" disabled={props.busy || count === 0} onClick={props.onConfirm}>
          {props.busy ? "Saving…" : regenerate ? "Add items" : "Generate forecast"}
        </button>
        <button type="button" disabled={props.busy} onClick={props.onCancel}>
          Cancel
        </button>
      </div>
    </section>
  );
}
