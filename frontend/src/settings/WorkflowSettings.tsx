import { useCallback, useRef, useState, type FormEvent } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, GreenLineSettings, Profile, Staff } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useAction } from "../api/useAction";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { VworkConnectionSection } from "./VworkConnectionSection";
import { VworkTemplatesSection } from "./VworkTemplatesSection";

interface Props {
  api: DashboardApi;
  entities: Entity[];
  profile: Profile;
  onDirty(value: boolean): void;
  onAccessDenied(): void;
}

export function WorkflowSettings({ api, entities, profile, onDirty, onAccessDenied }: Props) {
  const dirtyEntities = useRef(new Set<string>());
  const changed = useCallback(
    (entity: string, dirty: boolean) => {
      if (dirty) dirtyEntities.current.add(entity);
      else dirtyEntities.current.delete(entity);
      onDirty(dirtyEntities.current.size > 0);
    },
    [onDirty],
  );
  const loadUsers = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.users(signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, onAccessDenied],
  );
  const users = useResource(`workflow-users:${profile.subject}`, loadUsers);
  const activeUsers = users.data?.filter((person) => person.active);
  return (
    <>
      <h2>Workflow</h2>
      <p>Choose who handles quotes and scheduling for each entity.</p>
      <VworkConnectionSection
        api={api}
        profile={profile}
        onAccessDenied={onAccessDenied}
        onDirty={(dirty) => changed("vwork-account", dirty)}
      />
      {users.status === "loading" ? <Loading label="Loading staff…" /> : null}
      {users.status === "error" ? <Failure error={users.error} onRetry={users.refresh} /> : null}
      {entities.map((entity) => (
        <EntityWorkflow
          key={`${profile.subject}:${entity.id}`}
          api={api}
          entity={entity}
          profile={profile}
          staff={activeUsers}
          onDirty={changed}
          onAccessDenied={onAccessDenied}
        />
      ))}
      {!entities.length ? (
        <Notice>No entities are available. Add an entity to configure its workflow.</Notice>
      ) : null}
    </>
  );
}

function EntityWorkflow({
  api,
  entity,
  profile,
  staff,
  onDirty,
  onAccessDenied,
}: {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  staff: Staff[] | undefined;
  onDirty(entity: string, value: boolean): void;
  onAccessDenied(): void;
}) {
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.greenLineSettings(entity.id, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, onAccessDenied],
  );
  const resource = useResource(`workflow:${profile.subject}:${entity.id}`, load);
  return (
    <>
      {resource.status === "loading" ? (
        <Loading label={`Loading ${entity.displayName} workflow…`} />
      ) : null}
      {resource.status === "error" ? (
        <Failure
          error={resource.error}
          onRetry={resource.refresh}
          label={`Retry ${entity.displayName} workflow`}
        />
      ) : null}
      {resource.data && staff ? (
        <WorkflowForm
          api={api}
          entity={entity}
          profile={profile}
          staff={staff}
          initial={resource.data}
          onDirty={onDirty}
          onAccessDenied={onAccessDenied}
        />
      ) : null}
      <VworkTemplatesSection
        api={api}
        entity={entity}
        profile={profile}
        onAccessDenied={onAccessDenied}
        onDirty={(dirty) => onDirty(`templates:${entity.id}`, dirty)}
      />
    </>
  );
}

function WorkflowForm({
  api,
  entity,
  profile,
  staff,
  initial,
  onDirty,
  onAccessDenied,
}: {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  staff: Staff[];
  initial: GreenLineSettings;
  onDirty(entity: string, value: boolean): void;
  onAccessDenied(): void;
}) {
  const [enabled, setEnabled] = useState(initial.enabled);
  const [owner, setOwner] = useState(initial.defaultOwnerId ?? "");
  const [manager, setManager] = useState(initial.serviceManagerId ?? "");
  const [threshold, setThreshold] = useState(String(initial.lateThresholdWorkingDays));
  const [backfill, setBackfill] = useState(String(initial.backfillDays));
  const [autoSync, setAutoSync] = useState(initial.automaticSyncEnabled ?? false);
  const [syncInterval, setSyncInterval] = useState(
    String(initial.automaticSyncIntervalMinutes ?? 10),
  );
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const action = useAction(`workflow-save:${profile.subject}:${entity.id}`);
  function changed() {
    setSuccess(false);
    onDirty(entity.id, true);
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const days = Number(threshold),
      backfillWindow = Number(backfill);
    const interval = Number(syncInterval);
    if (
      !/^\d+$/.test(syncInterval) ||
      !Number.isSafeInteger(interval) ||
      interval < 1 ||
      interval > 1440
    ) {
      setError("Enter a sync interval from 1 to 1440 minutes.");
      document.getElementById(`${prefix}-sync-interval`)?.focus();
      return;
    }
    if (!/^\d+$/.test(threshold) || !Number.isSafeInteger(days) || days < 1 || days > 30) {
      setError("Enter a whole number from 1 to 30.");
      document.getElementById(`${prefix}-threshold`)?.focus();
      return;
    }
    if (
      !/^\d+$/.test(backfill) ||
      !Number.isSafeInteger(backfillWindow) ||
      backfillWindow < 0 ||
      backfillWindow > 365
    ) {
      setError("Enter a whole number from 0 to 365.");
      document.getElementById(`${prefix}-backfill`)?.focus();
      return;
    }
    setError("");
    const result = await action.run(
      (signal) =>
        api.saveGreenLineSettings(
          entity.id,
          {
            enabled,
            ...(owner ? { defaultOwnerId: owner } : {}),
            ...(manager ? { serviceManagerId: manager } : {}),
            lateThresholdWorkingDays: days,
            backfillDays: backfillWindow,
            automaticSyncEnabled: autoSync,
            automaticSyncIntervalMinutes: interval,
          },
          signal,
        ),
      (failure) => {
        if (isAccessFailure(failure)) onAccessDenied();
      },
    );
    if (result) {
      setSuccess(true);
      onDirty(entity.id, false);
    }
  }
  const prefix = `workflow-${entity.id}`;
  return (
    <form
      className="card settings-form"
      aria-label={`Workflow settings for ${entity.displayName}`}
      onSubmit={submit}
      noValidate
    >
      <h3>{entity.displayName}</h3>
      <label className="checkbox-label">
        <input
          type="checkbox"
          checked={autoSync}
          disabled={action.busy}
          onChange={(event) => {
            setAutoSync(event.target.checked);
            changed();
          }}
        />{" "}
        Enable automatic sync
      </label>
      <label htmlFor={`${prefix}-sync-interval`}>Sync interval in minutes</label>
      <p>Actual timing depends on sync duration and provider limits.</p>
      <input
        id={`${prefix}-sync-interval`}
        type="number"
        min="1"
        max="1440"
        value={syncInterval}
        disabled={action.busy}
        aria-invalid={error === "Enter a sync interval from 1 to 1440 minutes."}
        aria-describedby={
          error === "Enter a sync interval from 1 to 1440 minutes." ? `${prefix}-error` : undefined
        }
        onChange={(event) => {
          setSyncInterval(event.target.value);
          changed();
        }}
      />
      {initial.automaticSyncAvailable === false ? (
        <Notice tone="warning">
          Automatic sync is unavailable on the server. Ask an administrator to enable it.
        </Notice>
      ) : null}
      <label className="checkbox-label">
        <input
          type="checkbox"
          checked={enabled}
          disabled={action.busy}
          onChange={(event) => {
            setEnabled(event.target.checked);
            changed();
          }}
        />{" "}
        Enable quote-to-invoice workflow
      </label>
      <label htmlFor={`${prefix}-owner`}>Default owner</label>
      <select
        id={`${prefix}-owner`}
        value={owner}
        disabled={action.busy}
        onChange={(event) => {
          setOwner(event.target.value);
          changed();
        }}
      >
        <option value="">Unassigned</option>
        {staff
          .filter((person) => person.role === "DIRECTOR" || person.role === "FINANCIAL_ADMIN")
          .map((person) => (
            <option key={person.id} value={person.id}>
              {person.displayName}
            </option>
          ))}
        {owner &&
        !staff.some(
          (person) =>
            person.id === owner &&
            (person.role === "DIRECTOR" || person.role === "FINANCIAL_ADMIN"),
        ) ? (
          <option value={owner}>Unavailable staff member</option>
        ) : null}
      </select>
      <label htmlFor={`${prefix}-manager`}>Service Manager</label>
      <select
        id={`${prefix}-manager`}
        value={manager}
        disabled={action.busy}
        onChange={(event) => {
          setManager(event.target.value);
          changed();
        }}
      >
        <option value="">Unassigned</option>
        {staff.map((person) => (
          <option key={person.id} value={person.id}>
            {person.displayName}
          </option>
        ))}
        {manager && !staff.some((person) => person.id === manager) ? (
          <option value={manager}>Unavailable staff member</option>
        ) : null}
      </select>
      <label htmlFor={`${prefix}-threshold`}>Late after working days</label>
      <input
        id={`${prefix}-threshold`}
        type="number"
        min="1"
        max="30"
        value={threshold}
        disabled={action.busy}
        aria-invalid={error === "Enter a whole number from 1 to 30."}
        aria-describedby={
          error === "Enter a whole number from 1 to 30." ? `${prefix}-error` : undefined
        }
        onChange={(event) => {
          setThreshold(event.target.value);
          changed();
        }}
      />
      <label htmlFor={`${prefix}-backfill`}>Backfill window in days</label>
      <input
        id={`${prefix}-backfill`}
        type="number"
        min="0"
        max="365"
        value={backfill}
        disabled={action.busy}
        aria-invalid={error === "Enter a whole number from 0 to 365."}
        aria-describedby={
          error === "Enter a whole number from 0 to 365." ? `${prefix}-error` : undefined
        }
        onChange={(event) => {
          setBackfill(event.target.value);
          changed();
        }}
      />
      {error ? (
        <p id={`${prefix}-error`} role="alert">
          {error}
        </p>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
      {success ? <Notice tone="success">Workflow settings saved.</Notice> : null}
      <button type="submit" className="primary" disabled={action.busy}>
        {action.busy ? "Saving…" : "Save workflow settings"}
      </button>
    </form>
  );
}
