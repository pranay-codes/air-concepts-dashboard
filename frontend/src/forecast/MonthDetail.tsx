import { useCallback, useEffect, useRef, useState } from "react";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatMoney, formatDate } from "../ui/format";
import { DocumentEditor, type AdjustmentMode } from "./AdjustmentDialog";
import { monthName } from "./ForecastTable";
import type { ForecastMonth } from "./contracts";
import { type ForecastContext, handleAccess } from "./context";
const ruleNames = {
  SPLIT: "Split schedule",
  OVERRIDE: "Dashboard override",
  XERO_EXPECTED_DATE: "Xero expected date",
  DUE_DATE: "Due date",
  ISSUE_DATE: "Invoice date",
  MANUAL: "Manual item",
  STAFFING: "Staffing",
};
const actionLabels = [
  ["date", "Change date"],
  ["split", "Split"],
  ["exclude", "Exclude"],
] as const;
/** Full-page planning view for one forecast month: monthly totals, then receipts and payments as tables. */
export function MonthDetail(
  props: ForecastContext & {
    selectedMonth: string;
    summary: ForecastMonth | null;
    onBack(): void;
  },
) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => heading.current?.focus(), []);
  const [edit, setEdit] = useState<{ id: string; mode: AdjustmentMode } | null>(null);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await props.api.forecastItems(
          props.entity.id,
          props.selectedMonth,
          props.month,
          signal,
        );
      } catch (error) {
        if (!signal.aborted) handleAccess(props.onAccessDenied, error);
        throw error;
      }
    },
    [props.api, props.entity.id, props.month, props.selectedMonth, props.onAccessDenied],
  );
  const resource = useResource(
    `${props.subject}:${props.entity.id}:${props.month}:${props.selectedMonth}:items`,
    load,
  );
  const items = [...(resource.data?.items ?? [])].sort((a, b) => a.date.localeCompare(b.date));
  const name = monthName(props.selectedMonth);
  const summary = props.summary;
  const figures = summary
    ? [
        ["Opening", summary.opening],
        ["Receipts", summary.receipts],
        ["Payments", summary.payments],
        ["Net", summary.net],
        ["Closing", summary.closing],
      ]
    : [];
  return (
    <section className="forecast-month-page" aria-labelledby="forecast-month-heading">
      <button type="button" onClick={props.onBack}>
        Back to forecast
      </button>
      <h2 id="forecast-month-heading" ref={heading} tabIndex={-1}>
        {name} cash detail
      </h2>
      {summary ? (
        <dl className="forecast-summary-strip" aria-label={`${name} totals`}>
          {figures.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd className="money">{formatMoney(value ?? null, "NZD")}</dd>
            </div>
          ))}
        </dl>
      ) : null}
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {resource.status === "loading" ? <Loading label="Loading month details…" /> : null}
      {resource.data
        ? (["IN", "OUT"] as const).map((direction) => {
            const rows = items.filter((item) => item.direction === direction);
            const title = direction === "IN" ? "Receipts" : "Payments";
            const total = direction === "IN" ? summary?.receipts : summary?.payments;
            return (
              <section key={direction} aria-labelledby={`forecast-${direction}-heading`}>
                <h3 id={`forecast-${direction}-heading`}>{title}</h3>
                {rows.length === 0 ? (
                  <Notice>
                    No expected {direction === "IN" ? "receipts" : "payments"} for this month.
                  </Notice>
                ) : (
                  <div
                    className="forecast-ledger-region table-region"
                    role="region"
                    aria-labelledby={`forecast-${direction}-heading`}
                    tabIndex={0}
                  >
                    <table className="forecast-ledger">
                      <caption>
                        {title} · {name} · NZD, GST-inclusive
                      </caption>
                      <thead>
                        <tr>
                          <th scope="col">Date</th>
                          <th scope="col">Invoice or item</th>
                          <th scope="col">Contact</th>
                          <th scope="col">Basis</th>
                          <th scope="col" className="money">
                            Amount (NZD)
                          </th>
                          <th scope="col">Status</th>
                          <th scope="col">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {rows.map((item) => {
                          const label = item.number ?? item.label;
                          return (
                            <tr key={`${item.sourceId}:${item.splitSequence ?? "single"}`}>
                              <td>{formatDate(item.date)}</td>
                              <th scope="row">{label}</th>
                              <td>{item.number ? item.label : "—"}</td>
                              <td>{ruleNames[item.rule]}</td>
                              <td className="money">{formatMoney(item.amount, "NZD")}</td>
                              <td>
                                {item.rule === "SPLIT" ? (
                                  <span className="badge information">
                                    Split {item.splitSequence} of {item.splitCount}
                                  </span>
                                ) : null}
                                {item.overdue ? (
                                  <span className="badge warning">Overdue</span>
                                ) : null}
                              </td>
                              <td>
                                {item.source === "DOCUMENT" ? (
                                  <div className="forecast-row-actions">
                                    {actionLabels.map(([mode, action]) => (
                                      <button
                                        key={mode}
                                        type="button"
                                        aria-label={`${action} for ${label}`}
                                        onClick={() => setEdit({ id: item.sourceId, mode })}
                                      >
                                        {action}
                                      </button>
                                    ))}
                                  </div>
                                ) : null}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                      {total !== undefined && total !== null ? (
                        <tfoot>
                          <tr>
                            <th scope="row" colSpan={4}>
                              Total {title.toLowerCase()}
                            </th>
                            <td className="money">{formatMoney(total, "NZD")}</td>
                            <td colSpan={2} />
                          </tr>
                        </tfoot>
                      ) : null}
                    </table>
                  </div>
                )}
              </section>
            );
          })
        : null}
      {edit ? (
        <DocumentEditor
          {...props}
          documentId={edit.id}
          mode={edit.mode}
          onClose={() => setEdit(null)}
        />
      ) : null}
    </section>
  );
}
