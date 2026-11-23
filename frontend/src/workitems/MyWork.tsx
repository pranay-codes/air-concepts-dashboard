import { useCallback, useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatDate, formatFetchTime } from "../ui/format";
import { useMediaQuery } from "../ui/useMediaQuery";
import { JobDetail } from "./JobDetail";
import { SyncFreshness } from "./SyncFreshness";
import type { WorkItem } from "./contracts";
import {
  businessToday,
  categoryCounts,
  collectRows,
  defaultFilters,
  isPastFollowUp,
  visibleRows,
  type AttentionFilter,
  type CategoryFilter,
  type WorkCategory,
  type StageFilter,
  type WorkFilters,
  type WorkRow,
  type WorkSort,
} from "./myWorkModel";
import { attentionLabel, flagLabel, stageLabels } from "./stageText";

const actionLabels: Record<string, string> = {
  FOLLOW_UP: "Follow up",
  CREATE_JOB: "Create job",
  SCHEDULE_IN_VWORK: "Schedule in vWork",
  AWAIT_COMPLETION: "Waiting for technician",
  PREPARE_INVOICE: "Prepare invoice",
  REVIEW_AND_SEND: "Review and send",
  CHASE_PAYMENT: "Chase payment",
  AWAIT_PAYMENT: "Waiting for payment",
  RESOLVE: "Resolve",
};
const categoryTiles: { key: CategoryFilter; label: string }[] = [
  { key: "ALL", label: "All work" },
  { key: "ACTION", label: "Needs your action" },
  { key: "ATTENTION", label: "Needs attention" },
  { key: "WAITING", label: "Waiting" },
];
const stageOptions = Object.entries(stageLabels) as [WorkItem["stage"], string][];
const attentionOptions: { value: AttentionFilter; label: string }[] = [
  { value: "ANY", label: "Any" },
  { value: "FLAGGED", label: "Flagged for attention" },
  { value: "LATE", label: "Late" },
  { value: "NONE", label: "No flags" },
];
const sortOptions: { value: WorkSort; label: string }[] = [
  { value: "PRIORITY", label: "Priority" },
  { value: "OVERDUE", label: "Most overdue" },
  { value: "FOLLOW_UP", label: "Follow-up date" },
  { value: "TIME_IN_STAGE", label: "Time in stage" },
];
/** Below this width the list becomes cards; the table is used from tablet width upward. */
const compactQuery = "(width < 48rem)";

function daysInStage(item: WorkItem): string {
  return `${item.daysInStage} ${item.daysInStage === 1 ? "day" : "days"} in stage`;
}

function isUrgent(row: WorkRow): boolean {
  return row.item.late || row.category === "ATTENTION";
}

const categoryBadges: Record<WorkCategory, { label: string; tone: string }> = {
  ACTION: { label: "Your action", tone: "information" },
  ATTENTION: { label: "Needs attention", tone: "warning" },
  WAITING: { label: "Waiting on others", tone: "neutral" },
};

function followUp(item: WorkItem, today: string): ReactNode {
  if (!item.followUpDate) return <span className="supporting">No follow-up set</span>;
  return (
    <>
      <span>{formatDate(item.followUpDate)}</span>
      {isPastFollowUp(item, today) ? <span className="badge error">Past due</span> : null}
    </>
  );
}

export function MyWork({
  api,
  entity,
  entities,
  profile,
  onDirty,
  onAccessDenied,
  onSelectEntity,
}: {
  api: DashboardApi;
  entity: Entity | null;
  entities: Entity[];
  profile: Profile;
  onDirty(value: boolean): void;
  onAccessDenied(): void;
  onSelectEntity(id: string): void;
}) {
  const [selection, setSelection] = useState<{ item: WorkItem; wasAll: boolean } | null>(null);
  const [filters, setFilters] = useState<WorkFilters>(defaultFilters);
  const restore = useRef<string | null>(null);
  const [previousEntity, setPreviousEntity] = useState(entity?.id ?? null);
  const ids = useId();
  const authorised = useMemo(() => entities.map((item) => item.id), [entities]);
  const entityId = entity?.id ?? null;
  if (previousEntity !== entityId) {
    setPreviousEntity(entityId);
    if (selection && entityId !== selection.item.entityId) setSelection(null);
  }
  const key = JSON.stringify([profile.subject, profile.role, entityId, authorised]);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.myWork(entityId, authorised, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entityId, authorised, onAccessDenied],
  );
  const resource = useResource(key, load);
  useEffect(() => {
    if (!selection && resource.status === "success" && restore.current) {
      document.getElementById(`mywork-action-${restore.current}`)?.focus();
      restore.current = null;
    }
  }, [resource.status, resource.data, selection]);
  const data = resource.data;
  const rows = useMemo(() => (data ? collectRows(data) : []), [data]);
  const shown = useMemo(() => visibleRows(rows, filters), [rows, filters]);
  const compact = useMediaQuery(compactQuery);
  const today = businessToday();
  const chosen =
    selection && entityId === selection.item.entityId
      ? entities.find((e) => e.id === selection.item.entityId)
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
        backLabel="Back to My Work"
        focusFollowUp={selection.item.nextAction === "FOLLOW_UP"}
        onBack={() => {
          restore.current = selection.item.id;
          if (selection.wasAll) onSelectEntity("__all__");
          setSelection(null);
        }}
      />
    );
  function open(item: WorkItem) {
    if (!entities.some((candidate) => candidate.id === item.entityId)) return;
    setSelection({ item, wasAll: entityId === null });
    if (entityId !== item.entityId) onSelectEntity(item.entityId);
  }
  const update = (patch: Partial<WorkFilters>) =>
    setFilters((current) => ({ ...current, ...patch }));
  const filtersActive =
    filters.category !== defaultFilters.category ||
    filters.stage !== defaultFilters.stage ||
    filters.attention !== defaultFilters.attention ||
    filters.sort !== defaultFilters.sort;
  const counts = data ? categoryCounts(data, rows) : null;

  function actionButton(item: WorkItem, className?: string) {
    const action = actionLabels[item.nextAction ?? ""] ?? "Review";
    return (
      <button
        id={`mywork-action-${item.id}`}
        type="button"
        className={className}
        onClick={() => open(item)}
        aria-label={`${action} ${item.quoteNumber}`}
      >
        {action}
      </button>
    );
  }

  function needs(row: WorkRow): ReactNode {
    const { item } = row;
    const notes = item.attention.map((code) => attentionLabel(code, item.stage));
    const category = categoryBadges[row.category];
    return (
      <div className="mywork-needs">
        <span className={`badge ${category.tone}`}>{category.label}</span>
        {item.late ? <span className="badge error">Late</span> : null}
        {item.flags
          .filter((flag) => flag !== "LATE")
          .map((flag) => (
            <span className="badge warning" key={flag}>
              {flagLabel(flag)}
            </span>
          ))}
        {notes.length ? (
          <span className="supporting">
            {notes[0]}
            {notes.length > 1 ? ` (+${notes.length - 1} more in the job)` : ""}
          </span>
        ) : null}
      </div>
    );
  }

  function table(list: WorkRow[]) {
    return (
      <div className="mywork-table-wrap" role="region" aria-label="My work items" tabIndex={0}>
        <table className="mywork-table">
          <caption className="visually-hidden">Work items, one row per job</caption>
          <thead>
            <tr>
              <th scope="col">Job</th>
              <th scope="col">Customer</th>
              <th scope="col">Stage</th>
              <th scope="col">Owner</th>
              <th scope="col">Follow-up</th>
              <th scope="col">Needs</th>
              <th scope="col">Next action</th>
            </tr>
          </thead>
          <tbody>
            {list.map((row) => {
              const { item } = row;
              return (
                <tr key={item.id} className={isUrgent(row) ? "mywork-row urgent" : "mywork-row"}>
                  <th scope="row">
                    <button
                      type="button"
                      className="text-button"
                      aria-label={`Open ${item.quoteNumber}`}
                      onClick={() => open(item)}
                    >
                      {item.quoteNumber}
                    </button>
                    <span className="supporting">
                      {item.jobId || "Job ID not recorded"} · {item.entityName}
                    </span>
                  </th>
                  <td>{item.customerName || "Customer not recorded"}</td>
                  <td>
                    <span className="badge information">{stageLabels[item.stage]}</span>
                    <span className="supporting">{daysInStage(item)}</span>
                  </td>
                  <td>{item.owner?.displayName ?? "Unassigned"}</td>
                  <td>{followUp(item, today)}</td>
                  <td>{needs(row)}</td>
                  <td>{actionButton(item)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  }

  function cards(list: WorkRow[]) {
    return (
      <ul className="mywork-cards" aria-label="My work items">
        {list.map((row) => {
          const { item } = row;
          return (
            <li key={item.id}>
              <article className={isUrgent(row) ? "card mywork-card urgent" : "card mywork-card"}>
                <div className="mywork-card-head">
                  <h3>
                    <button
                      type="button"
                      className="text-button"
                      aria-label={`Open ${item.quoteNumber}`}
                      onClick={() => open(item)}
                    >
                      {item.quoteNumber}
                    </button>
                  </h3>
                  <span className="badge information">{stageLabels[item.stage]}</span>
                </div>
                <p>{item.customerName || "Customer not recorded"}</p>
                <p className="supporting">
                  {item.jobId || "Job ID not recorded"} · {item.entityName}
                </p>
                {needs(row)}
                <dl className="mywork-facts">
                  <div>
                    <dt>Owner</dt>
                    <dd>{item.owner?.displayName ?? "Unassigned"}</dd>
                  </div>
                  <div>
                    <dt>Follow-up</dt>
                    <dd>{followUp(item, today)}</dd>
                  </div>
                  <div>
                    <dt>Time in stage</dt>
                    <dd>{daysInStage(item)}</dd>
                  </div>
                </dl>
                {actionButton(item, "mywork-action")}
              </article>
            </li>
          );
        })}
      </ul>
    );
  }

  const countText = filtersActive
    ? `Showing ${shown.length} of ${rows.length} ${rows.length === 1 ? "item" : "items"}`
    : `${rows.length} ${rows.length === 1 ? "item" : "items"}`;
  return (
    <section aria-labelledby="mywork-heading">
      <header className="mywork-header">
        <div>
          <h1 id="mywork-heading">My Work</h1>
          <p className="supporting">{entity ? entity.displayName : "All entities"}</p>
        </div>
        {data ? (
          <div className="mywork-header-actions">
            <p className="supporting">Updated {formatFetchTime(resource.loadedAt)}</p>
            <button
              type="button"
              onClick={resource.refresh}
              disabled={resource.status === "refreshing"}
            >
              Refresh My Work
            </button>
          </div>
        ) : null}
      </header>
      {entity ? (
        <SyncFreshness
          api={api}
          entity={entity}
          profile={profile}
          onChanged={resource.refresh}
          onAccessDenied={onAccessDenied}
        />
      ) : (
        <p className="supporting">Select an entity to see its Xero and vWork sync status.</p>
      )}
      {resource.status === "loading" ? <Loading label="Loading your work…" /> : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} label="Retry My Work" />
      ) : null}
      {resource.status === "refreshing" ? <p role="status">Refreshing your work…</p> : null}
      {data && counts ? (
        <div aria-busy={resource.status === "refreshing"}>
          {data.truncated ? (
            <Notice tone="warning">
              Only the first 500 items are shown. Narrow the entity selection to see more.
            </Notice>
          ) : null}
          <div className="mywork-summary" role="group" aria-label="Filter by work category">
            {categoryTiles.map((tile) => (
              <button
                key={tile.key}
                type="button"
                className="mywork-tile"
                aria-pressed={filters.category === tile.key}
                onClick={() => update({ category: tile.key })}
              >
                <span>{tile.label}</span> <strong>{counts[tile.key]}</strong>
              </button>
            ))}
          </div>
          <div className="mywork-toolbar">
            <div className="mywork-field">
              <label htmlFor={`${ids}-stage`}>Workflow stage</label>
              <select
                id={`${ids}-stage`}
                value={filters.stage}
                onChange={(event) => update({ stage: event.target.value as StageFilter })}
              >
                <option value="ALL">All stages</option>
                {stageOptions.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div className="mywork-field">
              <label htmlFor={`${ids}-attention`}>Attention status</label>
              <select
                id={`${ids}-attention`}
                value={filters.attention}
                onChange={(event) => update({ attention: event.target.value as AttentionFilter })}
              >
                {attentionOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="mywork-field">
              <label htmlFor={`${ids}-sort`}>Sort by</label>
              <select
                id={`${ids}-sort`}
                value={filters.sort}
                onChange={(event) => update({ sort: event.target.value as WorkSort })}
              >
                {sortOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            {filtersActive ? (
              <button type="button" onClick={() => setFilters(defaultFilters)}>
                Clear filters
              </button>
            ) : null}
          </div>
          <p className="supporting" aria-live="polite">
            {countText}
          </p>
          {rows.length === 0 ? (
            <Notice>No open work{entity ? ` in ${entity.displayName}` : ""} right now.</Notice>
          ) : shown.length === 0 ? (
            <Notice>
              No work matches these filters.{" "}
              <button type="button" onClick={() => setFilters(defaultFilters)}>
                Clear filters
              </button>
            </Notice>
          ) : compact ? (
            cards(shown)
          ) : (
            table(shown)
          )}
        </div>
      ) : null}
    </section>
  );
}
