import { useCallback, useState } from "react";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatFetchTime } from "../ui/format";
import { type ForecastContext, handleAccess } from "./context";
export function History(props: ForecastContext & { revision: number }) {
  const [page, setPage] = useState(1);
  const loadSync = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await props.api.forecastSyncHistory(props.entity.id, signal);
      } catch (error) {
        if (!signal.aborted) handleAccess(props.onAccessDenied, error);
        throw error;
      }
    },
    [props.api, props.entity.id, props.onAccessDenied],
  );
  const loadAudit = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await props.api.forecastAudit(props.entity.id, page, signal);
      } catch (error) {
        if (!signal.aborted) handleAccess(props.onAccessDenied, error);
        throw error;
      }
    },
    [props.api, props.entity.id, page, props.onAccessDenied],
  );
  const sync = useResource(`${props.subject}:${props.entity.id}:sync:${props.revision}`, loadSync);
  const audit = useResource(
    `${props.subject}:${props.entity.id}:audit:${page}:${props.revision}`,
    loadAudit,
  );
  return (
    <section>
      <h2>Sync and change history</h2>
      <h3>Last 10 sync runs</h3>
      {sync.status === "loading" ? <Loading /> : null}
      {sync.error ? <Failure error={sync.error} onRetry={sync.refresh} /> : null}
      {sync.data?.length === 0 ? (
        <Notice>No sync has run yet. Use Sync now to load Xero documents.</Notice>
      ) : null}
      {sync.data?.map((run) => (
        <article className="forecast-card" key={run.id}>
          <strong>{run.status}</strong>
          <p>
            {run.trigger === "SCHEDULED" ? "Scheduled" : "Manual"} ·{" "}
            {formatFetchTime(run.startedAt)}
          </p>
          <p>
            Seen {run.documentsSeen}; inserted {run.documentsInserted}; updated{" "}
            {run.documentsUpdated}; settled {run.documentsSettled}; conflicts {run.conflictsFlagged}
            .
          </p>
          {run.errorCode ? <p className="notice warning">{run.errorCode}</p> : null}
          <p className="supporting">
            Remaining Xero calls: day {run.dayLimitRemaining ?? "Unknown"}, minute{" "}
            {run.minuteLimitRemaining ?? "Unknown"}, application minute{" "}
            {run.appMinuteLimitRemaining ?? "Unknown"}.
          </p>
        </article>
      ))}
      <h3>Changed fields</h3>
      {audit.status === "loading" ? <Loading /> : null}
      {audit.error ? <Failure error={audit.error} onRetry={audit.refresh} /> : null}
      {audit.data?.items.length === 0 ? <Notice>No forecast edits recorded.</Notice> : null}
      {audit.data?.items.map((row) => (
        <details className="forecast-card" key={row.id}>
          <summary>
            {row.action} · {row.recordTable?.replace("forecast_", "").replaceAll("_", " ")} ·{" "}
            {formatFetchTime(row.at)}
          </summary>
          <p>Actor: {row.actorId ?? "Scheduled sync"}</p>
          <pre className="forecast-audit">
            {JSON.stringify({ before: row.before, after: row.after }, null, 2)}
          </pre>
        </details>
      ))}
      {audit.data ? (
        <div className="forecast-actions">
          <p>
            Page {page} · {audit.data.itemCount} records
          </p>
          <button type="button" disabled={page === 1} onClick={() => setPage((p) => p - 1)}>
            Previous audit page
          </button>
          <button
            type="button"
            disabled={page * audit.data.pageSize >= audit.data.itemCount}
            onClick={() => setPage((p) => p + 1)}
          >
            Next audit page
          </button>
        </div>
      ) : null}
    </section>
  );
}
