import { useCallback, useId, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { isAccessFailure } from "../api/client";
import type { Entity, Profile } from "../api/contracts";
import type { DashboardApi } from "../api/dashboard";
import { useResource } from "../api/useResource";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { formatDate, formatFetchTime, formatMoney } from "../ui/format";
import { useMediaQuery } from "../ui/useMediaQuery";
import { businessToday } from "./myWorkModel";
import type {
  Overview,
  OverviewCycleTime,
  OverviewItem,
  OverviewMoney,
  OverviewPersonCount,
} from "./overviewContracts";
import { attentionLabel, stageLabels } from "./stageText";

const stepLabels: Record<OverviewCycleTime["step"], string> = {
  QUOTE_TO_ACCEPTED: "Quote dated to accepted",
  ACCEPTED_TO_SCHEDULED: "Accepted to job scheduled",
  QUOTE_TO_SCHEDULED: "Quote dated to job scheduled",
  SCHEDULED_TO_COMPLETED: "Job scheduled to completed",
  COMPLETED_TO_SENT: "Job completed to invoice sent",
};
const closeLabels: Record<string, string> = {
  PAID: "Paid",
  LOST: "Lost",
  NO_INVOICE_NEEDED: "No invoice needed",
};

type Period = { from: string; to: string };
type Column<T> = { header: string; cell(row: T): ReactNode };

function daysBefore(date: string, count: number): string {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() - count);
  return value.toISOString().slice(0, 10);
}

function periodProblem({ from, to }: Period): string | null {
  if (!from || !to) return "Enter both a start date and an end date.";
  if (from > to) return "The start date must be on or before the end date.";
  if (from <= daysBefore(to, 366)) return "Choose a period of 366 days or less.";
  return null;
}

const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? "" : "s"}`;
const amounts = (totals: OverviewMoney[]) =>
  totals.length
    ? totals.map((total) => formatMoney(total.amount, total.currency)).join(" + ")
    : "No value recorded";

/** One table on wider screens, one card per row on narrow ones. The first column names the row. */
function Records<T>({
  label,
  columns,
  rows,
  rowKey,
}: {
  label: string;
  columns: Column<T>[];
  rows: T[];
  rowKey(row: T): string;
}) {
  const compact = useMediaQuery("(width < 48rem)");
  if (compact)
    return (
      <ul className="mywork-cards" aria-label={label}>
        {rows.map((row) => (
          <li key={rowKey(row)} className="card mywork-card">
            <dl className="mywork-facts">
              {columns.map((column) => (
                <div key={column.header}>
                  <dt>{column.header}</dt>
                  <dd>{column.cell(row)}</dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
      </ul>
    );
  return (
    <div className="mywork-table-wrap" role="group" aria-label={label} tabIndex={0}>
      <table className="mywork-table">
        <caption className="visually-hidden">{label}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.header} scope="col">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((column, index) =>
                index === 0 ? (
                  <th key={column.header} scope="row">
                    {column.cell(row)}
                  </th>
                ) : (
                  <td key={column.header}>{column.cell(row)}</td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  const heading = useId();
  return (
    <section className="overview-section" aria-labelledby={heading}>
      <h2 id={heading}>{title}</h2>
      {note ? <p className="supporting">{note}</p> : null}
      {children}
    </section>
  );
}

const jobColumn: Column<OverviewItem> = {
  header: "Job",
  cell: ({ summary }) => (
    <>
      <span>{summary.quoteNumber}</span>
      <span className="supporting">
        {summary.jobId || "Job ID not recorded"} · {summary.entityName}
      </span>
    </>
  ),
};
const customerColumn: Column<OverviewItem> = {
  header: "Customer",
  cell: ({ summary }) => summary.customerName || "Customer not recorded",
};
const openColumns: Column<OverviewItem>[] = [
  jobColumn,
  customerColumn,
  {
    header: "Stage",
    cell: ({ summary }) => (
      <>
        <span>{stageLabels[summary.stage]}</span>
        <span className="supporting">{plural(summary.daysInStage, "day")} in stage</span>
      </>
    ),
  },
  { header: "Technician", cell: (item) => item.technician ?? "Not recorded" },
  {
    header: "Flags",
    cell: ({ summary }) => (
      <>
        {summary.late ? <span className="badge error">Late</span> : null}
        {summary.attention.map((code) => (
          <span key={code} className="supporting">
            {attentionLabel(code, summary.stage)}
          </span>
        ))}
        {!summary.late && !summary.attention.length ? "None" : null}
      </>
    ),
  },
];

function waitingColumns(
  value: string,
  amount: (item: OverviewItem) => string | undefined,
): Column<OverviewItem>[] {
  return [
    jobColumn,
    customerColumn,
    { header: value, cell: (item) => formatMoney(amount(item), item.summary.currency) },
    { header: "Waiting", cell: ({ summary }) => plural(summary.daysInStage, "day") },
    { header: "Owner", cell: ({ summary }) => summary.owner?.displayName ?? "Unassigned" },
  ];
}

const personColumns = (header: string): Column<OverviewPersonCount>[] => [
  { header, cell: (person) => person.name },
  { header: "Jobs completed", cell: (person) => person.count },
];

function excluded(row: OverviewCycleTime): string {
  const reasons = [
    [row.excludedBackfilled, "imported"],
    [row.excludedNoStart, "with no start date"],
    [row.excludedAlreadyComplete, "already complete when first seen"],
    [row.excludedOutOfOrder, "out of order"],
  ] as const;
  return (
    reasons
      .filter(([count]) => count > 0)
      .map(([count, reason]) => `${count} ${reason}`)
      .join(", ") || "None"
  );
}

const cycleColumns: Column<OverviewCycleTime>[] = [
  { header: "Step", cell: (row) => stepLabels[row.step] },
  {
    header: "Typical (median)",
    cell: (row) => (row.medianDays == null ? "Not enough data" : `${row.medianDays} days`),
  },
  {
    header: "Average",
    cell: (row) => (row.averageDays == null ? "—" : `${row.averageDays} days`),
  },
  { header: "Jobs measured", cell: (row) => row.sampleSize },
  { header: "Left out", cell: excluded },
];

function owners(items: OverviewItem[]) {
  const groups = new Map<string, { name: string; items: OverviewItem[] }>();
  for (const item of items) {
    const key = item.summary.owner?.id ?? "unassigned";
    const group = groups.get(key) ?? {
      name: item.summary.owner?.displayName ?? "Unassigned",
      items: [],
    };
    group.items.push(item);
    groups.set(key, group);
  }
  return [...groups].sort(([, a], [, b]) => b.items.length - a.items.length);
}

function Waiting({
  title,
  rows,
  truncated,
  columns,
}: {
  title: string;
  rows: OverviewItem[];
  truncated: boolean;
  columns: Column<OverviewItem>[];
}) {
  return (
    <Section title={title} note="Open now, longest waiting first.">
      {truncated ? <Notice tone="warning">Only the oldest 500 are listed.</Notice> : null}
      {rows.length ? (
        <Records label={title} columns={columns} rows={rows} rowKey={(row) => row.summary.id} />
      ) : (
        <p>Nothing is waiting here.</p>
      )}
    </Section>
  );
}

function Report({ data }: { data: Overview }) {
  const sent = data.invoicesSent;
  const open = data.stages.reduce((total, stage) => total + stage.count, 0);
  const waiting = (stage: string) => data.stages.find((s) => s.stage === stage)?.count ?? 0;
  const tiles = [
    ["Open work", open],
    ["Completed, not invoiced", waiting("JOB_COMPLETED")],
    ["Invoices not sent", waiting("INVOICE_CREATED")],
    ["Jobs completed in period", data.completedTotal],
    ["Invoices sent in period", sent.count],
  ] as const;
  return (
    <>
      <dl className="overview-tiles">
        {tiles.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <Section title="Pipeline" note="Open work now, valued at the quote total.">
        <Records
          label="Open work by stage"
          columns={[
            { header: "Stage", cell: (row) => stageLabels[row.stage] },
            { header: "Items", cell: (row) => row.count },
            { header: "Quoted value", cell: (row) => amounts(row.totals) },
          ]}
          rows={data.stages}
          rowKey={(row) => row.stage}
        />
        <h3>Closed in this period</h3>
        {data.closed.length ? (
          <ul>
            {data.closed.map((row) => (
              <li key={row.reason}>
                {closeLabels[row.reason] ?? row.reason}: {row.count}
              </li>
            ))}
          </ul>
        ) : (
          <p>Nothing closed in this period.</p>
        )}
      </Section>
      <Section title="Who has what" note="Open work now, by the dashboard owner.">
        {data.openTruncated ? (
          <Notice tone="warning">
            Only the oldest 500 open items are listed, so counts by person are incomplete. Choose
            one entity to see more.
          </Notice>
        ) : null}
        {data.openItems.length ? (
          owners(data.openItems).map(([key, group]) => {
            const late = group.items.filter((item) => item.summary.late).length;
            return (
              <details key={key} className="overview-person">
                <summary>
                  {group.name}: {group.items.length} open{late ? `, ${late} late` : ""}
                </summary>
                <Records
                  label={`Open work for ${group.name}`}
                  columns={openColumns}
                  rows={group.items}
                  rowKey={(row) => row.summary.id}
                />
              </details>
            );
          })
        ) : (
          <p>No open work.</p>
        )}
      </Section>
      <Waiting
        title="Completed, not invoiced"
        rows={data.uninvoiced}
        truncated={data.uninvoicedTruncated}
        columns={waitingColumns("Quoted value", (item) => item.summary.quoteTotal)}
      />
      <Waiting
        title="Invoices created, not sent"
        rows={data.unsent}
        truncated={data.unsentTruncated}
        columns={waitingColumns("Invoice total", (item) => item.invoiceTotal)}
      />
      <Section
        title="How long each step takes"
        note="Whole days, for steps that ended in this period. Times are when the dashboard saw each change."
      >
        <Records
          label="Cycle times"
          columns={cycleColumns}
          rows={data.cycleTimes}
          rowKey={(row) => row.step}
        />
      </Section>
      <Section
        title="Jobs completed"
        note="Office owner is whoever held the job before it passed on. Counts do not reflect job size."
      >
        <p>
          {plural(data.completedTotal, "job")} completed in this period.
          {data.completedAlreadyWhenFirstSeen
            ? ` ${data.completedAlreadyWhenFirstSeen} of these were already complete when first seen, so their completion date is approximate.`
            : ""}
        </p>
        {data.completedTotal ? (
          <div className="overview-pair">
            <Records
              label="Jobs completed by office owner"
              columns={personColumns("Office owner")}
              rows={data.completedByOwner}
              rowKey={(row) => row.id ?? row.name}
            />
            <Records
              label="Jobs completed by technician"
              columns={personColumns("Technician")}
              rows={data.completedByTechnician}
              rowKey={(row) => row.name}
            />
          </div>
        ) : null}
      </Section>
      <Section
        title="Invoices sent"
        note="Only invoices linked to a tracked quote. Sent means marked as sent, not proof of delivery."
      >
        <p>
          {plural(sent.count, "invoice")} sent in this period
          {sent.count ? `, totalling ${amounts(sent.totals)}` : ""}.
          {sent.paidNotSeenSent
            ? ` A further ${sent.paidNotSeenSent} were paid without being seen as sent.`
            : ""}
        </p>
        {sent.truncated ? (
          <Notice tone="warning">Only the latest 500 invoices are listed.</Notice>
        ) : null}
        {sent.items.length ? (
          <Records
            label="Invoices sent in this period"
            columns={[
              jobColumn,
              customerColumn,
              { header: "Invoice", cell: (item) => item.invoiceNumber ?? "Not recorded" },
              {
                header: "Total",
                cell: (item) => formatMoney(item.invoiceTotal, item.summary.currency),
              },
              {
                header: "First seen as sent",
                cell: (item) => formatFetchTime(item.invoiceSentSeenAt),
              },
            ]}
            rows={sent.items}
            rowKey={(row) => row.summary.id}
          />
        ) : null}
      </Section>
    </>
  );
}

export function DirectorOverview({
  api,
  entity,
  entities,
  profile,
  onAccessDenied,
}: {
  api: DashboardApi;
  entity: Entity | null;
  entities: Entity[];
  profile: Profile;
  onAccessDenied(): void;
}) {
  const ids = useId();
  const [period, setPeriod] = useState<Period>(() => {
    const to = businessToday();
    return { from: daysBefore(to, 29), to };
  });
  const [draft, setDraft] = useState(period);
  const [problem, setProblem] = useState<string | null>(null);
  const authorised = useMemo(() => entities.map((item) => item.id), [entities]);
  const entityId = entity?.id ?? null;
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
  function apply(event: FormEvent) {
    event.preventDefault();
    const found = periodProblem(draft);
    setProblem(found);
    if (!found) setPeriod(draft);
  }
  return (
    <div>
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
      {problem ? (
        <p role="alert" className="notice error">
          {problem}
        </p>
      ) : (
        <p className="supporting">
          Showing {formatDate(period.from)} to {formatDate(period.to)}.
        </p>
      )}
      {resource.status === "loading" ? <Loading label="Loading the overview…" /> : null}
      {resource.status === "error" ? (
        <Failure error={resource.error} onRetry={resource.refresh} label="Retry overview" />
      ) : null}
      {resource.status === "refreshing" ? <p role="status">Refreshing the overview…</p> : null}
      {resource.data ? (
        <div aria-busy={resource.status === "refreshing"}>
          <Report data={resource.data} />
        </div>
      ) : null}
    </div>
  );
}
