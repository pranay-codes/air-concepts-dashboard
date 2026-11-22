import { useCallback } from "react";
import { ApiError, apiMessage, isAccessFailure } from "../api/client";
import type { Entity, GreenLineSyncStatus, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { formatFetchTime } from "../ui/format";

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
  const show = (name: string, system: GreenLineSyncStatus["xero"]) => (
    <div>
      <p>
        {system.lastSuccess?.finishedAt
          ? `${name} last synced ${formatFetchTime(system.lastSuccess.finishedAt)}.`
          : `${name} has not synced yet.`}
      </p>
      {system.latest?.status === "FAILED" ? (
        <p>
          {name} refresh did not complete.{" "}
          {system.lastSuccess?.finishedAt
            ? "The last successful time is shown above."
            : "No successful refresh has been recorded."}
        </p>
      ) : null}
      {system.latest?.status === "RUNNING" ? <p>{name} refresh is running.</p> : null}
    </div>
  );
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
  return (
    <section aria-label={`${entity.displayName} sync status`}>
      {status.status === "loading" ? <p role="status">Loading sync status…</p> : null}
      {status.status === "error" ? (
        <div>
          <p>Sync status is unavailable. Try again.</p>
          <button type="button" onClick={status.refresh}>
            Retry sync status
          </button>
        </div>
      ) : null}
      {status.data ? (
        <>
          {show("Xero", status.data.xero)}
          {show("vWork", status.data.vwork)}
        </>
      ) : null}
      {action.error ? (
        <p role="alert">
          {action.error instanceof ApiError && action.error.status === 403
            ? "Only a Director, Financial Admin or the designated Service Manager can refresh."
            : apiMessage(action.error)}
        </p>
      ) : null}
      <button type="button" onClick={refresh} disabled={action.busy}>
        {action.busy ? "Refreshing sync…" : "Refresh sync"}
      </button>
    </section>
  );
}
