import { useCallback, useRef, useState } from "react";
import type { DashboardApi } from "../api/dashboard";
import { isAccessFailure } from "../api/client";
import { useResource } from "../api/useResource";
import { useAction } from "../api/useAction";
import { Failure, Loading } from "../ui/Feedback";
import { formatDecimal } from "../ui/format";
import { decimal } from "./contracts";
import { currentMonth } from "./ForecastHeader";
import { monthName, signClass } from "./ForecastTable";

export function stepMonth(month: string, offset: number) {
  const [year = "0", value = "1"] = month.split("-");
  return new Date(Date.UTC(Number(year), Number(value) - 1 + offset, 1)).toISOString().slice(0, 7);
}
export function ForecastGrid(props: {
  api: DashboardApi;
  entityId: string;
  subject: string;
  revision: number;
  onAccessDenied(): void;
  onChanged(): void;
}) {
  const { api, entityId, onAccessDenied } = props;
  const [start, setStart] = useState(() => stepMonth(currentMonth(), -1));
  const [success, setSuccess] = useState<{
    omittedCurrentItems: number;
    changedProtectedSchedules: number;
  } | null>(null);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.forecastGrid(entityId, start, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entityId, onAccessDenied, start],
  );
  const resource = useResource(
    `${props.subject}:${props.entityId}:grid:${start}:${props.revision}`,
    load,
  );
  const action = useAction(`${props.subject}:${props.entityId}:grid:generate`);
  return (
    <section aria-label="Cash forecast">
      <div className="forecast-grid-controls">
        <div role="group" aria-label="Forecast months">
          <button
            type="button"
            aria-label="Previous month"
            onClick={() => setStart((value) => stepMonth(value, -1))}
          >
            ‹
          </button>{" "}
          <button
            type="button"
            aria-label="Next month"
            onClick={() => setStart((value) => stepMonth(value, 1))}
          >
            ›
          </button>
        </div>
        <button
          type="button"
          disabled={action.busy}
          onClick={() => {
            setSuccess(null);
            void action
              .run(
                (signal) => props.api.generateForecastGrid(props.entityId, signal),
                (error) => {
                  if (isAccessFailure(error)) props.onAccessDenied();
                },
              )
              .then((result) => {
                if (result) {
                  setSuccess(result);
                  resource.refresh();
                }
              });
          }}
        >
          {action.busy ? "Creating forecast…" : "Create forecast"}
        </button>
      </div>
      <p className="supporting">
        Current and past forecasts are preserved. Creating a forecast refreshes the next six months
        from the latest synced Xero data. Rows are grouped by contact name; contacts with the same
        name appear together. Balances include the staffing estimate shown below. A dash means no
        amount for that contact, or a month that has not been created. An entered opening overrides
        the carried balance.
      </p>
      {success ? (
        <p role="status">
          Forecast created. Future months have been refreshed.{" "}
          {success.omittedCurrentItems
            ? `${success.omittedCurrentItems} new current-month items were not added because that month is preserved.`
            : ""}{" "}
          {success.changedProtectedSchedules
            ? `${success.changedProtectedSchedules} documents have changed schedules and were excluded from the refresh to prevent double-counting; review them in Xero items.`
            : ""}
        </p>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {resource.status === "loading" ? <Loading label="Loading forecast grid…" /> : null}
      {resource.status === "refreshing" ? <p role="status">Refreshing forecast…</p> : null}
      {resource.data ? (
        <div
          className="forecast-grid-scroll"
          role="region"
          aria-label="Cash forecast table"
          tabIndex={0}
        >
          <table className="forecast-grid-table">
            <caption>Cash forecast · NZD</caption>
            <thead>
              <tr>
                <th scope="col" aria-label="Forecast rows" />
                {resource.data.months.map((month) => (
                  <th scope="col" key={month.month}>
                    {monthName(month.month)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr>
                <th scope="row">Opening balance</th>
                {resource.data.months.map((month) => (
                  <td className="money" key={month.month}>
                    <OpeningCell
                      key={`${props.subject}:${entityId}:${month.month}`}
                      api={api}
                      entityId={entityId}
                      subject={props.subject}
                      month={month}
                      onSaved={props.onChanged}
                      onAccessDenied={onAccessDenied}
                    />
                  </td>
                ))}
              </tr>
              {(["IN", "OUT"] as const).map((direction) => (
                <FragmentRows key={direction} direction={direction} grid={resource.data!} />
              ))}
              <tr className="forecast-grid-summary">
                <th scope="row">Closing balance</th>
                {resource.data.months.map((month) => (
                  <td className={`money ${signClass(month.closing) ?? ""}`} key={month.month}>
                    {!month.available
                      ? "\u2014"
                      : month.closing === null
                        ? "Not recorded"
                        : formatDecimal(month.closing, 2)}
                  </td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
function OpeningCell(props: {
  api: DashboardApi;
  entityId: string;
  subject: string;
  month: import("../api/dashboard").ForecastGrid["months"][number];
  onSaved(): void;
  onAccessDenied(): void;
}) {
  const { month } = props;
  const editButton = useRef<HTMLButtonElement>(null);
  const [editing, setEditing] = useState(false);
  const [amount, setAmount] = useState(month.opening ?? "");
  const [error, setError] = useState("");
  const action = useAction(`${props.subject}:${props.entityId}:${month.month}:grid-opening`);
  if (!editing)
    return (
      <>
        <span className={signClass(month.opening)}>
          {month.opening === null ? "Not recorded" : formatDecimal(month.opening, 2)}
        </span>{" "}
        <button
          type="button"
          ref={editButton}
          className="compact-button"
          aria-label={`Edit opening balance for ${monthName(month.month)}`}
          onClick={() => {
            setAmount(month.opening ?? "");
            setError("");
            setEditing(true);
          }}
        >
          Edit
        </button>
      </>
    );
  return (
    <form
      className="forecast-grid-opening"
      onSubmit={(event) => {
        event.preventDefault();
        const value = decimal(amount);
        if (value === null) {
          setError("Enter a cash balance with at most two decimal places.");
          return;
        }
        if (month.openingSource === "CARRIED_FORWARD" && value === decimal(month.opening ?? ""))
          return;
        setError("");
        void action
          .run(
            (signal) =>
              props.api.saveForecastOpening(
                props.entityId,
                month.month,
                value,
                month.openingNote ?? "",
                signal,
              ),
            (failure) => {
              if (isAccessFailure(failure)) props.onAccessDenied();
            },
          )
          .then((result) => {
            if (result) {
              setEditing(false);
              props.onSaved();
              requestAnimationFrame(() => editButton.current?.focus());
            }
          });
      }}
    >
      <input
        autoFocus
        inputMode="decimal"
        aria-label={`Opening balance for ${monthName(month.month)}`}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `opening-error-${month.month}` : undefined}
        value={amount}
        onChange={(event) => setAmount(event.target.value)}
      />
      <button
        className="compact-button"
        type="submit"
        disabled={
          action.busy ||
          (month.openingSource === "CARRIED_FORWARD" &&
            decimal(amount) === decimal(month.opening ?? ""))
        }
      >
        {action.busy ? "Saving..." : "Save"}
      </button>
      <button
        className="compact-button"
        type="button"
        disabled={action.busy}
        onClick={() => {
          setEditing(false);
          requestAnimationFrame(() => editButton.current?.focus());
        }}
      >
        Cancel
      </button>
      {error ? (
        <p id={`opening-error-${month.month}`} role="alert">
          {error}
        </p>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
    </form>
  );
}
function FragmentRows({
  direction,
  grid,
}: {
  direction: "IN" | "OUT";
  grid: import("../api/dashboard").ForecastGrid;
}) {
  const rows = grid.rows.filter((row) => row.direction === direction);
  return (
    <>
      <tr>
        <th colSpan={9} scope="colgroup">
          {direction === "IN" ? "Money In" : "Money Out"}
        </th>
      </tr>
      {rows.length === 0 ? (
        <tr>
          <td colSpan={9}>No {direction === "IN" ? "invoices" : "bills"} in saved months.</td>
        </tr>
      ) : (
        rows.map((row) => (
          <tr key={row.contact}>
            <th scope="row">{row.contact}</th>
            {grid.months.map((month) => (
              <td className="money" key={month.month}>
                {!month.available
                  ? "\u2014"
                  : row.amounts[month.month]
                    ? formatDecimal(row.amounts[month.month], 2)
                    : "—"}
              </td>
            ))}
          </tr>
        ))
      )}
      {direction === "OUT" ? (
        <tr>
          <th scope="row">Staffing Expenses</th>
          {grid.months.map((month) => (
            <td className="money" key={month.month}>
              {month.staffingExpenses === null ? "—" : formatDecimal(month.staffingExpenses, 2)}
            </td>
          ))}
        </tr>
      ) : null}
      <tr className="forecast-grid-summary">
        <th scope="row">Total {direction === "IN" ? "Money In" : "Money Out"}</th>
        {grid.months.map((month) => {
          const total = direction === "IN" ? month.moneyIn : month.moneyOut;
          return (
            <td className="money" key={month.month}>
              {total === null ? "\u2014" : formatDecimal(total, 2)}
            </td>
          );
        })}
      </tr>
    </>
  );
}
