import { useCallback, useEffect, useRef, useState } from "react";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatDate, formatFetchTime, formatMoney } from "../ui/format";
import { monthName, signClass } from "./ForecastTable";
import { TransactionNoteEditor } from "./TransactionNoteEditor";
import type { Contribution, ForecastMonth } from "./contracts";
import { type ForecastContext, handleAccess } from "./context";
function rowLabel(item: Contribution): string {
  const base = item.number ? `${item.number} · ${item.label}` : item.label;
  return item.rule === "SPLIT"
    ? `${base} (split ${item.splitSequence} of ${item.splitCount})`
    : base;
}
function rowSubtitle(item: Contribution): string {
  const when = formatDate(item.date);
  if (item.overdue) return `Was due ${when} (overdue)`;
  return item.category === "ACCPAY" ? `Bill due ${when}` : `Due ${when}`;
}
/** Adds plain two-decimal amount strings using integer cents, avoiding float rounding. */
function sumMoney(values: string[]): string {
  const cents = values.reduce((total, value) => {
    const negative = value.startsWith("-");
    const [whole = "0", fraction = "0"] = value.replace("-", "").split(".");
    const amount = Number(whole) * 100 + Number(fraction.padEnd(2, "0").slice(0, 2));
    return total + (negative ? -amount : amount);
  }, 0);
  const sign = cents < 0 ? "-" : "";
  const abs = Math.abs(cents);
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, "0")}`;
}
/** A note belongs to the transaction, so every split row of one invoice shares it. */
const transactionKey = (item: Contribution) => `${item.source}:${item.sourceId}`;
/**
 * Full-page planning view for one forecast month: the month's totals, then expected money in and
 * money out, one row per Xero invoice or bill. Manual items and staffing are shown on their own
 * tabs for now. Individual invoices are adjusted from the Xero items tab, not from here.
 */
export function MonthDetail(
  props: ForecastContext & {
    selectedMonth: string;
    summary: ForecastMonth | null;
    onBack(): void;
  },
) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => heading.current?.focus(), []);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await props.api.forecastItems(props.entity.id, props.selectedMonth, signal);
      } catch (error) {
        if (!signal.aborted) handleAccess(props.onAccessDenied, error);
        throw error;
      }
    },
    [props.api, props.entity.id, props.selectedMonth, props.onAccessDenied],
  );
  const resource = useResource(
    `${props.subject}:${props.entity.id}:${props.financialYear}:${props.selectedMonth}:items`,
    load,
  );
  // Notes saved in this view override the loaded notes. They are tied to the loaded data they were
  // saved against, so a reload of the month (a new data object) drops them in favour of the server.
  const [saves, setSaves] = useState<{
    base: unknown;
    notes: Record<string, string | null>;
  }>({ base: null, notes: {} });
  const savedNotes = saves.base === resource.data ? saves.notes : {};
  const loadedNotes = new Map(
    (resource.data?.notes ?? []).map((note) => [`${note.source}:${note.sourceId}`, note.note]),
  );
  function noteFor(item: Contribution): string | null {
    const key = transactionKey(item);
    return key in savedNotes ? (savedNotes[key] ?? null) : (loadedNotes.get(key) ?? null);
  }
  async function saveNote(item: Contribution, text: string) {
    try {
      const saved = await props.api.saveForecastTransactionNote(
        props.entity.id,
        item.source,
        item.sourceId,
        text,
      );
      const base = resource.data;
      setSaves((current) => ({
        base,
        notes: {
          ...(current.base === base ? current.notes : {}),
          [transactionKey(item)]: saved.note ?? null,
        },
      }));
    } catch (error) {
      handleAccess(props.onAccessDenied, error);
      throw error;
    }
  }
  // Scoped to Xero invoices and bills for now; manual items, staffing and other fixed costs will
  // be added to this ledger in a later slice. The totals above remain the full forecast totals.
  const items = [...(resource.data?.items ?? [])]
    .filter((item) => item.source === "DOCUMENT")
    .sort((a, b) => a.date.localeCompare(b.date));
  const name = monthName(props.selectedMonth);
  const summary = props.summary;
  // Opening and closing balances take the sign colour; money in and money out stay in the text colour.
  const figures: [string, string | null, boolean][] = summary
    ? [
        ["Opening balance", summary.opening, true],
        ["Money In", summary.receipts, false],
        ["Money Out", summary.payments, false],
        ["Closing balance", summary.closing, true],
      ]
    : [];
  return (
    <section className="forecast-month-page" aria-labelledby="forecast-month-heading">
      <button type="button" onClick={props.onBack}>
        Back to forecast
      </button>
      <h2 id="forecast-month-heading" ref={heading} tabIndex={-1}>
        {name} cash forecast
      </h2>
      {props.summary?.state === "GENERATED" ? (
        <p className="supporting">
          Generated {formatFetchTime(props.summary.generatedAt)}. Lines are frozen; later Xero
          changes do not alter them.
        </p>
      ) : null}
      {props.summary?.state === "PREVIEW" ? (
        <p className="supporting">
          Preview. This month is not generated yet, so these figures can still change.
        </p>
      ) : null}
      {props.summary?.state === "NOT_GENERATED" ? (
        <p className="supporting">Not generated. No figures are shown for this month.</p>
      ) : null}
      {summary ? (
        <dl className="forecast-summary-strip" aria-label={`${name} totals`}>
          {figures.map(([label, value, signed]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd className={`money ${signed ? (signClass(value) ?? "") : ""}`.trim()}>
                {formatMoney(value ?? null, "NZD")}
              </dd>
            </div>
          ))}
        </dl>
      ) : null}
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {resource.status === "loading" ? <Loading label="Loading month details…" /> : null}
      {resource.data
        ? (["IN", "OUT"] as const).map((direction) => {
            const rows = items.filter((item) => item.direction === direction);
            const title = direction === "IN" ? "Money In" : "Money Out";
            const total = rows.length ? sumMoney(rows.map((item) => item.amount)) : null;
            return (
              <section key={direction} aria-labelledby={`forecast-${direction}-heading`}>
                <div
                  className="forecast-ledger-region table-region"
                  role="region"
                  aria-labelledby={`forecast-${direction}-heading`}
                  tabIndex={0}
                >
                  <table className="forecast-ledger forecast-ledger-cards">
                    <caption id={`forecast-${direction}-heading`}>{title}</caption>
                    <thead>
                      <tr>
                        <th scope="col">Transaction</th>
                        <th scope="col">Notes</th>
                        <th scope="col" className="money">
                          Amount
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.length === 0 ? (
                        <tr>
                          <td colSpan={3}>
                            <Notice>
                              No expected {direction === "IN" ? "money in" : "money out"} for this
                              month.
                            </Notice>
                          </td>
                        </tr>
                      ) : (
                        rows.map((item) => (
                          <tr key={`${item.sourceId}:${item.splitSequence ?? "single"}`}>
                            <th scope="row">
                              <span className="forecast-ledger-label">{rowLabel(item)}</span>
                              <span className="forecast-muted">{rowSubtitle(item)}</span>
                            </th>
                            <td className="note-column">
                              <TransactionNoteEditor
                                label={rowLabel(item)}
                                note={noteFor(item)}
                                onSave={(text) => saveNote(item, text)}
                              />
                            </td>
                            <td className="money">{formatMoney(item.amount, "NZD")}</td>
                          </tr>
                        ))
                      )}
                    </tbody>
                    {total !== null ? (
                      <tfoot>
                        <tr>
                          <th scope="row">Total {title}</th>
                          <td />
                          <td className="money">{formatMoney(total, "NZD")}</td>
                        </tr>
                      </tfoot>
                    ) : null}
                  </table>
                </div>
              </section>
            );
          })
        : null}
      <p className="forecast-muted">
        Closing balance = opening balance + money in − money out. Overdue invoices stay in the
        current month; drafts are excluded. These tables list Xero invoices and bills; manual items
        and staffing are on the Manual items and Staffing tabs, and will be added here in a later
        update. To change an invoice's expected date, split it or exclude it, use the Xero items
        tab. Notes are kept in this application and are never sent to Xero.
      </p>
    </section>
  );
}
