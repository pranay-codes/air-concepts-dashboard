import { useCallback, useState, type KeyboardEvent } from "react";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { yearOf } from "../api/dashboard";
import { isAccessFailure } from "../api/client";
import { useResource } from "../api/useResource";
import { useAction } from "../api/useAction";
import { Loading, Failure, Notice } from "../ui/Feedback";
import { ForecastGrid } from "./ForecastGrid";
import { ForecastHeader } from "./ForecastHeader";
import { ExcelExportDialog } from "./ExcelExportDialog";
import { AssumptionLines } from "./AssumptionLines";
import { XeroItems } from "./XeroItems";
import { History } from "./History";
import type { ForecastContext } from "./context";
interface Props {
  api: DashboardApi;
  profile: Profile;
  entity: Entity | null;
  entities: Entity[];
  combined: boolean;
  onDirty(dirty: boolean): void;
  onAccessDenied(): void;
}
export default function Forecast(props: Props) {
  if (props.profile.role === "STANDARD_USER")
    return <Notice>Forecast access is restricted to Director and Financial Admin.</Notice>;
  if (props.combined) return <Notice>Select an entity to view its forecast.</Notice>;
  if (!props.entity)
    return <Notice>No entities are available. Ask a Director to configure an entity.</Notice>;
  return (
    <EntityForecast
      key={`${props.profile.subject}:${props.profile.role}:${props.entity.id}`}
      {...props}
      entity={props.entity}
    />
  );
}
const tabs = ["Forecast", "Xero items", "Manual items", "Staffing", "History"] as const;
type ForecastTab = (typeof tabs)[number];
const tabId = (name: ForecastTab) => `forecast-tab-${name.toLowerCase().replace(/\s+/g, "-")}`;
function EntityForecast(props: Props & { entity: Entity }) {
  const { api, entity, onDirty: notifyDirty, onAccessDenied } = props;
  // The server selects the current financial year.
  const requestedYear = null;
  const [tab, setTab] = useState<(typeof tabs)[number]>("Forecast");
  const [dialog, setDialog] = useState<"export" | null>(null);
  const [revision, setRevision] = useState(0);
  const [dirty, setDirty] = useState(false);
  const onDirty = useCallback(
    (value: boolean) => {
      setDirty(value);
      notifyDirty(value);
    },
    [notifyDirty],
  );
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.forecast(entity.id, requestedYear, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entity.id, requestedYear, onAccessDenied],
  );
  const scope = requestedYear ?? "current";
  const resource = useResource(`${props.profile.subject}:${props.entity.id}:${scope}:${tab}`, load);
  const action = useAction(`${props.profile.subject}:${props.entity.id}:${scope}:sync`);
  const loadedYear = resource.data ? yearOf(resource.data.financialYearStart) : requestedYear;
  function changed() {
    resource.refresh();
    setRevision((value) => value + 1);
  }
  const context: ForecastContext = {
    api: props.api,
    entity: props.entity,
    financialYear: loadedYear,
    financialYearLabel: resource.data?.financialYearLabel ?? "",
    subject: props.profile.subject,
    onDirty,
    onChanged: changed,
    onAccessDenied: props.onAccessDenied,
  };
  const canLeave = () => !dirty || window.confirm("Discard unsaved forecast changes?");
  function leaveCurrentView() {
    setDirty(false);
    props.onDirty(false);
    setDialog(null);
  }
  /** Switches section only after the unsaved-changes check passes; returns whether the section changed. */
  function selectTab(name: ForecastTab): boolean {
    if (!canLeave()) return false;
    setTab(name);
    leaveCurrentView();
    return true;
  }
  /** WAI tabs keyboard model: arrows wrap, Home and End jump to the ends, and focus follows the selection. */
  function tabKey(event: KeyboardEvent<HTMLButtonElement>) {
    const index = tabs.indexOf(tab);
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? tabs.length - 1
          : event.key === "ArrowRight"
            ? (index + 1) % tabs.length
            : event.key === "ArrowLeft"
              ? (index + tabs.length - 1) % tabs.length
              : -1;
    const name = tabs[next];
    if (!name) return;
    event.preventDefault();
    if (selectTab(name)) window.document.getElementById(tabId(name))?.focus();
  }
  return (
    <div aria-busy={resource.status === "loading" || resource.status === "refreshing"}>
      <ForecastHeader
        entityName={props.entity.displayName}
        lastSync={resource.data?.lastSuccessfulSyncAt}
        stale={resource.data?.syncStale}
        busy={action.busy}
        onSync={() => {
          void action
            .run(
              (signal) => props.api.syncForecast(props.entity.id, signal),
              (error) => {
                if (isAccessFailure(error)) props.onAccessDenied();
              },
            )
            .then((result) => {
              if (result) changed();
            });
        }}
        onExport={resource.data ? () => setDialog("export") : undefined}
      />
      {resource.status === "refreshing" ? <p role="status">Refreshing forecast…</p> : null}
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {resource.data?.lastRun?.status === "FAILED" ? (
        <Notice tone="warning">
          The latest Xero sync did not complete. Showing the last recorded forecast data.{" "}
          <button type="button" onClick={() => setTab("History")}>
            View sync history
          </button>
        </Notice>
      ) : null}
      {action.error ? (
        <Failure
          error={action.error}
          onRetry={() => setTab("History")}
          label="Check sync history"
        />
      ) : null}
      <div className="forecast-tabs" role="tablist" aria-label="Forecast sections">
        {tabs.map((name) => (
          <button
            key={name}
            type="button"
            role="tab"
            id={tabId(name)}
            aria-controls="forecast-section-panel"
            aria-selected={tab === name}
            tabIndex={tab === name ? 0 : -1}
            onClick={() => selectTab(name)}
            onKeyDown={tabKey}
          >
            {name}
          </button>
        ))}
      </div>
      <div id="forecast-section-panel" role="tabpanel" aria-labelledby={tabId(tab)}>
        {tab === "Forecast" ? (
          <div className="forecast-view">
            {resource.status === "loading" ? <Loading label="Loading forecast…" /> : null}
            {resource.data ? (
              <ForecastGrid
                api={api}
                entityId={entity.id}
                subject={props.profile.subject}
                revision={revision}
                onAccessDenied={onAccessDenied}
                onChanged={changed}
              />
            ) : null}
          </div>
        ) : null}
        {tab === "Xero items" ? (
          <XeroItems key={loadedYear} {...context} revision={revision} />
        ) : null}
        {tab === "Manual items" ? (
          <AssumptionLines
            key={`${loadedYear}:manual`}
            {...context}
            kind="manual"
            revision={revision}
          />
        ) : null}
        {tab === "Staffing" ? (
          <AssumptionLines
            key={`${loadedYear}:staffing`}
            {...context}
            kind="staffing"
            revision={revision}
          />
        ) : null}
        {tab === "History" ? <History {...context} revision={revision} /> : null}
      </div>
      {dialog === "export" && loadedYear !== null ? (
        <ExcelExportDialog
          {...context}
          financialYear={loadedYear}
          onClose={() => setDialog(null)}
        />
      ) : null}
    </div>
  );
}
