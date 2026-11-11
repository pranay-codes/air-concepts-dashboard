import type { ForecastMonth, ForecastMonthState, OpeningSource } from "./contracts";
import { amountSign, formatMoney } from "../ui/format";
import "./forecast.css";

export function monthName(value: string) {
  return new Intl.DateTimeFormat("en-NZ", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}-01T00:00:00Z`));
}

const stateLabel: Record<ForecastMonthState, string> = {
  GENERATED: "Generated",
  PREVIEW: "Preview",
  NOT_GENERATED: "Not generated",
};

/** Entered openings are unlabelled; only overrides and carried-forward openings name their source. */
const openingSourceLabel: Record<Exclude<OpeningSource, "MANUAL">, string> = {
  OVERRIDE: "Override",
  CARRIED_FORWARD: "Carried forward",
};

/** Colour class for a financial amount, decided from its digits: positive green, negative red, zero or missing neutral. */
function signClass(value: string | null | undefined) {
  const sign = amountSign(value);
  return sign > 0 ? "amount-positive" : sign < 0 ? "amount-negative" : undefined;
}

/**
 * Figures for a month that has no forecast are described, never shown as zero (AC-9, FR-UC04-23).
 * The visible dash is hidden from assistive technology; the visually hidden text carries the meaning.
 */
function FigureCell({
  month,
  value,
  signed = false,
}: {
  month: ForecastMonth;
  value: string | null;
  signed?: boolean;
}) {
  if (month.state === "NOT_GENERATED")
    return (
      <>
        <span aria-hidden="true">—</span>
        <span className="visually-hidden">Not generated</span>
      </>
    );
  const text = formatMoney(value, "NZD");
  const tone = signed ? signClass(value) : undefined;
  return tone ? <span className={tone}>{text}</span> : <>{text}</>;
}

const columns = [
  { label: "Month", numeric: false },
  { label: "Status", numeric: false },
  { label: "Opening balance", numeric: true },
  { label: "Money In", numeric: true },
  { label: "Money Out", numeric: true },
  { label: "Net", numeric: true },
  { label: "Closing balance", numeric: true },
  { label: "Action", numeric: false },
] as const;

function StateBadge({ month }: { month: ForecastMonth }) {
  const tone = month.state === "GENERATED" ? "success" : month.state === "PREVIEW" ? "info" : "";
  return <span className={`badge ${tone}`.trim()}>{stateLabel[month.state]}</span>;
}

/** A carried opening that follows a preview month rests on figures that can still change until that month is generated (D13). */
function isProvisional(months: ForecastMonth[], index: number) {
  return (
    months[index]?.openingSource === "CARRIED_FORWARD" && months[index - 1]?.state === "PREVIEW"
  );
}

/** Opening amount, its source, and an Edit control that is always present when editing is allowed. */
function OpeningCell({
  month,
  onOpening,
  provisional,
}: {
  month: ForecastMonth;
  onOpening?: (month: ForecastMonth) => void;
  provisional: boolean;
}) {
  const source =
    provisional || !month.openingSource || month.openingSource === "MANUAL"
      ? null
      : openingSourceLabel[month.openingSource];
  return (
    <div className="opening-cell">
      <span className={`opening-amount ${signClass(month.opening) ?? ""}`.trim()}>
        {month.opening === null ? "Not set" : formatMoney(month.opening, "NZD")}
      </span>
      {source ? <span className="supporting opening-source">{source}</span> : null}
      {onOpening ? (
        <button
          type="button"
          className="compact-button"
          aria-label={`Edit opening balance for ${monthName(month.month)}`}
          onClick={() => onOpening(month)}
        >
          Edit
        </button>
      ) : null}
    </div>
  );
}

/** Generate or add-items action for a month, or a plain note when no action is possible. */
function MonthAction({
  month,
  onGenerate,
}: {
  month: ForecastMonth;
  onGenerate?: (month: ForecastMonth) => void;
}) {
  const items = month.pendingNewItems;
  const noun = items === 1 ? "item" : "items";
  if (month.state === "PREVIEW" && month.generatable && onGenerate)
    return (
      <button
        type="button"
        className="compact-button primary"
        aria-label={`Generate forecast for ${monthName(month.month)}`}
        onClick={() => onGenerate(month)}
      >
        Generate
      </button>
    );
  if (month.state === "GENERATED" && month.generatable && items > 0 && onGenerate)
    return (
      <button type="button" className="compact-button" onClick={() => onGenerate(month)}>
        Add {items} new {noun}
      </button>
    );
  if (month.state === "GENERATED" && !month.generatable && items > 0)
    return (
      <span className="supporting">
        Locked: {items} {noun} not in this forecast
      </span>
    );
  if (month.state === "GENERATED") return <span className="supporting">Frozen</span>;
  return null;
}

export function ForecastTable({
  months,
  onOpen,
  onOpening,
  onGenerate,
}: {
  months: ForecastMonth[];
  onOpen?: (month: string) => void;
  onOpening?: (month: ForecastMonth) => void;
  onGenerate?: (month: ForecastMonth) => void;
}) {
  const warning = (month: ForecastMonth) =>
    month.closing?.startsWith("-") ? (
      <span className="badge warning">Negative closing balance</span>
    ) : null;
  return (
    <>
      <div
        className="forecast-desktop table-region"
        role="region"
        aria-label="12-month forecast"
        tabIndex={0}
      >
        <table className="forecast-table">
          <caption>12-month cash forecast · NZD, GST-inclusive</caption>
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column.label} scope="col" className={column.numeric ? "money" : undefined}>
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {months.map((month, index) => (
              <tr key={month.month} data-state={month.state}>
                <th scope="row">
                  {onOpen ? (
                    <button
                      type="button"
                      className="link-button"
                      onClick={() => onOpen(month.month)}
                    >
                      View {monthName(month.month)}
                    </button>
                  ) : (
                    monthName(month.month)
                  )}
                </th>
                <td>
                  <StateBadge month={month} />
                  {month.containsOverdue ? (
                    <>
                      {" "}
                      <span className="badge warning">Includes overdue cash</span>
                    </>
                  ) : null}
                </td>
                <td className="money">
                  <OpeningCell
                    month={month}
                    onOpening={onOpening}
                    provisional={isProvisional(months, index)}
                  />
                </td>
                <td className="money">
                  <FigureCell month={month} value={month.receipts} />
                </td>
                <td className="money">
                  <FigureCell month={month} value={month.payments} />
                </td>
                <td className="money total">
                  <FigureCell month={month} value={month.net} signed />
                </td>
                <td className="money total">
                  <FigureCell month={month} value={month.closing} signed /> {warning(month)}
                </td>
                <td>
                  <MonthAction month={month} onGenerate={onGenerate} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {months.some((month) => month.state === "NOT_GENERATED") ? (
        <p className="forecast-muted table-note">
          — Not generated: figures appear once the month is generated.
        </p>
      ) : null}
    </>
  );
}
