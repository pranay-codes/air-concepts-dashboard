import { formatFetchTime } from "../ui/format";
export function currentMonth() {
  const parts = new Intl.DateTimeFormat("en-NZ", {
    timeZone: "Pacific/Auckland",
    year: "numeric",
    month: "2-digit",
  }).formatToParts(new Date());
  return `${parts.find((p) => p.type === "year")?.value}-${parts.find((p) => p.type === "month")?.value}`;
}
export function earliestMonth() {
  const [year = "2026", month = "1"] = currentMonth().split("-");
  return new Date(Date.UTC(Number(year) - 1, Number(month) - 1, 1)).toISOString().slice(0, 7);
}
export function ForecastHeader(props: {
  entityName: string;
  month: string;
  onMonth(month: string): void;
  lastSync?: string | null;
  loadedAt?: Date | null;
  stale?: boolean;
  busy?: boolean;
  onSync?(): void;
  onExport?(): void;
}) {
  return (
    <header className="forecast-header">
      <div>
        <h1>Financial forecast</h1>
        <p>
          <strong>{props.entityName}</strong> · 12 months · NZD
        </p>
        <label>
          Forecast start month
          <input
            type="month"
            min={earliestMonth()}
            max={currentMonth()}
            value={props.month}
            required
            onChange={(e) => {
              if (e.target.value) props.onMonth(e.target.value);
            }}
          />
        </label>
        {props.onSync ? (
          <p>
            Xero data as at {formatFetchTime(props.lastSync)}{" "}
            {props.stale ? <span className="badge warning">Stale or not synced</span> : null}
          </p>
        ) : null}
        {props.loadedAt ? (
          <p className="supporting">
            Page loaded at {formatFetchTime(props.loadedAt.toISOString())}
          </p>
        ) : null}
      </div>
      <div className="forecast-actions">
        {props.onSync ? (
          <button type="button" disabled={props.busy} onClick={props.onSync}>
            {props.busy ? "Syncing…" : "Sync now"}
          </button>
        ) : null}
        {props.onExport ? (
          <button type="button" onClick={props.onExport}>
            Export to Excel
          </button>
        ) : null}
      </div>
    </header>
  );
}
