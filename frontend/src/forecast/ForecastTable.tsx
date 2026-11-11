import type { ForecastMonth, ForecastMonthState } from "./contracts";
import { formatMoney } from "../ui/format";
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

/** Figures for a month that has no forecast are described, never shown as zero (AC-9, FR-UC04-23). */
export function figure(month: ForecastMonth, value: string | null) {
  if (month.state === "NOT_GENERATED") return "Not generated";
  return formatMoney(value, "NZD");
}

function StateBadge({ month }: { month: ForecastMonth }) {
  const tone = month.state === "GENERATED" ? "success" : month.state === "PREVIEW" ? "info" : "";
  return <span className={`badge ${tone}`.trim()}>{stateLabel[month.state]}</span>;
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
      <button type="button" onClick={() => onGenerate(month)}>
        Generate forecast for {monthName(month.month)}
      </button>
    );
  if (month.state === "GENERATED" && month.generatable && items > 0 && onGenerate)
    return (
      <button type="button" onClick={() => onGenerate(month)}>
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
  onOpening?: () => void;
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
        <table>
          <caption>12-month cash forecast · NZD, GST-inclusive</caption>
          <thead>
            <tr>
              {[
                "Month",
                "Status",
                "Opening balance",
                "Money In",
                "Money Out",
                "Net",
                "Closing balance",
                "Action",
              ].map((h) => (
                <th key={h} scope="col">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {months.map((month) => (
              <tr key={month.month}>
                <th scope="row">
                  {onOpen ? (
                    <button type="button" onClick={() => onOpen(month.month)}>
                      View {monthName(month.month)}
                    </button>
                  ) : (
                    monthName(month.month)
                  )}
                </th>
                <td>
                  <StateBadge month={month} />
                </td>
                <td className="money">
                  {formatMoney(month.opening, "NZD")}
                  {month.opening === null && onOpening ? (
                    <button type="button" onClick={onOpening}>
                      Enter opening balance
                    </button>
                  ) : null}
                </td>
                <td className="money">{figure(month, month.receipts)}</td>
                <td className="money">{figure(month, month.payments)}</td>
                <td className="money">{figure(month, month.net)}</td>
                <td className="money">
                  {figure(month, month.closing)} {warning(month)}
                </td>
                <td>
                  <MonthAction month={month} onGenerate={onGenerate} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="forecast-mobile">
        {months.map((month) => (
          <article className="forecast-card" key={month.month}>
            <h3>{monthName(month.month)}</h3>
            <p>
              <StateBadge month={month} />
            </p>
            <dl>
              {(
                [
                  ["Opening balance", month.opening, false],
                  ["Money In", month.receipts, true],
                  ["Money Out", month.payments, true],
                  ["Net", month.net, true],
                  ["Closing balance", month.closing, true],
                ] as const
              ).map(([label, value, figured]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{figured ? figure(month, value) : formatMoney(value, "NZD")}</dd>
                </div>
              ))}
            </dl>
            {warning(month)}
            {month.containsOverdue ? (
              <span className="badge warning">Includes overdue cash</span>
            ) : null}
            {month.opening === null && onOpening ? (
              <button type="button" onClick={onOpening}>
                Enter opening balance
              </button>
            ) : null}
            <MonthAction month={month} onGenerate={onGenerate} />
            {onOpen ? (
              <button type="button" onClick={() => onOpen(month.month)}>
                View {monthName(month.month)}
              </button>
            ) : null}
          </article>
        ))}
      </div>
    </>
  );
}
