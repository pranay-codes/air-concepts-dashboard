import { useCallback, useEffect, useRef, useState } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatDate, formatFetchTime, formatMoney } from "../ui/format";
import type { WorkItem, WorkItemQuery } from "./contracts";
import { JobDetail } from "./JobDetail";
import { flagLabel, stageLabels } from "./stageText";

const initial = (): WorkItemQuery => ({
  page: 1,
  pageSize: 25,
  sort: "stageEnteredAt",
  direction: "ASC",
});
const ignoreDirty = () => {};
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
  const page = state.query.page ?? 1;
  const filtered = Boolean(state.query.search || state.query.stage || state.query.late);
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
                        <td>{item.owner?.displayName ?? "Unassigned"}</td>
                        <td>{formatDate(item.followUpDate)}</td>
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
                    <p>Owner: {item.owner?.displayName ?? "Unassigned"}</p>
                    <p>Follow-up: {formatDate(item.followUpDate)}</p>
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
