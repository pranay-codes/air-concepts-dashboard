import { useCallback, useState } from "react";
import type { DashboardApi } from "../api/dashboard";
import { isAccessFailure } from "../api/client";
import { useResource } from "../api/useResource";
import { useAction } from "../api/useAction";
import { Failure, Loading } from "../ui/Feedback";
import { formatMoney } from "../ui/format";
import { currentMonth } from "./ForecastHeader";
import { monthName } from "./ForecastTable";

export function stepMonth(month: string, offset: number) {
  const [year = "0", value = "1"] = month.split("-");
  return new Date(Date.UTC(Number(year), Number(value) - 1 + offset, 1)).toISOString().slice(0, 7);
}
export function ForecastGrid(props: {
  api: DashboardApi;
  entityId: string;
  subject: string;
  onAccessDenied(): void;
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
  const resource = useResource(`${props.subject}:${props.entityId}:grid:${start}`, load);
  const action = useAction(`${props.subject}:${props.entityId}:grid:generate`);
  return (
    <section aria-label="Invoice and bill forecast">
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
        name appear together. A dash means no amount for that contact in a saved month.
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
          aria-label="Invoice and bill forecast table"
          tabIndex={0}
        >
          <table className="forecast-grid-table">
            <caption>Invoice and bill forecast · NZD</caption>
            <thead>
              <tr>
                <th scope="col">Contact</th>
                {resource.data.months.map((month) => (
                  <th scope="col" key={month.month}>
                    {monthName(month.month)}
                    {!month.available ? <span className="supporting"> · Not created</span> : null}
                    {month.generatedAt ? (
                      <span className="supporting">
                        {" "}
                        · Saved{" "}
                        {new Date(month.generatedAt).toLocaleDateString("en-NZ", {
                          timeZone: "Pacific/Auckland",
                        })}
                      </span>
                    ) : null}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(["IN", "OUT"] as const).map((direction) => (
                <FragmentRows key={direction} direction={direction} grid={resource.data!} />
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
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
          {direction === "IN" ? "Customer invoices" : "Supplier bills"}
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
                  ? "Not created"
                  : row.amounts[month.month]
                    ? formatMoney(row.amounts[month.month], "NZD")
                    : "—"}
              </td>
            ))}
          </tr>
        ))
      )}
    </>
  );
}
