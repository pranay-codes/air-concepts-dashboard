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
            <SystemLine name="Xero" view={views.xero} system={status.data.xero} />
            <SystemLine name="vWork" view={views.vwork} system={status.data.vwork} />
          </>
        ) : null}
        <button type="button" onClick={refresh} disabled={action.busy}>
          {action.busy ? "Refreshing sync…" : "Refresh sync"}
        </button>
      </div>
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

const syncErrors: Record<string, string> = {
  QUOTE_SYNC_COMPLETENESS_UNVERIFIED:
    "Some quotes may be missing. Ask an administrator to check the sync.",
  VWORK_NOT_CONNECTED: "Not connected. Ask a Director to check Settings.",
  QUOTE_SYNC_INVOICE_CHECK_REQUIRED:
    "A quote needs an invoice check before sync can finish. Ask an administrator to review it.",
  INVOICE_SYNC_PARTIAL:
    "Some invoices could not be checked. Retry the sync or ask an administrator to review it.",
  INVOICE_LOOKUP_INCOMPLETE:
    "An invoice check could not be completed. Retry the sync or ask an administrator to review it.",
  QUOTE_SYNC_PAGE_LIMIT:
    "The quote sync reached its page limit before finishing. Ask an administrator to review the sync.",
  QUOTE_SYNC_INVALID_PAGE:
    "Xero returned inconsistent quote results. Retry the sync; if it continues, ask an administrator to review it.",
  XERO_NOT_CONNECTED:
    "This entity is not connected to Xero. Ask a Director to connect it in Settings.",
  XERO_RECONNECT_REQUIRED: "Xero access needs to be reconnected by a Director in Settings.",
  XERO_SCOPE_REQUIRED: "Xero permissions need attention from a Director in Settings.",
  XERO_ORGANISATION_ACCESS_DENIED:
    "Xero refused access to this entity. Ask a Director to check its connection.",
  XERO_RATE_LIMITED: "Xero has limited requests. Wait before refreshing again.",
  XERO_UNAVAILABLE: "Xero could not be reached. Try refreshing again later.",
  XERO_PROVIDER_FAILED:
    "Xero could not complete the request. Retry or ask an administrator to check the connection.",
  XERO_INVALID_RESPONSE:
    "Xero returned incomplete or inconsistent data. Retry or ask an administrator to review it.",
  XERO_TENANT_CHANGED:
    "The Xero organisation changed. Ask a Director to review this entity's jobs and connection.",
  VWORK_NOT_CONFIGURED:
    "The vWork integration is not configured. Ask an administrator to check its setup.",
  VWORK_KEY_NOT_CONFIGURED: "The vWork credential setup needs attention from an administrator.",
  VWORK_CREDENTIAL_UNREADABLE:
    "The saved vWork credential could not be read. Ask an administrator to check the connection.",
  VWORK_SEARCH_UNVERIFIED:
    "The dashboard cannot yet verify vWork job searches. Ask an administrator to review the integration.",
  VWORK_LINK_UNVERIFIED:
    "A vWork job could not be safely linked. Ask an administrator to review the job.",
  VWORK_SYNC_OPERATION_IN_PROGRESS:
    "A job operation is still in progress. Resolve it before refreshing again.",
  QUOTE_SYNC_OPERATION_IN_PROGRESS:
    "A job operation is still in progress. Resolve it before refreshing again.",
  INVOICE_SYNC_OPERATION_IN_PROGRESS:
    "An invoice operation is still in progress. Resolve it before refreshing again.",
};

const changedCodes = new Set([
  "QUOTE_SYNC_CONNECTION_CHANGED",
  "INVOICE_SYNC_CONNECTION_CHANGED",
  "VWORK_CONNECTION_CHANGED",
  "VWORK_SYNC_CHANGED",
  "INVOICE_SYNC_CHANGED",
  "WORK_ITEM_CHANGED",
  "XERO_CONNECTION_CHANGED",
  "SYNC_EXPIRED",
]);

function syncErrorMessage(code: string | null | undefined): string {
  if (code && syncErrors[code]) return syncErrors[code];
  if (code && changedCodes.has(code))
    return "The connection or a record changed during sync. Refresh again; if it continues, ask an administrator to review it.";
  return "The refresh could not be completed. Try again or ask an administrator to review the sync details.";
}

function SystemLine({
  name,
  view,
  system,
}: {
  name: string;
  view: ReturnType<typeof syncView>;
  system: SystemStatus;
}) {
  const failed = system.latest?.status === "FAILED";
  return (
    <div role="group" aria-label={`${name} data`} className="mywork-sync-item">
      <span className="mywork-sync-name">{name}</span>
      <span className={`badge ${view.tone}`}>{view.label}</span>
      <span className="supporting">
        {view.lastSynced
          ? `Last successful sync ${formatFetchTime(view.lastSynced)}`
          : "No successful sync recorded"}
      </span>
      {failed ? (
        <p className="mywork-sync-explanation">{syncErrorMessage(system.latest?.errorCode)}</p>
      ) : null}
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
