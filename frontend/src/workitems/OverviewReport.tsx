import { Notice } from "../ui/Feedback";
import { formatDate, formatFetchTime, formatMoney } from "../ui/format";
import type { WorkItem } from "./contracts";
import { Bar, Records, Section, type Column } from "./OverviewParts";
import type {
  Overview,
  OverviewCycleTime,
  OverviewItem,
  OverviewPersonCount,
} from "./overviewContracts";
import {
  amounts,
  attentionItems,
  dayText,
  decisions,
  FEW_JOBS,
  owners,
  plural,
  quoted,
  slowestStep,
  stageOf,
} from "./overviewModel";
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
const jumps = [
  ["overview-decisions", "Decisions"],
  ["overview-uninvoiced", "Money waiting"],
  ["overview-attention", "Jobs needing attention"],
  ["overview-team", "Team"],
  ["overview-pipeline", "Pipeline"],
  ["overview-performance", "Performance"],
] as const;

type Open = (item: WorkItem, section: string) => void;

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

function Headlines({ data }: { data: Overview }) {
  const open = data.stages.reduce((total, stage) => total + stage.count, 0);
  const late = data.openItems.filter((item) => item.summary.late).length;
  const completed = stageOf(data, "JOB_COMPLETED");
  const created = stageOf(data, "INVOICE_CREATED");
  const sent = data.invoicesSent;
  const waiting = (count: number, totals: Overview["stages"][number]["totals"]) =>
    count ? quoted(totals) : "Nothing waiting";
  const groups = [
    {
      title: "Open now",
      tiles: [
        {
          label: "Open work",
          value: open,
          detail: `${data.openTruncated ? "At least " : ""}${late} late`,
          target: "overview-pipeline",
        },
        {
          label: "Completed, not invoiced",
          value: completed.count,
          detail: waiting(completed.count, completed.totals),
          target: "overview-uninvoiced",
        },
        {
          label: "Invoices not sent",
          value: created.count,
          detail: waiting(created.count, created.totals),
          target: "overview-unsent",
        },
      ],
    },
    {
      title: "In the selected period",
      tiles: [
        {
          label: "Jobs completed",
          value: data.completedTotal,
          detail: `${formatDate(data.from)} to ${formatDate(data.to)}`,
          target: "overview-completed",
        },
        {
          label: "Invoices sent",
          value: sent.count,
          detail: sent.count ? amounts(sent.totals) : "None sent",
          target: "overview-sent",
        },
      ],
    },
  ];
  return (
    <div className="overview-headlines">
      {groups.map((group) => (
        <Section key={group.title} title={group.title}>
          <ul className="overview-tiles">
            {group.tiles.map((tile) => (
              <li key={tile.label}>
                <a href={`#${tile.target}`}>
                  <span className="overview-tile-label">{tile.label}</span>
                  <strong>{tile.value}</strong>
                  <span className="supporting">{tile.detail}</span>
                </a>
              </li>
            ))}
          </ul>
        </Section>
      ))}
    </div>
  );
}

const urgency = { uninvoiced: "warning", unsent: "warning", flagged: "error", late: "error" };

function Decisions({ data }: { data: Overview }) {
  const rows = decisions(data);
  return (
    <Section id="overview-decisions" title="Needs your decision">
      {data.openTruncated ? (
        <p className="supporting">
          Flagged, late and unowned jobs are counted from the oldest 500 open jobs only.
        </p>
      ) : null}
      {rows.length ? (
        <>
          <ul className="overview-decisions">
            {rows.map((row) => (
              <li
                key={row.key}
                className={`card ${row.key === "unassigned" ? "" : `urgent-${urgency[row.key]}`}`}
              >
                <h3>{row.title}</h3>
                <p className="overview-decision-detail">{row.detail}</p>
                <p className="supporting">{row.nextStep}</p>
                <a href={`#${row.target}`}>{row.linkLabel}</a>
              </li>
            ))}
          </ul>
          <p className="supporting">A job can appear in more than one of these.</p>
        </>
      ) : (
        <Notice tone="success">
          Nothing needs a decision: no uninvoiced, unsent, flagged, late or unowned open jobs
          {data.openTruncated ? " were found in the jobs checked" : ""}.
        </Notice>
      )}
    </Section>
  );
}

function jobColumns(section: string, onOpen: Open): Column<OverviewItem>[] {
  return [
    {
      header: "Job",
      cell: ({ summary }) => (
        <>
          <button
            type="button"
            className="text-button"
            aria-label={`Open ${summary.quoteNumber}`}
            onClick={() => onOpen(summary, section)}
          >
            {summary.quoteNumber}
          </button>
          <span className="supporting">
            {summary.jobId || "Job ID not recorded"} · {summary.entityName}
          </span>
        </>
      ),
    },
    {
      header: "Customer",
      cell: ({ summary }) => summary.customerName || "Customer not recorded",
    },
  ];
}

const stageColumn: Column<OverviewItem> = {
  header: "Stage",
  cell: ({ summary }) => (
    <>
      <span>{stageLabels[summary.stage]}</span>
      <span className="supporting">{plural(summary.daysInStage, "day")} in stage</span>
    </>
  ),
};
const ownerColumn: Column<OverviewItem> = {
  header: "Owner",
  cell: ({ summary }) => summary.owner?.displayName ?? "Unassigned",
};
const flagColumn = (header: string): Column<OverviewItem> => ({
  header,
  wide: true,
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
});

function Waiting({
  id,
  title,
  rows,
  truncated,
  amountHeader,
  amount,
  onOpen,
}: {
  id: string;
  title: string;
  rows: OverviewItem[];
  truncated: boolean;
  amountHeader: string;
  amount(item: OverviewItem): string | undefined;
  onOpen: Open;
}) {
  return (
    <Section id={id} title={title} note="Open now, longest waiting first.">
      {truncated ? <Notice tone="warning">Only the oldest 500 are listed.</Notice> : null}
      {rows.length ? (
        <Records
          label={title}
          collapsible
          columns={[
            ...jobColumns(id, onOpen),
            {
              header: amountHeader,
              numeric: true,
              cell: (item) => formatMoney(amount(item), item.summary.currency),
            },
            {
              header: "Waiting",
              numeric: true,
              cell: ({ summary }) => dayText(summary.daysInStage),
            },
            ownerColumn,
          ]}
          rows={rows}
          rowKey={(row) => row.summary.id}
        />
      ) : (
        <p>Nothing is waiting here.</p>
      )}
    </Section>
  );
}

function Attention({ data, onOpen }: { data: Overview; onOpen: Open }) {
  const rows = attentionItems(data.openItems);
  return (
    <Section
      id="overview-attention"
      title="Jobs needing attention"
      note="Open jobs that are late or flagged, flagged first."
    >
      {data.openTruncated ? (
        <p className="supporting">Checked against the oldest 500 open jobs only.</p>
      ) : null}
      {rows.length ? (
        <Records
          label="Jobs needing attention"
          collapsible
          columns={[
            ...jobColumns("overview-attention", onOpen),
            stageColumn,
            ownerColumn,
            flagColumn("Why"),
          ]}
          rows={rows}
          rowKey={(row) => row.summary.id}
        />
      ) : (
        <p>No open jobs are late or flagged.</p>
      )}
    </Section>
  );
}

function Team({ data, onOpen }: { data: Overview; onOpen: Open }) {
  const groups = owners(data.openItems);
  const most = Math.max(0, ...groups.map((group) => group.items.length));
  return (
    <Section
      id="overview-team"
      title="Who has what"
      note="Open work now, by the dashboard owner. People with late or flagged jobs come first."
    >
      {data.openTruncated ? (
        <Notice tone="warning">
          Only the oldest 500 open items are listed, so counts by person are incomplete. Choose one
          entity to see more.
        </Notice>
      ) : null}
      {groups.length ? (
        groups.map((group) => (
          <details key={group.key} className="overview-person">
            <summary>
              <span className="overview-person-name">
                {group.name}: {group.items.length} open{group.late ? `, ${group.late} late` : ""}
              </span>
              {group.flagged ? (
                <span className="badge warning">{group.flagged} flagged</span>
              ) : null}
              <Bar value={group.items.length} max={most} />
            </summary>
            <Records
              label={`Open work for ${group.name}`}
              collapsible
              columns={[
                ...jobColumns("overview-team", onOpen),
                stageColumn,
                { header: "Technician", cell: (item) => item.technician ?? "Not recorded" },
                flagColumn("Flags"),
              ]}
              rows={group.items}
              rowKey={(row) => row.summary.id}
            />
          </details>
        ))
      ) : (
        <p>No open work.</p>
      )}
    </Section>
  );
}

function Pipeline({ data }: { data: Overview }) {
  const most = Math.max(0, ...data.stages.map((row) => row.count));
  return (
    <Section
      id="overview-pipeline"
      title="Pipeline"
      note="Open work now, valued at the quote total."
    >
      <Records
        label="Open work by stage"
        table
        columns={[
          { header: "Stage", cell: (row) => stageLabels[row.stage] },
          {
            header: "Jobs",
            cell: (row) => (
              <span className="overview-measure">
                <span>{row.count}</span>
                <Bar value={row.count} max={most} />
              </span>
            ),
          },
          {
            header: "Quoted value",
            numeric: true,
            cell: (row) => (row.count ? amounts(row.totals) : "Nothing open"),
          },
        ]}
        rows={data.stages}
        rowKey={(row) => row.stage}
      />
    </Section>
  );
}

function CycleTimes({ rows }: { rows: OverviewCycleTime[] }) {
  const slowest = slowestStep(rows);
  const longest = Math.max(0, ...rows.map((row) => row.medianDays ?? 0));
  return (
    <Section
      level={3}
      title="How long each step takes"
      note="Whole days, for steps that ended in this period. Times are when the dashboard saw each change."
    >
      <Records
        label="Cycle times"
        columns={[
          {
            header: "Step",
            cell: (row) => (
              <>
                <span>{stepLabels[row.step]}</span>
                {row.step === slowest ? <span className="badge warning">Slowest step</span> : null}
                {row.sampleSize > 0 && row.sampleSize < FEW_JOBS ? (
                  <span className="badge neutral">Few jobs measured</span>
                ) : null}
              </>
            ),
          },
          {
            header: "Typical (median)",
            cell: (row) =>
              row.medianDays == null ? (
                "Not enough data"
              ) : (
                <span className="overview-measure">
                  <span>{dayText(row.medianDays)}</span>
                  <Bar value={row.medianDays} max={longest} />
                </span>
              ),
          },
          {
            header: "Average",
            numeric: true,
            cell: (row) => (row.averageDays == null ? "Not available" : dayText(row.averageDays)),
          },
          { header: "Jobs measured", numeric: true, cell: (row) => row.sampleSize },
          { header: "Left out", wide: true, cell: excluded },
        ]}
        rows={rows}
        rowKey={(row) => row.step}
      />
    </Section>
  );
}

function PeopleCounts({
  label,
  header,
  rows,
}: {
  label: string;
  header: string;
  rows: OverviewPersonCount[];
}) {
  const most = Math.max(0, ...rows.map((row) => row.count));
  return (
    <Records
      label={label}
      table
      columns={[
        { header, cell: (person) => person.name },
        {
          header: "Jobs completed",
          cell: (person) => (
            <span className="overview-measure">
              <span>{person.count}</span>
              <Bar value={person.count} max={most} />
            </span>
          ),
        },
      ]}
      rows={rows}
      rowKey={(row) => row.id ?? row.name}
    />
  );
}

function Performance({ data, onOpen }: { data: Overview; onOpen: Open }) {
  const sent = data.invoicesSent;
  return (
    <Section
      id="overview-performance"
      title="Performance in this period"
      note={`${formatDate(data.from)} to ${formatDate(data.to)}.`}
    >
      <CycleTimes rows={data.cycleTimes} />
      <Section level={3} title="Closed in this period">
        {data.closed.length ? (
          <ul className="overview-closed">
            {data.closed.map((row) => (
              <li key={row.reason} className="badge neutral">
                {closeLabels[row.reason] ?? row.reason}: {row.count}
              </li>
            ))}
          </ul>
        ) : (
          <p>Nothing closed in this period.</p>
        )}
      </Section>
      <Section
        id="overview-completed"
        level={3}
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
            <PeopleCounts
              label="Jobs completed by office owner"
              header="Office owner"
              rows={data.completedByOwner}
            />
            <PeopleCounts
              label="Jobs completed by technician"
              header="Technician"
              rows={data.completedByTechnician}
            />
          </div>
        ) : null}
      </Section>
      <Section
        id="overview-sent"
        level={3}
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
            collapsible
            columns={[
              ...jobColumns("overview-sent", onOpen),
              { header: "Invoice", cell: (item) => item.invoiceNumber ?? "Not recorded" },
              {
                header: "Total",
                numeric: true,
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
    </Section>
  );
}

/** Reading order: headline figures, decisions, the records behind them, then performance. */
export function OverviewReport({ data, onOpen }: { data: Overview; onOpen: Open }) {
  return (
    <>
      <nav className="overview-jumps" aria-label="On this page">
        <span className="supporting">On this page</span>
        {jumps.map(([target, label]) => (
          <a key={target} href={`#${target}`}>
            {label}
          </a>
        ))}
      </nav>
      <Headlines data={data} />
      <Decisions data={data} />
      <Waiting
        id="overview-uninvoiced"
        title="Completed, not invoiced"
        rows={data.uninvoiced}
        truncated={data.uninvoicedTruncated}
        amountHeader="Quoted value"
        amount={(item) => item.summary.quoteTotal}
        onOpen={onOpen}
      />
      <Waiting
        id="overview-unsent"
        title="Invoices created, not sent"
        rows={data.unsent}
        truncated={data.unsentTruncated}
        amountHeader="Invoice total"
        amount={(item) => item.invoiceTotal}
        onOpen={onOpen}
      />
      <Attention data={data} onOpen={onOpen} />
      <Team data={data} onOpen={onOpen} />
      <Pipeline data={data} />
      <Performance data={data} onOpen={onOpen} />
    </>
  );
}
