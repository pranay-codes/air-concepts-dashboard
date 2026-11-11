const syncFormat = new Intl.DateTimeFormat("en-NZ", {
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
  timeZone: "Pacific/Auckland",
});

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
 * Xero sync time shown on this page, e.g. "11 Nov 2026, 2:16 pm". The instant is still converted in
 * Pacific/Auckland; only the zone name is left out of the visible text.
 */
export function formatSyncTime(date: Date): string {
  return syncFormat.format(date).replace(/\s+/g, " ");
}

/** Returns the sync instant, or null when it is missing or not a valid date. */
export function parseSyncTime(value: string | null | undefined): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date : null;
}

/** Financial years arrive as 2026-2027 and are shown with an en dash. */
export function displayFinancialYear(label: string): string {
  return label.replace(/^(\d{4})-(\d{4})$/, "$1–$2");
}

function Chevron(props: { direction: "left" | "right" }) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={props.direction === "left" ? "M15 6l-6 6 6 6" : "M9 6l6 6-6 6"} />
    </svg>
  );
}

/**
 * Previous and next financial-year controls with the selected year between them. The label comes from
 * the server, so the screen never guesses the year boundary (FR-UC04-22).
 */
export function FinancialYearNav(props: {
  label?: string | null;
  canStep: boolean;
  onStep(delta: number): void;
}) {
  return (
    <div className="fy-nav" role="group" aria-label="Financial year">
      <button
        type="button"
        className="fy-nav__step"
        aria-label="Previous financial year"
        data-tooltip="Previous financial year"
        disabled={!props.canStep}
        onClick={() => props.onStep(-1)}
      >
        <Chevron direction="left" />
      </button>
      <span className="fy-nav__label" aria-live="polite">
        {props.label ? displayFinancialYear(props.label) : "Loading…"}
      </span>
      <button
        type="button"
        className="fy-nav__step"
        aria-label="Next financial year"
        data-tooltip="Next financial year"
        disabled={!props.canStep}
        onClick={() => props.onStep(1)}
      >
        <Chevron direction="right" />
      </button>
    </div>
  );
}

/**
 * Page header: entity, period and currency with the Xero sync time underneath on the left; financial-year
 * navigation and page actions grouped on the right.
 */
export function ForecastHeader(props: {
  entityName: string;
  financialYearLabel?: string | null;
  /** Whether the year can be stepped from; defaults to true once the year label is known. */
  canStep?: boolean;
  onStep?(delta: number): void;
  lastSync?: string | null;
  stale?: boolean;
  busy?: boolean;
  onSync?(): void;
  onExport?(): void;
}) {
  const sync = parseSyncTime(props.lastSync);
  return (
    <header className="forecast-header">
      <div className="forecast-heading">
        <h1>Financial forecast</h1>
        <p>
          <strong>{props.entityName}</strong> · 12 months · NZD
        </p>
        {props.onSync ? (
          <p className="supporting forecast-sync">
            {sync ? (
              <>
                Xero data as at <time dateTime={sync.toISOString()}>{formatSyncTime(sync)}</time>
              </>
            ) : (
              "Xero sync time unknown"
            )}{" "}
            {props.stale ? <span className="badge warning">Stale or not synced</span> : null}
          </p>
        ) : null}
      </div>
      <div className="forecast-controls">
        {props.onStep ? (
          <FinancialYearNav
            label={props.financialYearLabel}
            canStep={props.canStep ?? Boolean(props.financialYearLabel)}
            onStep={props.onStep}
          />
        ) : null}
        {props.onSync || props.onExport ? (
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
        ) : null}
      </div>
    </header>
  );
}
