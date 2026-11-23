import { useCallback } from "react";
import { ApiError, apiMessage, isAccessFailure } from "../api/client";
import type { Entity, GreenLineSyncStatus, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { formatFetchTime } from "../ui/format";
import { syncView } from "./myWorkModel";

type SystemStatus = GreenLineSyncStatus["xero"];

export function SyncFreshness({
  api,
  entity,
  profile,
  onChanged,
  onAccessDenied,
}: {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  onChanged(): void;
  onAccessDenied(): void;
}) {
  const key = `${profile.subject}:${profile.role}:${entity.id}`;
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.greenLineSyncStatus(entity.id, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, onAccessDenied],
  );
  const status = useResource(`sync:${key}`, load);
  const action = useAction(`sync:${key}`);
  async function refresh() {
    const result = await action.run(
      (signal) => api.refreshGreenLine(entity.id, signal),
      (error) => {
        status.refresh();
        onChanged();
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) {
      status.refresh();
      onChanged();
    }
  }
  const views = status.data
    ? { xero: syncView(status.data.xero), vwork: syncView(status.data.vwork) }
    : null;
  const someOutOfDate =
    views !== null && (views.xero.label !== "Synced" || views.vwork.label !== "Synced");
  return (
    <section aria-label={`${entity.displayName} data sync`} className="mywork-sync">
      <div className="mywork-sync-row">
        {status.status === "loading" ? <p role="status">Loading sync status…</p> : null}
        {status.status === "error" ? (
          <div className="mywork-sync-item">
            <p>Sync status is unavailable. Try again.</p>
            <button type="button" onClick={status.refresh}>
              Retry sync status
            </button>
          </div>
        ) : null}
        {status.data && views ? (
          <>
            <SystemLine name="Xero" view={views.xero} />
            <SystemLine name="vWork" view={views.vwork} />
          </>
        ) : null}
        <button type="button" onClick={refresh} disabled={action.busy}>
          {action.busy ? "Refreshing sync…" : "Refresh sync"}
        </button>
      </div>
      {someOutOfDate ? (
        <p className="supporting">Some work may be out of date until both systems have synced.</p>
      ) : null}
      {action.error ? (
        <p role="alert">
          {action.error instanceof ApiError && action.error.status === 403
            ? "Only a Director, Financial Admin or the designated Service Manager can refresh."
            : apiMessage(action.error)}
        </p>
      ) : null}
      {status.data ? (
        <details className="mywork-details">
          <summary>Sync details</summary>
          <TechnicalDetails name="Xero" system={status.data.xero} />
          <TechnicalDetails name="vWork" system={status.data.vwork} />
        </details>
      ) : null}
    </section>
  );
}

function SystemLine({ name, view }: { name: string; view: ReturnType<typeof syncView> }) {
  return (
    <div role="group" aria-label={`${name} data`} className="mywork-sync-item">
      <span className="mywork-sync-name">{name}</span>
      <span className={`badge ${view.tone}`}>{view.label}</span>
      <span className="supporting">
        {view.lastSynced
          ? `Last successful sync ${formatFetchTime(view.lastSynced)}`
          : "No successful sync recorded"}
      </span>
    </div>
  );
}

function TechnicalDetails({ name, system }: { name: string; system: SystemStatus }) {
  const latest = system.latest;
  return (
    <div>
      <h3>{name}</h3>
      <ul>
        {latest ? (
          <li>
            Latest refresh: {latest.status.toLowerCase()}, started{" "}
            {formatFetchTime(latest.startedAt)}
            {latest.errorCode ? `. Error code ${latest.errorCode}` : ""}
          </li>
        ) : (
          <li>No refresh has been recorded.</li>
        )}
        {system.lastFailure?.finishedAt ? (
          <li>Last failed refresh finished {formatFetchTime(system.lastFailure.finishedAt)}</li>
        ) : null}
      </ul>
    </div>
  );
}
