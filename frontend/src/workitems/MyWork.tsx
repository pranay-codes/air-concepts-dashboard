import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatFetchTime } from "../ui/format";
import { JobDetail } from "./JobDetail";
import { SyncFreshness } from "./SyncFreshness";
import type { WorkItem } from "./contracts";
import { stageLabels } from "./stageText";

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
  const restore = useRef<string | null>(null);
  const [previousEntity, setPreviousEntity] = useState(entity?.id ?? null);
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
  const section = (title: string, items: WorkItem[]) => (
    <section aria-label={title}>
      <h2>{title}</h2>
      {items.length === 0 ? (
        <p>No items in this section.</p>
      ) : (
        <ul className="mywork-list">
          {items.map((item) => {
            const action = actionLabels[item.nextAction ?? ""] ?? "Review";
            return (
              <li key={item.id} className="card">
                <p>
                  <strong>{item.quoteNumber}</strong> · {item.jobId || "Job ID not recorded"} ·{" "}
                  {item.customerName || "Customer not recorded"}
                </p>
                <p>
                  {item.entityName} · {stageLabels[item.stage]}{" "}
                  {item.late ? <span className="badge warning">Late</span> : null}
                </p>
                <p>
                  {item.daysInStage} {item.daysInStage === 1 ? "day" : "days"} in stage
                </p>
                <button
                  id={`mywork-action-${item.id}`}
                  type="button"
                  onClick={() => open(item)}
                  aria-label={`${action} ${item.quoteNumber}`}
                >
                  {action}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
  return (
    <section aria-labelledby="mywork-heading">
      <h1 id="mywork-heading">My Work</h1>
      <p>{entity ? entity.displayName : "All entities"}</p>
      {entity ? (
        <SyncFreshness
          api={api}
          entity={entity}
          profile={profile}
          onChanged={resource.refresh}
          onAccessDenied={onAccessDenied}
        />
      ) : null}
      {resource.status === "loading" ? <Loading label="Loading your work…" /> : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} label="Retry My Work" />
      ) : null}
      {resource.status === "refreshing" ? <p role="status">Refreshing your work…</p> : null}
      {resource.data ? (
        <>
          <p className="supporting">Dashboard data fetched {formatFetchTime(resource.loadedAt)}.</p>
          {resource.data.truncated ? (
            <Notice tone="warning">
              Only the first 500 items are shown. Narrow the entity selection to see more.
            </Notice>
          ) : null}
          <p aria-live="polite">
            {resource.data.needsAction.length} need your action, {resource.data.waiting.length}{" "}
            waiting, {resource.data.needsAttention.length} need attention.
          </p>
          {section("Needs attention", resource.data.needsAttention)}
          {section("Needs your action", resource.data.needsAction)}
          {section("Waiting", resource.data.waiting)}
          <button
            type="button"
            onClick={resource.refresh}
            disabled={resource.status === "refreshing"}
          >
            Refresh My Work
          </button>
        </>
      ) : null}
    </section>
  );
}
