import { useCallback } from "react";
import type { DashboardApi } from "../api/dashboard";
import type { Entity } from "../api/contracts";
import { isAccessFailure } from "../api/client";
import { useResource } from "../api/useResource";
import { Loading, Failure, Notice } from "../ui/Feedback";
import { ForecastTable } from "./ForecastTable";
export function CombinedForecast({
  api,
  entities,
  month,
  subject,
  onAccessDenied,
}: {
  api: DashboardApi;
  entities: Entity[];
  month: string;
  subject: string;
  onAccessDenied(): void;
}) {
  const ids = entities
    .map((e) => e.id)
    .sort()
    .join(",");
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.combinedForecast(ids.split(","), month, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, ids, month, onAccessDenied],
  );
  const resource = useResource(`${subject}:${ids}:${month}:combined`, load, entities.length > 0);
  return (
    <section>
      <h2>All entities</h2>
      <p>
        Contributing entities: {entities.map((e) => e.displayName).join(", ") || "None available"}.
        Choose a specific entity to make edits.
      </p>
      {resource.status === "loading" ? <Loading label="Loading combined forecast…" /> : null}
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {resource.data ? (
        <>
          {!resource.data.complete ? (
            <Notice tone="warning">
              Incomplete totals: at least one entity needs an opening balance or a successful,
              current sync.
            </Notice>
          ) : null}
          <ForecastTable months={resource.data.months} />
          {resource.data.entities.map((entity) => (
            <details className="forecast-panel" key={entity.entityId}>
              <summary>
                {entity.entityName}
                {!entity.complete ? " · Incomplete" : ""}
              </summary>
              <p>{entity.exceptions.join(", ") || "No exceptions"}</p>
              <ForecastTable months={entity.months} />
            </details>
          ))}
        </>
      ) : null}
    </section>
  );
}
