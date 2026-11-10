import { useCallback, useEffect, useRef, useState } from "react";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatMoney, formatDate } from "../ui/format";
import { DocumentEditor, type AdjustmentMode } from "./AdjustmentDialog";
import { monthName } from "./ForecastTable";
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
export function MonthDetail(props: ForecastContext & { selectedMonth: string; onBack(): void }) {
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
  const items = resource.data?.items ?? [];
  return (
    <section
      className="forecast-panel forecast-detail"
      aria-label={`Details for ${monthName(props.selectedMonth)}`}
    >
      <button type="button" onClick={props.onBack}>
        Back to forecast
      </button>
      <h2 ref={heading} tabIndex={-1}>
        {monthName(props.selectedMonth)}
      </h2>
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {resource.status === "loading" ? <Loading label="Loading month details…" /> : null}
      {resource.data
        ? (["IN", "OUT"] as const).map((direction) => (
            <section key={direction}>
              <h3>{direction === "IN" ? "Receipts" : "Payments"}</h3>
              {!items.some((i) => i.direction === direction) ? (
                <Notice>
                  No expected {direction === "IN" ? "receipts" : "payments"} for this month.
                </Notice>
              ) : null}
              {items
                .filter((i) => i.direction === direction)
                .map((item) => (
                  <article
                    className="forecast-card"
                    key={`${item.sourceId}:${item.splitSequence ?? "single"}`}
                  >
                    <strong>{item.number ?? item.label}</strong>
                    {item.number ? <p>{item.label}</p> : null}
                    <p>
                      {formatDate(item.date)} · {formatMoney(item.amount, "NZD")}
                    </p>
                    <span className="badge information">
                      {item.rule === "SPLIT"
                        ? `Split ${item.splitSequence} of ${item.splitCount}`
                        : ruleNames[item.rule]}
                    </span>
                    {item.overdue ? <span className="badge warning">Overdue</span> : null}
                    {item.source === "DOCUMENT" ? (
                      <div className="forecast-row-actions">
                        {(
                          [
                            ["date", "Change date"],
                            ["split", "Split"],
                            ["exclude", "Exclude"],
                          ] as const
                        ).map(([mode, label]) => (
                          <button
                            key={mode}
                            type="button"
                            onClick={() => setEdit({ id: item.sourceId, mode })}
                          >
                            {label}
                          </button>
                        ))}
                      </div>
                    ) : null}
                  </article>
                ))}
            </section>
          ))
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
