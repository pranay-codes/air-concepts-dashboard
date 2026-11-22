import { useCallback, useEffect, useRef, useState } from "react";
import { apiMessage, isAccessFailure } from "../api/client";
import type { Entity, Profile, StaffOption } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { useAction } from "../api/useAction";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatDate, formatFetchTime, formatMoney } from "../ui/format";
import type { WorkItem, WorkItemQuery } from "./contracts";
import { JobDetail } from "./JobDetail";
import { SyncFreshness } from "./SyncFreshness";
import { flagLabel, stageLabels } from "./stageText";

const initial = (): WorkItemQuery => ({
  page: 1,
  pageSize: 25,
  sort: "stageEnteredAt",
  direction: "ASC",
});
const ignoreDirty = () => {};

function FollowUp({
  item,
  api,
  profile,
  onChanged,
  onAccessDenied,
}: {
  item: WorkItem;
  api: DashboardApi;
  profile: Profile;
  onChanged(): void;
  onAccessDenied(): void;
}) {
  const [value, setValue] = useState(item.followUpDate ?? "");
  const action = useAction(`${profile.subject}:${item.entityId}:${item.id}`);
  const canEdit =
    item.stage !== "CLOSED" &&
    (profile.role === "DIRECTOR" ||
      profile.role === "FINANCIAL_ADMIN" ||
      item.owner?.id === profile.subject);
  const todayParts = new Intl.DateTimeFormat("en-NZ", {
    timeZone: "Pacific/Auckland",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const today = ["year", "month", "day"]
    .map((part) => todayParts.find((entry) => entry.type === part)?.value)
    .join("-");
  const urgency =
    item.followUpDate && item.followUpDate <= today
      ? item.followUpDate === today
        ? "Due today"
        : "Overdue"
      : null;
  async function save(next: string) {
    if (next === (item.followUpDate ?? "")) return;
    setValue(next);
    const result = await action.run(
      (signal) =>
        api.updateWorkItem(
          item.entityId,
          item.id,
          next
            ? { version: item.version, followUpDate: next }
            : { version: item.version, clearFollowUp: true },
          signal,
        ),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) onChanged();
  }
  return (
    <div>
      {canEdit ? (
        <input
          className="date-compact"
          aria-label={`Follow-up date for ${item.quoteNumber}`}
          type="date"
          value={value}
          disabled={action.busy}
          onChange={(event) => {
            // A partly typed date reports an empty value with badInput; wait for a complete date.
            if (event.target.validity.badInput) {
              setValue(event.target.value);
              return;
            }
            void save(event.target.value);
          }}
        />
      ) : (
        formatDate(item.followUpDate)
      )}
      {urgency ? (
        <span className={`badge ${urgency === "Overdue" ? "error" : "warning"}`}>{urgency}</span>
      ) : null}
      {action.error ? <p role="alert">{apiMessage(action.error)}</p> : null}
    </div>
  );
}
function OwnerSelect({
  item,
  choices,
  api,
  profile,
  onChanged,
  onAccessDenied,
}: {
  item: WorkItem;
  choices: StaffOption[];
  api: DashboardApi;
  profile: Profile;
  onChanged(): void;
  onAccessDenied(): void;
}) {
  const action = useAction(`owner:${profile.subject}:${item.entityId}:${item.id}`);
  const current = item.owner?.id ?? "";
  // Keep the current owner selectable even if they are no longer an assignment choice.
  const options =
    item.owner && !choices.some((choice) => choice.id === item.owner?.id)
      ? [...choices, { id: item.owner.id, displayName: item.owner.displayName }]
      : choices;
  async function change(next: string) {
    if (next === current) return;
    const result = await action.run(
      (signal) =>
        api.updateWorkItem(
          item.entityId,
          item.id,
          next
            ? { version: item.version, ownerId: next }
            : { version: item.version, clearOwner: true },
          signal,
        ),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) onChanged();
  }
  return (
    <div>
      <select
        aria-label={`Owner for ${item.quoteNumber}`}
        value={current}
        disabled={action.busy}
        onChange={(event) => void change(event.target.value)}
      >
        <option value="">Unassigned</option>
        {options.map((choice) => (
          <option key={choice.id} value={choice.id}>
            {choice.displayName}
          </option>
        ))}
      </select>
      {action.error ? <p role="alert">{apiMessage(action.error)}</p> : null}
    </div>
  );
}
export function JobsList({
  api,
  entity,
  profile,
  onAccessDenied,
  onDirty = ignoreDirty,
}: {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  onAccessDenied(): void;
  onDirty?(value: boolean): void;
}) {
  const scope = `${profile.subject}:${profile.role}:${entity.id}`;
  const [state, setState] = useState(() => ({ scope, query: initial(), search: "" }));
  const [selection, setSelection] = useState<{ scope: string; id: string } | null>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  if (state.scope !== scope) setState({ scope, query: initial(), search: "" });
  useEffect(() => {
    const timer = setTimeout(
      () =>
        setState((current) =>
          current.scope !== scope || (current.query.search ?? "") === current.search.trim()
            ? current
            : { ...current, query: { ...current.query, search: current.search.trim(), page: 1 } },
        ),
      300,
    );
    return () => clearTimeout(timer);
  }, [scope, state.search]);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.workItems(entity.id, state.query, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, state.query, onAccessDenied],
  );
  const resource = useResource(JSON.stringify([scope, state.query]), load, state.scope === scope);
  const canAssignOwner = profile.role === "DIRECTOR" || profile.role === "FINANCIAL_ADMIN";
  const loadChoices = useCallback((signal: AbortSignal) => api.staff(signal), [api]);
  const choices = useResource(`owner-choices:${profile.subject}`, loadChoices, canAssignOwner);
  const ownerCell = (item: WorkItem) =>
    canAssignOwner && item.stage !== "CLOSED" ? (
      <OwnerSelect
        item={item}
        choices={choices.data ?? []}
        api={api}
        profile={profile}
        onChanged={resource.refresh}
        onAccessDenied={onAccessDenied}
      />
    ) : (
      (item.owner?.displayName ?? "Unassigned")
    );
  const page = state.query.page ?? 1;
  const filtered = Boolean(
    state.query.search ||
    state.query.stage ||
    state.query.late ||
    state.query.xeroStatus ||
    state.query.followUpDueBy,
  );
  const change = (patch: Partial<WorkItemQuery>) =>
    setState((current) => ({ ...current, query: { ...current.query, ...patch, page: 1 } }));
  const sort = (field: NonNullable<WorkItemQuery["sort"]>) =>
    change({
      sort: field,
      direction: state.query.sort === field && state.query.direction === "ASC" ? "DESC" : "ASC",
    });
  const heading = (text: string, field: NonNullable<WorkItemQuery["sort"]>) => (
    <th
      scope="col"
      aria-sort={
        state.query.sort === field
          ? state.query.direction === "DESC"
            ? "descending"
            : "ascending"
          : undefined
      }
    >
      <button type="button" className="sort-button" onClick={() => sort(field)}>
        {text}
        {state.query.sort === field ? (state.query.direction === "DESC" ? " ↓" : " ↑") : ""}
      </button>
    </th>
  );
  const flags = (item: WorkItem) =>
    item.flags.map((flag) => (
      <span className="badge warning" key={flag}>
        {flagLabel(flag)}
      </span>
    ));
  const selected = selection?.scope === scope ? selection.id : null;
  if (selected)
    return (
      <JobDetail
        key={`${scope}:${selected}`}
        api={api}
        entity={entity}
        profile={profile}
        itemId={selected}
        onDirty={onDirty}
        onAccessDenied={onAccessDenied}
        onChanged={resource.refresh}
        onBack={() => {
          setSelection(null);
          requestAnimationFrame(() => trigger.current?.focus());
        }}
      />
    );
  const open = (item: WorkItem) => (
    <button
      type="button"
      className="text-button"
      onClick={(event) => {
        trigger.current = event.currentTarget;
        setSelection({ scope, id: item.id });
      }}
    >
      {item.quoteNumber}
    </button>
  );
  return (
    <section aria-labelledby="jobs-heading">
      <h1 id="jobs-heading">Service jobs</h1>
      <p>Quotes and jobs for {entity.displayName}</p>
      <SyncFreshness
        api={api}
        entity={entity}
        profile={profile}
        onChanged={resource.refresh}
        onAccessDenied={onAccessDenied}
      />
      <p className="supporting">
        Last dashboard fetch: {formatFetchTime(resource.loadedAt)}. Xero sync time is not available.
      </p>
      <div className="filter-grid">
        <label>
          Search jobs
          <input
            type="search"
            value={state.search}
            maxLength={200}
            onChange={(event) => setState((s) => ({ ...s, search: event.target.value }))}
          />
        </label>
        <label>
          Xero status
          <select
            value={state.query.xeroStatus ?? ""}
            onChange={(event) =>
              change({
                xeroStatus: (event.target.value || undefined) as WorkItemQuery["xeroStatus"],
              })
            }
          >
            <option value="">All statuses</option>
            {["DRAFT", "SENT", "ACCEPTED", "DECLINED", "INVOICED", "DELETED"].map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
        </label>
        <label>
          Follow-up due by
          <input
            type="date"
            value={state.query.followUpDueBy ?? ""}
            onChange={(event) => change({ followUpDueBy: event.target.value || undefined })}
          />
        </label>
        <label>
          Stage
          <select
            value={state.query.stage ?? ""}
            onChange={(event) =>
              change({ stage: (event.target.value || undefined) as WorkItemQuery["stage"] })
            }
          >
            <option value="">All stages</option>
            {Object.entries(stageLabels).map(([key, value]) => (
              <option key={key} value={key}>
                {value}
              </option>
            ))}
          </select>
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={Boolean(state.query.late)}
            onChange={(event) => change({ late: event.target.checked || undefined })}
          />{" "}
          Late only
        </label>
        {filtered || state.search ? (
          <button type="button" onClick={() => setState({ scope, query: initial(), search: "" })}>
            Clear filters
          </button>
        ) : null}
        <button
          type="button"
          onClick={resource.refresh}
          disabled={resource.status === "loading" || resource.status === "refreshing"}
        >
          Refresh jobs
        </button>
      </div>
      {resource.status === "loading" ? <Loading label="Loading jobs…" /> : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} label="Retry jobs" />
      ) : null}
      {resource.status === "refreshing" ? <p role="status">Refreshing jobs…</p> : null}
      {resource.data ? (
        <>
          <p aria-live="polite">
            {resource.data.total} {resource.data.total === 1 ? "job" : "jobs"} found
          </p>
          {resource.data.total === 0 ? (
            <Notice>
              {filtered
                ? "No jobs match these filters. Clear filters to see more."
                : "No jobs are available for this entity."}
            </Notice>
          ) : null}
          {resource.data.items.length ? (
            <>
              <div
                className="invoice-table-wrap"
                role="region"
                aria-label="Service jobs table"
                tabIndex={0}
              >
                <table className="financial-table">
                  <caption>Service jobs</caption>
                  <thead>
                    <tr>
                      {heading("Quote", "quoteNumber")}
                      <th scope="col">Job ID</th>
                      {heading("Customer", "customer")}
                      <th scope="col">Entity</th>
                      {heading("Total", "total")}
                      <th scope="col">Xero status</th>
                      <th scope="col">Stage</th>
                      <th scope="col">Owner</th>
                      {heading("Follow-up", "followUpDate")}
                    </tr>
                  </thead>
                  <tbody>
                    {resource.data.items.map((item) => (
                      <tr key={item.id}>
                        <th scope="row">{open(item)}</th>
                        <td>
                          {item.jobId || "Not recorded"} {flags(item)}
                        </td>
                        <td>{item.customerName || "Not recorded"}</td>
                        <td>{item.entityName}</td>
                        <td className="money">{formatMoney(item.quoteTotal, item.currency)}</td>
                        <td>{item.xeroQuoteStatus}</td>
                        <td>
                          <span className="badge information">{stageLabels[item.stage]}</span>{" "}
                          {item.attention.length ? (
                            <span className="badge warning">Needs attention</span>
                          ) : null}
                        </td>
                        <td>{ownerCell(item)}</td>
                        <td>
                          <FollowUp
                            key={`${item.id}:${item.version}`}
                            item={item}
                            api={api}
                            profile={profile}
                            onChanged={resource.refresh}
                            onAccessDenied={onAccessDenied}
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="invoice-cards">
                {resource.data.items.map((item) => (
                  <article className="card invoice-card" key={item.id}>
                    <h3>{open(item)}</h3>
                    <p>{item.customerName || "Customer not recorded"}</p>
                    <p>
                      {item.entityName} · {stageLabels[item.stage]}
                    </p>
                    {item.attention.length ? (
                      <p>
                        <span className="badge warning">Needs attention</span>
                      </p>
                    ) : null}
                    <p>
                      Job ID: {item.jobId || "Not recorded"} {flags(item)}
                    </p>
                    <p>Total: {formatMoney(item.quoteTotal, item.currency)}</p>
                    <div>Owner: {ownerCell(item)}</div>
                    <div>
                      Follow-up:{" "}
                      <FollowUp
                        key={`${item.id}:${item.version}`}
                        item={item}
                        api={api}
                        profile={profile}
                        onChanged={resource.refresh}
                        onAccessDenied={onAccessDenied}
                      />
                    </div>
                  </article>
                ))}
              </div>
            </>
          ) : null}
          <nav className="pagination" aria-label="Jobs pagination">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => setState((s) => ({ ...s, query: { ...s.query, page: page - 1 } }))}
            >
              Previous
            </button>
            <span>
              Page {page} of {Math.max(1, resource.data.totalPages)}
            </span>
            <button
              type="button"
              disabled={page >= resource.data.totalPages}
              onClick={() => setState((s) => ({ ...s, query: { ...s.query, page: page + 1 } }))}
            >
              Next
            </button>
          </nav>
        </>
      ) : null}
    </section>
  );
}
