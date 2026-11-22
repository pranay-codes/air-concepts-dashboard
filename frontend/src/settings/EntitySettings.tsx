import { useCallback, useState } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity } from "../api/contracts";
import { isUuid } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { ArchiveEntityDialog, RenameEntityDialog } from "./EntityActions";
import { EntityDialog, type EntityDialogMode } from "./EntityDialog";
import type { SettingsProps } from "./Settings";

type Dialog =
  | EntityDialogMode
  | { kind: "rename"; entity: Entity }
  | { kind: "archive"; entity: Entity; organisationName: string | null };

const zone = "Pacific/Auckland";
const dateFormat = new Intl.DateTimeFormat("en-NZ", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: zone,
});
const timeFormat = new Intl.DateTimeFormat("en-NZ", {
  hour: "numeric",
  minute: "2-digit",
  timeZone: zone,
});

/** Card dates are shown in the business time zone. Values that are not dates are reported as unknown. */
function connectedDate(value: string | undefined): string | null {
  const date = value ? new Date(value) : null;
  return date && Number.isFinite(date.getTime()) ? dateFormat.format(date) : null;
}
function lastChecked(value: string | null | undefined): string {
  const date = value ? new Date(value) : null;
  if (!date || !Number.isFinite(date.getTime())) return "Not checked yet";
  const day = dateFormat.format(date);
  const time = timeFormat.format(date);
  return day === dateFormat.format(new Date()) ? `Today, ${time}` : `${day}, ${time}`;
}

/** Opens the dialog for a return from Xero. Invalid values are reported instead of being used. */
function initialDialog(): Dialog | null {
  const attempt = new URLSearchParams(window.location.search).get("xeroAttempt");
  return attempt && isUuid(attempt) ? { kind: "resume", attemptId: attempt } : null;
}

export function EntitySettings(props: SettingsProps) {
  const { api, entities, onEntitiesChanged, onAccessDenied, openAuthorization } = props;
  const [revision, setRevision] = useState(0);
  const [success, setSuccess] = useState("");
  const [invalidReturn] = useState(() => {
    const attempt = new URLSearchParams(window.location.search).get("xeroAttempt");
    return attempt !== null && !isUuid(attempt);
  });
  const [dialog, setDialog] = useState<Dialog | null>(initialDialog);

  function changed() {
    setRevision((value) => value + 1);
    onEntitiesChanged();
  }
  function closeDialog() {
    if (dialog?.kind === "resume") window.history.replaceState(null, "", window.location.pathname);
    setDialog(null);
  }

  return (
    <>
      <div className="entities-header">
        <div>
          <h2>Entities</h2>
          <p>
            Each entity is linked to one Xero organisation. Its invoices, bills and forecast come
            from there.
          </p>
        </div>
        <button type="button" className="primary" onClick={() => setDialog({ kind: "add" })}>
          <span aria-hidden="true">+ </span>Add entity
        </button>
      </div>
      {success ? (
        <Notice tone="success">
          {success}{" "}
          <button type="button" onClick={() => setSuccess("")}>
            Dismiss
          </button>
        </Notice>
      ) : null}
      {invalidReturn ? (
        <Notice tone="warning">This authorisation link is invalid. Start a new connection.</Notice>
      ) : null}
      <div className="entity-list">
        {entities.map((entity) => (
          <EntityCard
            key={`${entity.id}:${revision}`}
            api={api}
            entity={entity}
            onAccessDenied={onAccessDenied}
            onConnect={() => setDialog({ kind: "connect", entity })}
            onRename={() => setDialog({ kind: "rename", entity })}
            onArchive={(organisationName) =>
              setDialog({ kind: "archive", entity, organisationName })
            }
          />
        ))}
      </div>
      {!entities.length ? (
        <Notice>Add your first entity, then connect its Xero organisation.</Notice>
      ) : null}
      <p className="supporting security-note">
        Xero access keys are stored encrypted on the server and never shown in the browser. Only the
        Director can add, edit or remove entities.
      </p>
      {dialog &&
      (dialog.kind === "add" || dialog.kind === "connect" || dialog.kind === "resume") ? (
        <EntityDialog
          mode={dialog}
          api={api}
          entities={entities}
          onAccessDenied={onAccessDenied}
          openAuthorization={openAuthorization}
          onClose={closeDialog}
          onEntitiesChanged={changed}
          onConfirmed={(message) => {
            setSuccess(message);
            closeDialog();
            changed();
          }}
        />
      ) : null}
      {dialog?.kind === "rename" ? (
        <RenameEntityDialog
          api={api}
          entity={dialog.entity}
          onAccessDenied={onAccessDenied}
          onClose={closeDialog}
          onRenamed={() => {
            setSuccess("Entity name saved.");
            closeDialog();
            changed();
          }}
        />
      ) : null}
      {dialog?.kind === "archive" ? (
        <ArchiveEntityDialog
          api={api}
          entity={dialog.entity}
          organisationName={dialog.organisationName}
          onAccessDenied={onAccessDenied}
          onClose={closeDialog}
          onArchived={() => {
            setSuccess(`${dialog.entity.displayName} was removed from the entity list.`);
            closeDialog();
            changed();
          }}
        />
      ) : null}
    </>
  );
}

function EntityCard({
  api,
  entity,
  onAccessDenied,
  onConnect,
  onRename,
  onArchive,
}: {
  api: DashboardApi;
  entity: Entity;
  onAccessDenied(): void;
  onConnect(): void;
  onRename(): void;
  onArchive(organisationName: string | null): void;
}) {
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.connection(entity.id, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, onAccessDenied],
  );
  const resource = useResource(`entity-settings:${entity.id}`, load);
  const connection = resource.data;
  const connected = connection?.status === "CONNECTED";
  const reconnect =
    connection?.status === "NEEDS_RECONNECTION" || connection?.quoteAccess === "RECONNECT_REQUIRED";
  const verifiedOn = connectedDate(connection?.lastConfirmedAt);
  return (
    <article className="card entity-card">
      <div className="entity-card-header">
        <h3>
          <span className="entity-dot" aria-hidden="true" />
          {entity.displayName}
        </h3>
        {connection ? (
          connected ? (
            <span className={`badge ${connection.errorCode ? "warning" : "success"}`}>
              {connection.errorCode ? "Check connection" : "Connected"}
            </span>
          ) : (
            <span className="badge warning">
              {reconnect ? "Needs reconnection" : "Not connected"}
            </span>
          )
        ) : null}
      </div>
      {resource.status === "loading" ? <Loading label="Checking Xero connection…" /> : null}
      {resource.status === "error" ? (
        <Failure
          error={resource.error}
          onRetry={resource.refresh}
          label={`Retry ${entity.displayName} connection`}
        />
      ) : null}
      {connection ? (
        <>
          <p className="supporting">
            Xero organisation · <strong>{connection.organisationName ?? "Not linked"}</strong>
          </p>
          <dl className="entity-facts">
            <div>
              <dt>Tenant ID</dt>
              <dd>
                <code>{connection.maskedTenantId ?? "Not recorded"}</code>
              </dd>
            </div>
            <div>
              <dt>Connected by</dt>
              <dd>
                {connection.connectedBy
                  ? `${connection.connectedBy}${verifiedOn ? ` · ${verifiedOn}` : ""}`
                  : "Not recorded"}
              </dd>
            </div>
            <div>
              <dt>Last checked</dt>
              <dd>{lastChecked(connection.lastCheckedAt)}</dd>
            </div>
          </dl>
          {connected && connection.quoteAccess === "UNKNOWN" ? (
            <Notice tone="warning">
              Xero permissions for the quote workflow could not be verified. Ask the setup owner to
              check.
            </Notice>
          ) : null}
          {connected && connection.quoteAccess === "RECONNECT_REQUIRED" ? (
            <Notice tone="warning">Reconnect to allow quote and contact access.</Notice>
          ) : null}
        </>
      ) : null}
      <div className="action-row">
        {connection && (!connected || reconnect) ? (
          <button type="button" className="primary" onClick={onConnect}>
            {reconnect ? `Reconnect ${entity.displayName}` : `Connect ${entity.displayName}`}
          </button>
        ) : null}
        <button type="button" onClick={onRename}>
          Edit
        </button>
        <button
          type="button"
          className="danger"
          onClick={() => onArchive(connection?.organisationName ?? null)}
        >
          Remove
        </button>
      </div>
    </article>
  );
}
