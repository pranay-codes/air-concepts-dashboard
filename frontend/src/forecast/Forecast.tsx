import { useCallback, useRef, useState } from "react";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { yearOf } from "../api/dashboard";
import { isAccessFailure } from "../api/client";
import { useResource } from "../api/useResource";
import { useAction } from "../api/useAction";
import { Loading, Failure, Notice } from "../ui/Feedback";
import { formatMoney } from "../ui/format";
import { ForecastTable } from "./ForecastTable";
import { ForecastHeader } from "./ForecastHeader";
import { GenerationReview } from "./GenerationReview";
import { OpeningBalanceForm } from "./OpeningBalanceForm";
import { ExcelExportDialog } from "./ExcelExportDialog";
import { MonthDetail } from "./MonthDetail";
import { AssumptionLines } from "./AssumptionLines";
import { XeroItems } from "./XeroItems";
import { History } from "./History";
import { CombinedForecast } from "./CombinedForecast";
import { DocumentEditor, type AdjustmentMode } from "./AdjustmentDialog";
import type { ForecastMonth } from "./contracts";
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
  if (props.combined)
    return (
      <CombinedForecast
        key={`${props.profile.subject}:${props.entities
          .map((e) => e.id)
          .sort()
          .join(",")}`}
        api={props.api}
        entities={props.entities}
        subject={props.profile.subject}
        onAccessDenied={props.onAccessDenied}
      />
    );
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
function EntityForecast(props: Props & { entity: Entity }) {
  const { api, entity, onDirty: notifyDirty, onAccessDenied } = props;
  // null asks the server for the financial year that contains today; navigation then uses the year it reports.
  const [requestedYear, setRequestedYear] = useState<number | null>(null);
  const [tab, setTab] = useState<(typeof tabs)[number]>("Forecast");
  const [selectedMonth, setSelectedMonth] = useState<string | null>(null);
  const monthOpener = useRef<HTMLElement | null>(null);
  const [dialog, setDialog] = useState<"opening" | "export" | null>(null);
  const [openingMonth, setOpeningMonth] = useState<ForecastMonth | null>(null);
  const [document, setDocument] = useState<{ id: string; mode: AdjustmentMode } | null>(null);
  const [review, setReview] = useState<ForecastMonth | null>(null);
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
  const generation = useAction(`${props.profile.subject}:${props.entity.id}:${scope}:generate`);
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
    setSelectedMonth(null);
    setDialog(null);
    setDocument(null);
    setReview(null);
  }
  function confirmGeneration(month: ForecastMonth) {
    void generation
      .run(
        (signal) => props.api.generateForecastMonth(props.entity.id, month.month, signal),
        (error) => {
          if (isAccessFailure(error)) props.onAccessDenied();
        },
      )
      .then((result) => {
        if (result) {
          setReview(null);
          changed();
        }
      });
  }
  return (
    <div aria-busy={resource.status === "loading" || resource.status === "refreshing"}>
      <ForecastHeader
        entityName={props.entity.displayName}
        financialYearLabel={resource.data?.financialYearLabel ?? null}
        canStep={loadedYear !== null}
        onStep={(delta) => {
          if (loadedYear === null || !canLeave()) return;
          leaveCurrentView();
          setRequestedYear(loadedYear + delta);
        }}
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
      <nav className="forecast-tabs" aria-label="Forecast sections">
        {tabs.map((name) => (
          <button
            key={name}
            type="button"
            aria-pressed={tab === name}
            onClick={() => {
              if (!canLeave()) return;
              setTab(name);
              leaveCurrentView();
            }}
          >
            {name}
          </button>
        ))}
      </nav>
      {tab === "Forecast" ? (
        <div className="forecast-view">
          {resource.status === "loading" ? <Loading label="Loading forecast…" /> : null}
          {selectedMonth ? (
            <MonthDetail
              key={`${loadedYear}:${selectedMonth}:${revision}`}
              {...context}
              selectedMonth={selectedMonth}
              summary={resource.data?.months.find((row) => row.month === selectedMonth) ?? null}
              onBack={() => {
                setSelectedMonth(null);
                requestAnimationFrame(() => monthOpener.current?.focus());
              }}
            />
          ) : resource.data ? (
            <div className="forecast-overview">
              {review ? (
                <GenerationReview
                  month={review}
                  busy={generation.busy}
                  error={generation.error}
                  onConfirm={() => confirmGeneration(review)}
                  onCancel={() => setReview(null)}
                />
              ) : null}
              <ForecastTable
                months={resource.data.months}
                onOpen={(value) => {
                  monthOpener.current = window.document.activeElement as HTMLElement | null;
                  setSelectedMonth(value);
                }}
                onOpening={(month) => {
                  setOpeningMonth(month);
                  setDialog("opening");
                }}
                onGenerate={(month) => setReview(month)}
              />
              <p>
                Beyond this financial year: money in{" "}
                {formatMoney(resource.data.beyondWindow.receipts, "NZD")}, money out{" "}
                {formatMoney(resource.data.beyondWindow.payments, "NZD")}.
              </p>
              {resource.data.exclusions.length ? (
                <details className="forecast-panel">
                  <summary>Excluded documents ({resource.data.exclusions.length})</summary>
                  {resource.data.exclusions.map((row) => (
                    <p key={row.documentId}>
                      {row.number ?? row.documentId}: {row.reason}{" "}
                      {row.documentId ? (
                        <button
                          type="button"
                          onClick={() => setDocument({ id: row.documentId!, mode: "exclude" })}
                        >
                          Include again
                        </button>
                      ) : null}
                    </p>
                  ))}
                </details>
              ) : null}
            </div>
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
      <details className="forecast-panel">
        <summary>How this is calculated</summary>
        <p>
          Money in and money out use outstanding Xero balances and the forecast dates shown in each
          month. Split schedules replace the single document amount. Excluded items contribute
          nothing. Overdue cash is placed in the month that is current when that month is generated.
        </p>
        <p>
          A month is generated once it is the current month or the next month after the last
          generated month. Generating adds lines that are not yet in a forecast; it never changes
          lines already there. Once the following month is generated, a month is locked.
        </p>
        <p>
          Manual cash amounts and gross staffing costs are GST-inclusive where GST applies; tax is
          never added again. Each closing balance becomes the next opening balance. Missing balances
          are shown as Not recorded, and months not yet generated show Not generated.
        </p>
      </details>
      {dialog === "opening" && openingMonth && resource.data ? (
        <OpeningBalanceForm
          {...context}
          month={openingMonth.month}
          initial={openingMonth.opening}
          source={
            openingMonth.month === resource.data.financialYearStart
              ? resource.data.openingBalanceSource
              : null
          }
          onClose={() => {
            setDialog(null);
            setOpeningMonth(null);
          }}
        />
      ) : null}
      {dialog === "export" && loadedYear !== null ? (
        <ExcelExportDialog
          {...context}
          financialYear={loadedYear}
          onClose={() => setDialog(null)}
        />
      ) : null}
      {document ? (
        <DocumentEditor
          {...context}
          documentId={document.id}
          mode={document.mode}
          onClose={() => setDocument(null)}
        />
      ) : null}
    </div>
  );
}
