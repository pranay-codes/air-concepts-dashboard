import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from "react";
import { ApiError, isAccessFailure } from "../api/client";
import {
  defaultQuery,
  type Entity,
  type InvoiceQuery,
  type InvoiceType,
  type Profile,
} from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { useAction } from "../api/useAction";
import { AssignmentPicker } from "./AssignmentPicker";
import { Failure, Notice } from "../ui/Feedback";
import { dateRange, formatFetchTime, type DatePreset } from "../ui/format";
import { InvoiceFilters } from "./InvoiceFilters";
import { InvoiceList } from "./InvoiceList";
import { InvoiceDetailPanel } from "./InvoiceDetailPanel";

interface Props {
  api: DashboardApi;
  entity: Entity;
  profile: Profile;
  onAccessDenied(): void;
  onSettings(): void;
}
type State = {
  scope: string;
  query: InvoiceQuery;
  search: string;
  preset: DatePreset;
  selected: string | null;
};
const initial = (scope: string): State => ({
  scope,
  query: defaultQuery("ACCREC"),
  search: "",
  preset: "ALL_DATES",
  selected: null,
});
export function Invoices({ api, entity, profile, onAccessDenied, onSettings }: Props) {
  const scope = `${profile.subject}:${profile.role}:${entity.id}`;
  const [state, setState] = useState(() => initial(scope));
  const [assignmentVersion, setAssignmentVersion] = useState(0);
  const [assignmentNotice, setAssignmentNotice] = useState<{
    scope: string;
    message: string;
  } | null>(null);
  const assignment = useAction(`assignment:${scope}`);
  const assignmentFocus = useRef<{ id: string; detail: boolean } | null>(null);
  if (state.scope !== scope) setState(initial(scope));
  const trigger = useRef<HTMLButtonElement | null>(null);
  const scroll = useRef(0);
  const frame = useRef<number | undefined>(undefined);
  useEffect(
    () => () => {
      if (frame.current !== undefined) cancelAnimationFrame(frame.current);
    },
    [],
  );
  const canBills = profile.role !== "STANDARD_USER";
  const type = state.query.type ?? "ACCREC";
  const invalidRange = Boolean(
    state.query.from && state.query.to && state.query.from > state.query.to,
  );
  useEffect(() => {
    const timer = setTimeout(
      () =>
        setState((current) =>
          current.scope !== scope || current.query.search === state.search.trim()
            ? current
            : {
                ...current,
                query: { ...current.query, search: state.search.trim(), page: 1 },
                selected: null,
              },
        ),
      300,
    );
    return () => clearTimeout(timer);
  }, [scope, state.search]);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.invoices(entity.id, state.query, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, state.query, onAccessDenied],
  );
  const resource = useResource(
    JSON.stringify([scope, state.query, assignmentVersion]),
    load,
    state.scope === scope && !invalidRange && (type === "ACCREC" || canBills),
  );
  const loadStaff = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.staff(signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, onAccessDenied],
  );
  const canAssign = profile.role !== "STANDARD_USER" && type === "ACCREC";
  const staff = useResource(`assignable:${scope}`, loadStaff, canAssign);
  const restoreAssignmentFocus = useCallback(() => {
    const target = assignmentFocus.current;
    if (!target) return;
    const elements = Array.from(
      document.querySelectorAll<HTMLSelectElement>(`[data-assignment-invoice="${target.id}"]`),
    );
    const control = elements.find(
      (element) =>
        Boolean(element.closest("#invoice-detail")) === target.detail &&
        element.getClientRects().length > 0,
    );
    if (control) {
      control.focus({ preventScroll: true });
      assignmentFocus.current = null;
    }
  }, []);
  useEffect(() => {
    if (resource.status === "success") restoreAssignmentFocus();
  }, [resource.status, resource.data, restoreAssignmentFocus]);
  async function assign(invoiceId: string, staffId: string | null) {
    if (!canAssign || assignment.busy) return;
    assignmentFocus.current = {
      id: invoiceId,
      detail: Boolean(document.activeElement?.closest("#invoice-detail")),
    };
    const result = await assignment.run(
      (signal) => api.assign(entity.id, invoiceId, staffId, signal),
      (error) => {
        if (isAccessFailure(error)) onAccessDenied();
      },
    );
    if (result) {
      setAssignmentNotice({
        scope,
        message: `Assignment saved: ${result.owner?.displayName ?? "Unassigned"}.`,
      });
      setAssignmentVersion((value) => value + 1);
    }
  }
  const pending = resource.status === "loading" || resource.status === "refreshing";
  const data = resource.data;
  function change(patch: Partial<InvoiceQuery>) {
    setState((current) => ({
      ...current,
      query: { ...current.query, ...patch, page: 1 },
      selected: null,
    }));
  }
  function selectType(value: InvoiceType) {
    if (value === "ACCPAY" && !canBills) return;
    change({ type: value });
  }
  function tabKey(event: KeyboardEvent<HTMLButtonElement>) {
    const types: InvoiceType[] = canBills ? ["ACCREC", "ACCPAY"] : ["ACCREC"];
    const index = types.indexOf(type);
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? types.length - 1
          : event.key === "ArrowRight"
            ? (index + 1) % types.length
            : event.key === "ArrowLeft"
              ? (index + types.length - 1) % types.length
              : -1;
    const value = types[next];
    if (!value) return;
    event.preventDefault();
    selectType(value);
    document.getElementById(`invoice-tab-${value}`)?.focus();
  }
  function back() {
    setState((current) => ({ ...current, selected: null }));
    frame.current = requestAnimationFrame(() => {
      if (trigger.current?.isConnected) {
        trigger.current.focus({ preventScroll: true });
        window.scrollTo({ top: scroll.current });
      }
    });
  }
  const page = state.query.page ?? 1;
  const size = state.query.pageSize ?? 25;
  const filtered = Boolean(
    state.query.search ||
    state.query.status ||
    state.query.overdue ||
    state.query.from ||
    state.query.to,
  );
  const unavailablePage = Boolean(data && data.itemCount > 0 && page > data.pageCount);
  return (
    <section aria-labelledby="invoices-title">
      <h1 id="invoices-title">Invoices</h1>
      <p className="supporting">Review live Xero records for {entity.displayName}.</p>
      <div role="tablist" aria-label="Invoice type" className="tabs">
        {(["ACCREC", ...(canBills ? ["ACCPAY"] : [])] as InvoiceType[]).map((value) => (
          <button
            type="button"
            role="tab"
            id={`invoice-tab-${value}`}
            key={value}
            aria-controls="invoice-results"
            aria-selected={type === value}
            tabIndex={type === value ? 0 : -1}
            onClick={() => selectType(value)}
            onKeyDown={tabKey}
          >
            {value === "ACCREC" ? "Sales invoices" : "Supplier bills"}
          </button>
        ))}
      </div>
      {assignmentNotice?.scope === scope ? (
        <Notice tone="success">{assignmentNotice.message}</Notice>
      ) : null}
      {assignment.busy ? <Notice>Saving assignment…</Notice> : null}
      {assignment.error ? <Failure error={assignment.error} /> : null}
      {canAssign && staff.status === "error" ? (
        <Failure error={staff.error} onRetry={staff.refresh} label="Retry active staff" />
      ) : null}
      <div className={`invoice-layout${state.selected ? " has-detail" : ""}`}>
        <div
          className="list-pane"
          id="invoice-results"
          role="tabpanel"
          aria-labelledby={`invoice-tab-${type}`}
        >
          <InvoiceFilters
            query={state.query}
            search={state.search}
            preset={state.preset}
            invalidRange={invalidRange}
            onChange={change}
            onSearch={(search) => setState((current) => ({ ...current, search }))}
            onPreset={(preset) =>
              setState((current) => ({
                ...current,
                preset,
                query: {
                  ...current.query,
                  ...(preset === "CUSTOM"
                    ? {}
                    : { from: undefined, to: undefined, ...dateRange(preset) }),
                  page: 1,
                },
                selected: null,
              }))
            }
            onClear={() => setState({ ...initial(scope), query: defaultQuery(type) })}
          />
          <div className="section-heading">
            <h2>{type === "ACCREC" ? "Sales invoices" : "Supplier bills"}</h2>
            <button
              type="button"
              disabled={
                pending ||
                (resource.status === "error" &&
                  resource.error instanceof ApiError &&
                  resource.error.retryAfterSeconds !== undefined)
              }
              onClick={resource.refresh}
            >
              Refresh invoices
            </button>
          </div>
          <p className="supporting">
            Dashboard fetch: {formatFetchTime(resource.loadedAt)}. Source: Xero.
          </p>
          {resource.status === "refreshing" ? (
            <Notice tone="warning">
              Refreshing. Values shown are from the last successful fetch.
            </Notice>
          ) : null}
          {resource.status === "error" ? (
            <>
              <Failure error={resource.error} onRetry={resource.refresh} label="Retry invoices" />
              {profile.role === "DIRECTOR" &&
              resource.error instanceof ApiError &&
              resource.error.code.startsWith("XERO_") ? (
                <button type="button" onClick={onSettings}>
                  Check Xero connection
                </button>
              ) : null}
            </>
          ) : null}
          <p role="status" aria-live="polite">
            {pending
              ? "Fetching matching records…"
              : data
                ? `${data.itemCount} matching ${data.itemCount === 1 ? "record" : "records"}${unavailablePage ? "; the requested page is no longer available" : ""}.`
                : ""}
          </p>
          <div aria-busy={pending}>
            <InvoiceList
              renderOwner={
                canAssign
                  ? (record) => (
                      <AssignmentPicker
                        invoice={record}
                        role={profile.role}
                        options={staff.data ?? []}
                        disabled={assignment.busy || staff.status !== "success"}
                        onAssign={(id, target) => void assign(id, target)}
                      />
                    )
                  : undefined
              }
              items={data?.items ?? []}
              type={type}
              sort={state.query.sort ?? "DATE"}
              direction={state.query.direction ?? "DESC"}
              selected={state.selected}
              pending={resource.status === "loading"}
              onSort={(sort) =>
                change({
                  sort,
                  direction:
                    sort === state.query.sort && state.query.direction === "ASC" ? "DESC" : "ASC",
                })
              }
              onOpen={(id, element) => {
                trigger.current = element;
                scroll.current = window.scrollY;
                setState((current) => ({ ...current, selected: id }));
              }}
            />
          </div>
          {data && !pending && data.items.length === 0 ? (
            <Notice>
              {unavailablePage ? (
                <>
                  Results changed.{" "}
                  <button type="button" onClick={() => change({})}>
                    Return to first page
                  </button>
                </>
              ) : filtered ? (
                "No records match these filters. Clear filters to see all dates and statuses."
              ) : (
                "No records are available for this entity. Check Xero or refresh when new records are available."
              )}
            </Notice>
          ) : null}
          <div className="pagination" aria-label="Invoice pagination">
            <label htmlFor="invoice-page-size">
              Rows per page
              <select
                id="invoice-page-size"
                value={size}
                onChange={(event) => change({ pageSize: Number(event.target.value) })}
              >
                {[10, 25, 50, 100].map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </label>
            <span>
              {data && !unavailablePage
                ? `Page ${page} of ${Math.max(1, data.pageCount)}`
                : `Page ${page}`}
            </span>
            <button
              type="button"
              disabled={pending || page <= 1}
              onClick={() =>
                setState((current) => ({
                  ...current,
                  query: { ...current.query, page: page - 1 },
                  selected: null,
                }))
              }
            >
              Previous page
            </button>
            <button
              type="button"
              disabled={pending || !data || page >= data.pageCount}
              onClick={() =>
                setState((current) => ({
                  ...current,
                  query: { ...current.query, page: page + 1 },
                  selected: null,
                }))
              }
            >
              Next page
            </button>
          </div>
        </div>
        {state.selected && state.scope === scope ? (
          <InvoiceDetailPanel
            key={`${scope}:${type}:${state.selected}`}
            api={api}
            entity={entity}
            profile={profile}
            type={type}
            invoiceId={state.selected}
            onBack={back}
            onAccessDenied={onAccessDenied}
            assignmentVersion={assignmentVersion}
            assignmentOptions={staff.data ?? []}
            assignmentDisabled={assignment.busy || staff.status !== "success"}
            onAssign={canAssign ? (id, target) => void assign(id, target) : undefined}
            onReady={restoreAssignmentFocus}
          />
        ) : null}
      </div>
    </section>
  );
}
