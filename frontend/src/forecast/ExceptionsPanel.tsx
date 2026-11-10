import type { ForecastView } from "./contracts";
export function ExceptionsPanel({
  exceptions,
  onOpening,
  onReview,
}: {
  exceptions: ForecastView["exceptions"];
  onOpening(): void;
  onReview(id: string): void;
}) {
  if (!exceptions.length) return null;
  return (
    <section className="forecast-panel" aria-label="Forecast needs attention">
      <h2>Needs attention</h2>
      <ul className="forecast-issues">
        {exceptions.map((issue, i) => (
          <li key={`${issue.kind}:${issue.documentId ?? i}`}>
            {issue.message}
            {issue.kind === "NO_OPENING_BALANCE" ? (
              <button type="button" onClick={onOpening}>
                Enter opening balance
              </button>
            ) : null}
            {issue.kind === "DATE_CONFLICT" && issue.documentId ? (
              <button type="button" onClick={() => onReview(issue.documentId!)}>
                Review schedule
              </button>
            ) : null}
          </li>
        ))}
      </ul>
    </section>
  );
}
