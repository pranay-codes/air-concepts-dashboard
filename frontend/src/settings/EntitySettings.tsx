import { useCallback, useState, type FormEvent } from "react";
import { ApiError, isAccessFailure } from "../api/client";
import { isUuid } from "../api/contracts";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatFetchTime } from "../ui/format";
import type { SettingsProps } from "./Settings";

export function EntitySettings(props: SettingsProps) {
  const { api, entities, onDirty, onEntitiesChanged } = props;
  const [name, setName] = useState("");
  const [success, setSuccess] = useState("");
  const [revision, setRevision] = useState(0);
  const [attemptId, setAttemptId] = useState(() =>
    new URLSearchParams(window.location.search).get("xeroAttempt"),
  );
  const action = useAction(`create-entity:${props.profile.subject}`);
  function changed() {
    setRevision((value) => value + 1);
    onEntitiesChanged();
  }
  async function create(event: FormEvent) {
    event.preventDefault();
    if (!name.trim() || name.trim().length > 160) return;
    const result = await action.run(
      (signal) => api.createEntity(name.trim(), signal),
      (error) => {
        if (isAccessFailure(error)) props.onAccessDenied();
      },
    );
    if (result) {
      setName("");
      onDirty(false);
      setSuccess(`Added ${result.displayName}. Connect it to Xero below.`);
      changed();
    }
  }
  return (
    <>
      <h2>Entities and Xero</h2>
      <p>Each entity connects to one authorised Xero organisation. Invoice data stays in Xero.</p>
      <form className="card settings-form" aria-label="Add entity" onSubmit={create}>
        <label htmlFor="new-entity-name">New entity name</label>
        <input
          id="new-entity-name"
          required
          maxLength={160}
          value={name}
          disabled={action.busy}
          onChange={(event) => {
            setName(event.target.value);
            onDirty(Boolean(event.target.value));
          }}
        />
        <div className="action-row">
          <button type="submit" className="primary" disabled={action.busy || !name.trim()}>
            {action.busy ? "Adding entity…" : "Add entity"}
          </button>
          <button
            type="button"
            disabled={action.busy || !name}
            onClick={() => {
              setName("");
              onDirty(false);
            }}
          >
            Clear name
          </button>
        </div>
        {action.error ? (
          <>
            <Failure error={action.error} />
            <p className="supporting">
              If creation was interrupted, refresh Entities and check whether it was added before
              submitting again.
            </p>
          </>
        ) : null}
      </form>
      {success ? <Notice tone="success">{success}</Notice> : null}
      {attemptId ? (
        isUuid(attemptId) ? (
          <ConnectionAttempt
            {...props}
            attemptId={attemptId}
            onConfirmed={changed}
            onClose={() => {
              setAttemptId(null);
              window.history.replaceState(null, "", window.location.pathname);
            }}
          />
        ) : (
          <Notice tone="warning">
            This authorisation link is invalid. Start a new connection.
          </Notice>
        )
      ) : null}
      <div className="entity-cards">
        {entities.map((entity) => (
          <EntityConnection
            key={`${entity.id}:${revision}`}
            {...props}
            entity={entity}
            blocked={Boolean(name)}
          />
        ))}
      </div>
      {!entities.length ? (
        <Notice>Add your first entity, then connect its Xero organisation.</Notice>
      ) : null}
    </>
  );
}
function EntityConnection({
  entity,
  blocked,
  ...props
}: SettingsProps & { entity: SettingsProps["entities"][number]; blocked: boolean }) {
  const { api, onAccessDenied } = props;
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
  const action = useAction(`connect:${entity.id}`);
  async function connect() {
    const result = await action.run(
      (signal) => props.api.startConnection(entity.id, signal),
      (error) => {
        if (isAccessFailure(error)) props.onAccessDenied();
      },
    );
    if (result)
      (props.openAuthorization ?? ((url) => window.location.assign(url)))(result.authorizationUrl);
  }
  return (
    <article className="card">
      <h3>{entity.displayName}</h3>
      {resource.status === "loading" ? <Loading label="Checking Xero connection…" /> : null}
      {resource.status === "error" ? (
        <Failure
          error={resource.error}
          onRetry={resource.refresh}
          label={`Retry ${entity.displayName} connection`}
        />
      ) : null}
      {resource.data ? (
        <>
          <p>
            <span
              className={`badge ${resource.data.status === "CONNECTED" ? "success" : "warning"}`}
            >
              {resource.data.status === "CONNECTED"
                ? "Connected"
                : resource.data.status === "NEEDS_RECONNECTION"
                  ? "Needs reconnection"
                  : "Not connected"}
            </span>
          </p>
          {resource.data.organisationName ? <p>{resource.data.organisationName}</p> : null}
          <p className="supporting">
            Last confirmed: {formatFetchTime(resource.data.lastConfirmedAt)}
          </p>
        </>
      ) : null}
      <button type="button" disabled={action.busy || blocked} onClick={() => void connect()}>
        {action.busy
          ? "Opening Xero…"
          : `${resource.data?.status === "CONNECTED" || resource.data?.status === "NEEDS_RECONNECTION" ? "Reconnect" : "Connect"} ${entity.displayName}`}
      </button>
      {blocked ? (
        <p className="supporting">
          Finish adding the new entity or clear its name before connecting to Xero.
        </p>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
    </article>
  );
}
function ConnectionAttempt({
  api,
  entities,
  onAccessDenied,
  attemptId,
  onConfirmed,
  onClose,
}: SettingsProps & { attemptId: string; onConfirmed(): void; onClose(): void }) {
  const [selected, setSelected] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.attempt(attemptId, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, attemptId, onAccessDenied],
  );
  const resource = useResource(`xero-attempt:${attemptId}`, load);
  const action = useAction(`confirm:${attemptId}`);
  const attempt = resource.data;
  const entity = entities.find((entity) => entity.id === attempt?.entityId);
  async function confirm(event: FormEvent) {
    event.preventDefault();
    if (!attempt || !selected) return;
    const result = await action.run(
      async (signal) => {
        const value = await api.confirmConnection(attemptId, selected, signal);
        if (value.entityId !== attempt.entityId) throw new ApiError(502, "INVALID_RESPONSE");
        return value;
      },
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) {
      setConfirmed(true);
      onConfirmed();
      resource.refresh();
    }
  }
  return (
    <section className="card connection-attempt" aria-label="Complete Xero connection">
      <h3>Complete Xero connection{entity ? ` for ${entity.displayName}` : ""}</h3>
      {resource.status === "loading" ? <Loading label="Checking authorisation…" /> : null}
      {resource.status === "error" ? (
        <Failure
          error={resource.error}
          onRetry={resource.refresh}
          label="Check authorisation again"
        />
      ) : null}
      {confirmed || attempt?.state === "COMPLETED" ? (
        <Notice tone="success">
          Connection confirmed. Current connection details are shown below.
        </Notice>
      ) : attempt?.state === "READY" ? (
        attempt.choices.length ? (
          <form onSubmit={confirm}>
            <label htmlFor="xero-organisation">Xero organisation</label>
            <select
              id="xero-organisation"
              value={selected}
              disabled={action.busy}
              onChange={(event) => setSelected(event.target.value)}
            >
              <option value="">Choose an authorised organisation</option>
              {attempt.choices.map((choice) => (
                <option key={choice.connectionId} value={choice.connectionId}>
                  {choice.name}
                </option>
              ))}
            </select>
            <p className="supporting">
              Confirm the organisation that belongs to {entity?.displayName ?? "this entity"}. An
              organisation can be connected to only one entity.
            </p>
            <button type="submit" className="primary" disabled={action.busy || !selected}>
              {action.busy ? "Confirming…" : "Confirm connection"}
            </button>
          </form>
        ) : (
          <Notice tone="warning">
            No authorised organisations are available. Start a new connection and grant access in
            Xero.
          </Notice>
        )
      ) : attempt?.state === "FAILED" ? (
        <Notice tone="warning">
          Authorisation failed or expired. Start a new connection for this entity.
        </Notice>
      ) : attempt ? (
        <Notice>
          Authorisation is still in progress.{" "}
          <button type="button" onClick={resource.refresh}>
            Check again
          </button>
        </Notice>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
      <button type="button" disabled={action.busy} onClick={onClose}>
        Dismiss connection attempt
      </button>
    </section>
  );
}
