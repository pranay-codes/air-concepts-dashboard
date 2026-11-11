import { useCallback, useState } from "react";
import type { DashboardApi } from "../api/dashboard";
import { yearOf } from "../api/dashboard";
import type { Entity } from "../api/contracts";
import { isAccessFailure } from "../api/client";
import { useResource } from "../api/useResource";
import { Loading, Failure, Notice } from "../ui/Feedback";
import { ForecastTable } from "./ForecastTable";
import { ForecastHeader } from "./ForecastHeader";

/** Totals across the entities the user may see. Month totals and subtotals only; no document rows (FR-UC04-13). */
export function CombinedForecast({
  api,
  entities,
  subject,
  onAccessDenied,
}: {
  api: DashboardApi;
  entities: Entity[];
  subject: string;
  onAccessDenied(): void;
}) {
  const [requestedYear, setRequestedYear] = useState<number | null>(null);
  const ids = entities
    .map((e) => e.id)
    .sort()
    .join(",");
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.combinedForecast(ids.split(","), requestedYear, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, ids, requestedYear, onAccessDenied],
  );
  const resource = useResource(
    `${subject}:${ids}:${requestedYear ?? "current"}:combined`,
    load,
    entities.length > 0,
  );
  const loadedYear = resource.data ? yearOf(resource.data.financialYearStart) : requestedYear;
  return (
    <section>
      <ForecastHeader
        entityName="All entities"
        financialYearLabel={resource.data?.financialYearLabel ?? null}
        onStep={(delta) => {
          if (loadedYear !== null) setRequestedYear(loadedYear + delta);
        }}
      />
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
              Incomplete totals: at least one entity needs an opening balance, a successful, current
              sync, or a generated month.
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
