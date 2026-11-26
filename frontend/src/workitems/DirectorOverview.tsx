import { useCallback, useEffect, useId, useMemo, useRef, useState, type FormEvent } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { Failure, Loading } from "../ui/Feedback";
import { formatDate, formatFetchTime } from "../ui/format";
import { useMediaQuery } from "../ui/useMediaQuery";
import type { WorkItem } from "./contracts";
import { JobDetail } from "./JobDetail";
import { businessToday } from "./myWorkModel";
import { periodPresets, periodProblem, type Period } from "./overviewModel";
import { OverviewReport } from "./OverviewReport";
import "./overview.css";

export function DirectorOverview({
  api,
  entity,
  entities,
  profile,
  onAccessDenied,
  onDirty,
  onSelectEntity,
}: {
  api: DashboardApi;
  entity: Entity | null;
  entities: Entity[];
  profile: Profile;
  onAccessDenied(): void;
  onDirty(value: boolean): void;
  onSelectEntity(id: string): void;
}) {
  const ids = useId();
  const narrow = useMediaQuery("(width < 48rem)");
  const presets = periodPresets(businessToday());
  const [period, setPeriod] = useState<Period>(() => periodPresets(businessToday())[1]!.period);
  const [draft, setDraft] = useState(period);
  const [problem, setProblem] = useState<string | null>(null);
  const [selection, setSelection] = useState<{
    item: WorkItem;
    wasAll: boolean;
    section: string;
  } | null>(null);
  const restore = useRef<string | null>(null);
  const authorised = useMemo(() => entities.map((item) => item.id), [entities]);
  const entityId = entity?.id ?? null;
  const [previousEntity, setPreviousEntity] = useState(entityId);
  if (previousEntity !== entityId) {
    setPreviousEntity(entityId);
    if (selection && entityId !== selection.item.entityId) setSelection(null);
  }
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.overview(entityId, authorised, period, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entityId, authorised, period, onAccessDenied],
  );
  const resource = useResource(
    JSON.stringify([profile.subject, profile.role, entityId, authorised, period]),
    load,
  );
  // Return the reader to the report they opened a job from, once it is on screen again.
  useEffect(() => {
    if (selection || !resource.data || !restore.current) return;
    const target = document.getElementById(restore.current);
    target?.focus();
    target?.scrollIntoView?.({ block: "start" });
    restore.current = null;
  }, [selection, resource.data]);
  const chosen =
    selection && entityId === selection.item.entityId
      ? entities.find((candidate) => candidate.id === entityId)
      : undefined;
  if (selection && chosen)
    return (
      <JobDetail
        key={`${profile.subject}:${profile.role}:${chosen.id}:${selection.item.id}`}
        api={api}
        entity={chosen}
        profile={profile}
        itemId={selection.item.id}
        onDirty={onDirty}
        onAccessDenied={onAccessDenied}
        onChanged={resource.refresh}
        backLabel="Back to Overview"
        onBack={() => {
          restore.current = selection.section;
          if (selection.wasAll) onSelectEntity("__all__");
          setSelection(null);
        }}
      />
    );
  function open(item: WorkItem, section: string) {
    if (!authorised.includes(item.entityId)) return;
    setSelection({ item, section, wasAll: entityId === null });
    if (entityId !== item.entityId) onSelectEntity(item.entityId);
  }
  function show(next: Period) {
    setDraft(next);
    setProblem(null);
    setPeriod(next);
  }
  function apply(event: FormEvent) {
    event.preventDefault();
    const found = periodProblem(draft);
    setProblem(found);
    if (!found) setPeriod(draft);
  }
  const controls = (
    <div className="overview-controls">
      <div className="overview-presets" role="group" aria-label="Report period">
        {presets.map((preset) => (
          <button
            key={preset.label}
            type="button"
            aria-pressed={preset.period.from === period.from && preset.period.to === period.to}
            onClick={() => show(preset.period)}
          >
            {preset.label}
          </button>
        ))}
      </div>
      <form className="mywork-toolbar" onSubmit={apply} noValidate>
        {(["from", "to"] as const).map((field) => (
          <div key={field} className="mywork-field">
            <label htmlFor={`${ids}-${field}`}>{field === "from" ? "From" : "To"}</label>
            <input
              id={`${ids}-${field}`}
              type="date"
              value={draft[field]}
              onChange={(event) =>
                setDraft((current) => ({ ...current, [field]: event.target.value }))
              }
            />
          </div>
        ))}
        <button type="submit">Apply period</button>
      </form>
    </div>
  );
  return (
    <div className="overview">
      <header className="mywork-header">
        <div>
          <h1>Overview</h1>
          <p className="supporting">{entity ? entity.displayName : "All entities"}</p>
        </div>
        {resource.data ? (
          <div className="mywork-header-actions">
            <p className="supporting">Updated {formatFetchTime(resource.loadedAt)}</p>
            <button
              type="button"
              onClick={resource.refresh}
              disabled={resource.status === "refreshing"}
            >
              Refresh overview
            </button>
          </div>
        ) : null}
      </header>
      <div className="overview-period card">
        {narrow ? (
          <details>
            <summary>Change period</summary>
            {controls}
          </details>
        ) : (
          controls
        )}
        {problem ? (
          <p role="alert" className="notice error">
            {problem}
          </p>
        ) : (
          <p className="supporting">
            Showing {formatDate(period.from)} to {formatDate(period.to)}. The period changes the
            performance figures only; open work is shown as it is now.
          </p>
        )}
      </div>
      {resource.status === "loading" ? <Loading label="Loading the overview…" /> : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} label="Retry overview" />
      ) : null}
      {resource.status === "refreshing" ? <p role="status">Refreshing the overview…</p> : null}
      {resource.data ? (
        <div aria-busy={resource.status === "refreshing"}>
          <OverviewReport data={resource.data} onOpen={open} />
        </div>
      ) : null}
    </div>
  );
}
