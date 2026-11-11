import { formatFetchTime } from "../ui/format";

/** Calendar month of the current time in New Zealand, as YYYY-MM. */
export function currentMonth() {
  const parts = new Intl.DateTimeFormat("en-NZ", {
    timeZone: "Pacific/Auckland",
    year: "numeric",
    month: "2-digit",
  }).formatToParts(new Date());
  return `${parts.find((p) => p.type === "year")?.value}-${parts.find((p) => p.type === "month")?.value}`;
}

/**
 * Page header. The financial year is chosen with previous and next controls; the label comes from the
 * server, so the screen never guesses the year boundary (FR-UC04-22).
 */
export function ForecastHeader(props: {
  entityName: string;
  financialYearLabel?: string | null;
  onStep?(delta: number): void;
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
        <div className="forecast-year" role="group" aria-label="Financial year">
          {props.onStep ? (
            <button type="button" onClick={() => props.onStep?.(-1)}>
              Previous year
            </button>
          ) : null}
          <p aria-live="polite">
            Financial year <strong>{props.financialYearLabel ?? "Loading…"}</strong>
          </p>
          {props.onStep ? (
            <button type="button" onClick={() => props.onStep?.(1)}>
              Next year
            </button>
          ) : null}
        </div>
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
