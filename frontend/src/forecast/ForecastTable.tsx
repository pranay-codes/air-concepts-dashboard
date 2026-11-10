import type { ForecastMonth } from "./contracts";
import { formatMoney } from "../ui/format";
import "./forecast.css";
export function monthName(value: string) {
  return new Intl.DateTimeFormat("en-NZ", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}-01T00:00:00Z`));
}
export function ForecastTable({
  months,
  onOpen,
  onOpening,
}: {
  months: ForecastMonth[];
  onOpen?: (month: string) => void;
  onOpening?: () => void;
}) {
  const amount = (value: string | null) => formatMoney(value, "NZD");
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
              {["Month", "Opening", "Receipts", "Payments", "Net", "Closing"].map((h) => (
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
                <td className="money">
                  {amount(month.opening)}
                  {month.opening === null && onOpening ? (
                    <button type="button" onClick={onOpening}>
                      Enter opening balance
                    </button>
                  ) : null}
                </td>
                <td className="money">{amount(month.receipts)}</td>
                <td className="money">{amount(month.payments)}</td>
                <td className="money">{amount(month.net)}</td>
                <td className="money">
                  {amount(month.closing)} {warning(month)}
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
            <dl>
              {[
                ["Opening", month.opening],
                ["Receipts", month.receipts],
                ["Payments", month.payments],
                ["Net", month.net],
                ["Closing", month.closing],
              ].map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{amount(value ?? null)}</dd>
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
