import { Fragment, useCallback, useRef, useState } from "react";
import type { DashboardApi } from "../api/dashboard";
import { isAccessFailure } from "../api/client";
import { useResource } from "../api/useResource";
import { useAction } from "../api/useAction";
import { Failure, Loading } from "../ui/Feedback";
import { formatDecimal } from "../ui/format";
import { cents, decimal } from "./contracts";
import { TransactionNoteEditor } from "./TransactionNoteEditor";
import { currentMonth } from "./ForecastHeader";
import { monthName, signClass } from "./ForecastTable";

export function stepMonth(month: string, offset: number) {
  const [year = "0", value = "1"] = month.split("-");
  return new Date(Date.UTC(Number(year), Number(value) - 1 + offset, 1)).toISOString().slice(0, 7);
}
export function ForecastGrid(props: {
  api: DashboardApi;
  entityId: string;
  subject: string;
  revision: number;
  onAccessDenied(): void;
  onChanged(): void;
}) {
  const { api, entityId, onAccessDenied } = props;
  const [start, setStart] = useState(() => stepMonth(currentMonth(), -1));
  const [success, setSuccess] = useState<{
    omittedCurrentItems: number;
    changedProtectedSchedules: number;
  } | null>(null);
  const load = useCallback(
    async (signal: AbortSignal) => {
      try {
        return await api.forecastGrid(entityId, start, signal);
      } catch (error) {
        if (!signal.aborted && isAccessFailure(error)) onAccessDenied();
        throw error;
      }
    },
    [api, entityId, onAccessDenied, start],
  );
  const resource = useResource(
    `${props.subject}:${props.entityId}:grid:${start}:${props.revision}`,
    load,
  );
  const action = useAction(`${props.subject}:${props.entityId}:grid:generate`);
  return (
    <section aria-label="Cash forecast">
      <div className="forecast-grid-controls">
        <div role="group" aria-label="Forecast months">
          <button
            type="button"
            aria-label="Previous month"
            onClick={() => setStart((value) => stepMonth(value, -1))}
          >
            ‹
          </button>{" "}
          <button
            type="button"
            aria-label="Next month"
            onClick={() => setStart((value) => stepMonth(value, 1))}
          >
            ›
          </button>
        </div>
        <button
          type="button"
          disabled={action.busy}
          onClick={() => {
            setSuccess(null);
            void action
              .run(
                (signal) => props.api.generateForecastGrid(props.entityId, signal),
                (error) => {
                  if (isAccessFailure(error)) props.onAccessDenied();
                },
              )
              .then((result) => {
                if (result) {
                  setSuccess(result);
                  resource.refresh();
                }
              });
          }}
        >
          {action.busy ? "Creating forecast…" : "Create forecast"}
        </button>
      </div>
      {success ? (
        <p role="status">
          Forecast created. Future months have been refreshed.{" "}
          {success.omittedCurrentItems
            ? `${success.omittedCurrentItems} new current-month items were not added because that month is not updated.`
            : ""}{" "}
          {success.changedProtectedSchedules
            ? `${success.changedProtectedSchedules} documents have changed schedules and were excluded from the refresh to prevent double-counting; review them in Xero items.`
            : ""}
        </p>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {resource.status === "loading" ? <Loading label="Loading forecast grid…" /> : null}
      {resource.status === "refreshing" ? <p role="status">Refreshing forecast…</p> : null}
      {resource.data ? (
        <div
          className="forecast-grid-scroll"
          role="region"
          aria-label="Cash forecast table"
          tabIndex={0}
        >
          <table className="forecast-grid-table">
            <caption>Cash forecast · NZD</caption>
            <thead>
              <tr>
                <th scope="col" aria-label="Forecast rows" />
                {resource.data.months.map((month) => (
                  <th scope="col" key={month.month}>
                    {monthName(month.month)}
                  </th>
                ))}
              </tr>
              <tr className="forecast-grid-opening-row">
                <th scope="row">Opening balance</th>
                {resource.data.months.map((month) => (
                  <td className="money" key={month.month}>
                    <OpeningCell
                      key={`${props.subject}:${entityId}:${month.month}`}
                      api={api}
                      entityId={entityId}
                      subject={props.subject}
                      month={month}
                      onSaved={props.onChanged}
                      onAccessDenied={onAccessDenied}
                    />
                  </td>
                ))}
              </tr>
            </thead>
            <tbody>
              {(["IN", "OUT"] as const).map((direction) => (
                <FragmentRows
                  key={`${props.subject}:${entityId}:${direction}`}
                  direction={direction}
                  grid={resource.data!}
                  api={api}
                  entityId={entityId}
                  onAccessDenied={onAccessDenied}
                  onSaved={() => {
                    resource.refresh();
                    props.onChanged();
                  }}
                />
              ))}
              <tr className="forecast-grid-summary">
                <th scope="row">Closing balance</th>
                {resource.data.months.map((month) => (
                  <td className={`money ${signClass(month.closing) ?? ""}`} key={month.month}>
                    {!month.available
                      ? "\u2014"
                      : month.closing === null
                        ? "Not recorded"
                        : formatDecimal(month.closing, 2)}
                  </td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
function OpeningCell(props: {
  api: DashboardApi;
  entityId: string;
  subject: string;
  month: import("../api/dashboard").ForecastGrid["months"][number];
  onSaved(): void;
  onAccessDenied(): void;
}) {
  const { month } = props;
  const editButton = useRef<HTMLButtonElement>(null);
  const [editing, setEditing] = useState(false);
  const [amount, setAmount] = useState(month.opening ?? "");
  const [error, setError] = useState("");
  const action = useAction(`${props.subject}:${props.entityId}:${month.month}:grid-opening`);
  if (month.month < currentMonth())
    return (
      <span className={signClass(month.opening)}>
        {month.opening === null ? "Not recorded" : formatDecimal(month.opening, 2)}
      </span>
    );
  if (!editing)
    return (
      <button
        type="button"
        ref={editButton}
        className={`forecast-grid-value ${signClass(month.opening) ?? ""}`.trim()}
        aria-label={`Edit opening balance for ${monthName(month.month)}: ${month.opening === null ? "Not recorded" : formatDecimal(month.opening, 2)}`}
        onClick={() => {
          setAmount(month.opening ?? "");
          setError("");
          setEditing(true);
        }}
      >
        {month.opening === null ? "Not recorded" : formatDecimal(month.opening, 2)}
      </button>
    );
  return (
    <form
      className="forecast-grid-opening"
      onSubmit={(event) => {
        event.preventDefault();
        const value = decimal(amount);
        if (value === null) {
          setError("Enter a cash balance with at most two decimal places.");
          return;
        }
        if (month.openingSource === "CARRIED_FORWARD" && value === decimal(month.opening ?? ""))
          return;
        setError("");
        void action
          .run(
            (signal) =>
              props.api.saveForecastOpening(
                props.entityId,
                month.month,
                value,
                month.openingNote ?? "",
                signal,
              ),
            (failure) => {
              if (isAccessFailure(failure)) props.onAccessDenied();
            },
          )
          .then((result) => {
            if (result) {
              setEditing(false);
              props.onSaved();
              requestAnimationFrame(() => editButton.current?.focus());
            }
          });
      }}
    >
      <input
        autoFocus
        inputMode="decimal"
        aria-label={`Opening balance for ${monthName(month.month)}`}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `opening-error-${month.month}` : undefined}
        value={amount}
        onChange={(event) => setAmount(event.target.value)}
      />
      <button
        className="compact-button"
        type="submit"
        disabled={
          action.busy ||
          (month.openingSource === "CARRIED_FORWARD" &&
            decimal(amount) === decimal(month.opening ?? ""))
        }
      >
        {action.busy ? "Saving..." : "Save"}
      </button>
      <button
        className="compact-button"
        type="button"
        disabled={action.busy}
        onClick={() => {
          setEditing(false);
          requestAnimationFrame(() => editButton.current?.focus());
        }}
      >
        Cancel
      </button>
      {error ? (
        <p id={`opening-error-${month.month}`} role="alert">
          {error}
        </p>
      ) : null}
      {action.error ? <Failure error={action.error} /> : null}
    </form>
  );
}
function AmountCell(props: {
  api: DashboardApi;
  entityId: string;
  month: string;
  direction: "IN" | "OUT";
  scope: "COMPANY" | "DOCUMENT" | "MANUAL";
  itemKey: string;
  label: string;
  amount: string | undefined;
  onSaved(): void;
  onAccessDenied(): void;
}) {
  const [editing, setEditing] = useState(false);
  const [amount, setAmount] = useState(props.amount ?? "");
  const [error, setError] = useState("");
  const button = useRef<HTMLButtonElement>(null);
  const action = useAction(`${props.entityId}:${props.month}:${props.scope}:${props.itemKey}`);
  const label = `${props.label} for ${monthName(props.month)}`;
  if (props.month < currentMonth())
    return <>{props.amount === undefined ? "\u2014" : formatDecimal(props.amount, 2)}</>;
  if (!editing)
    return (
      <button
        ref={button}
        type="button"
        className="forecast-grid-value"
        aria-label={`Edit ${label}: ${props.amount === undefined ? "\u2014" : formatDecimal(props.amount, 2)}`}
        onClick={() => {
          setAmount(props.amount ?? "");
          setError("");
          setEditing(true);
        }}
      >
        {props.amount === undefined ? "\u2014" : formatDecimal(props.amount, 2)}
      </button>
    );
  return (
    <form
      className="forecast-grid-opening"
      onSubmit={(event) => {
        event.preventDefault();
        const value = decimal(amount);
        if (value === null || (cents(value) ?? -1n) < 0n) {
          setError("Enter zero or a positive amount with at most two decimal places.");
          return;
        }
        setError("");
        void action
          .run(
            (signal) =>
              props.api.saveForecastGridAmount(
                props.entityId,
                {
                  month: props.month,
                  direction: props.direction,
                  scope: props.scope,
                  key: props.itemKey,
                  amount: value,
                },
                signal,
              ),
            (failure) => {
              if (isAccessFailure(failure)) props.onAccessDenied();
            },
          )
          .then((result) => {
            if (result) {
              setEditing(false);
              props.onSaved();
              requestAnimationFrame(() => button.current?.focus());
            }
          });
      }}
    >
      <input
        autoFocus
        inputMode="decimal"
        aria-label={label}
        aria-invalid={Boolean(error)}
        value={amount}
        onChange={(event) => setAmount(event.target.value)}
      />
      <button className="compact-button" type="submit" disabled={action.busy}>
        Save
      </button>
      <button
        className="compact-button"
        type="button"
        disabled={action.busy}
        onClick={() => {
          setError("");
          void action
            .run(
              (signal) =>
                props.api.saveForecastGridAmount(
                  props.entityId,
                  {
                    month: props.month,
                    direction: props.direction,
                    scope: props.scope,
                    key: props.itemKey,
                    amount: null,
                    reset: true,
                  },
                  signal,
                ),
              (failure) => {
                if (isAccessFailure(failure)) props.onAccessDenied();
              },
            )
            .then((result) => {
              if (result) {
                setEditing(false);
                props.onSaved();
                requestAnimationFrame(() => button.current?.focus());
              }
            });
        }}
      >
        Use calculated amount
      </button>
      <button
        className="compact-button"
        type="button"
        disabled={action.busy}
        onClick={() => {
          setEditing(false);
          requestAnimationFrame(() => button.current?.focus());
        }}
      >
        Cancel
      </button>
      {error ? <p role="alert">{error}</p> : null}
      {action.error ? <Failure error={action.error} /> : null}
    </form>
  );
}
function FragmentRows({
  direction,
  grid,
  api,
  entityId,
  onAccessDenied,
  onSaved,
}: {
  direction: "IN" | "OUT";
  grid: import("../api/dashboard").ForecastGrid;
  api: DashboardApi;
  entityId: string;
  onAccessDenied(): void;
  onSaved(): void;
}) {
  const [saves, setSaves] = useState<{
    base: typeof grid | null;
    notes: Record<string, string | null>;
  }>({ base: null, notes: {} });
  const savedNotes = saves.base === grid ? saves.notes : {};
  const rows = grid.rows.filter((row) => row.direction === direction);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState(false);
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [startMonth, setStartMonth] = useState(currentMonth());
  const [recurrence, setRecurrence] = useState("ONE_OFF");
  const [before, setBefore] = useState("");
  const [validation, setValidation] = useState("");
  const manualAction = useAction(`${entityId}:${direction}:grid-manual`);
  const groups = new Map<string, typeof rows>();
  for (const row of rows) {
    const key =
      row.source === "DOCUMENT" && row.company !== "Unknown contact"
        ? `company:${row.company}`
        : `${row.source}:${row.documentId}:${row.contact}`;
    groups.set(key, [...(groups.get(key) ?? []), row]);
  }
  const orderedGroups = [...groups.entries()].filter(([, group]) => group[0]?.source !== "MANUAL");
  const companies = [...new Set(orderedGroups.map(([, group]) => group[0]!.company))];
  const pending = [...groups.entries()].filter(([, group]) => group[0]?.source === "MANUAL");
  const manualOptions = pending.map(([, group]) => group[0]!);
  while (pending.length) {
    let inserted = false;
    for (let i = pending.length - 1; i >= 0; i--) {
      const entry = pending[i]!;
      const anchor = entry[1][0]?.positionBefore;
      let position = orderedGroups.findIndex(([, group]) =>
        anchor?.startsWith("manual:")
          ? `manual:${group[0]?.documentId}:${group[0]?.contact}` === anchor &&
            group[0]?.source === "MANUAL"
          : group[0]?.source === "DOCUMENT" && `company:${group[0]?.company}` === anchor,
      );
      const exactTargetPending = pending.some(
        ([, group]) => `manual:${group[0]?.documentId}:${group[0]?.contact}` === anchor,
      );
      if (position < 0 && anchor?.startsWith("manual:") && !exactTargetPending) {
        const id = anchor.slice(7, 43);
        position = orderedGroups.findIndex(
          ([, group]) => group[0]?.source === "MANUAL" && group[0]?.documentId === id,
        );
      }
      if (!anchor || position >= 0) {
        if (position < 0) orderedGroups.push(entry);
        else orderedGroups.splice(position, 0, entry);
        pending.splice(i, 1);
        inserted = true;
      }
    }
    if (!inserted) {
      orderedGroups.push(...pending);
      break;
    }
  }
  return (
    <>
      <tr>
        <th colSpan={9} scope="colgroup">
          {direction === "IN" ? "Money In" : "Money Out"}{" "}
          <button
            type="button"
            aria-label={`Add manual line to ${direction === "IN" ? "Money In" : "Money Out"}`}
            onClick={() => setAdding(true)}
          >
            Add manual line
          </button>
        </th>
      </tr>
      {adding ? (
        <tr>
          <td colSpan={9}>
            <form
              className="forecast-grid-manual"
              onSubmit={(event) => {
                event.preventDefault();
                const value = decimal(amount);
                if (!description.trim() || value === null || (cents(value) ?? 0n) <= 0n) {
                  setValidation("Enter a description and an amount greater than zero.");
                  return;
                }
                setValidation("");
                void manualAction
                  .run(
                    (signal) =>
                      api.createGridManual(
                        entityId,
                        {
                          description: description.trim(),
                          amount: value,
                          direction,
                          category: direction === "IN" ? "OTHER_RECEIPT" : "OTHER_PAYMENT",
                          recurrence,
                          startMonth,
                          endMonth: null,
                          dayOfMonth: 1,
                          active: true,
                        },
                        before || null,
                        signal,
                      ),
                    (error) => {
                      if (isAccessFailure(error)) onAccessDenied();
                    },
                  )
                  .then((result) => {
                    if (result) {
                      setAdding(false);
                      setDescription("");
                      setAmount("");
                      onSaved();
                    }
                  });
              }}
            >
              <label>
                Description{" "}
                <input
                  required
                  maxLength={200}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </label>
              <label>
                Amount (NZD){" "}
                <input
                  required
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                />
              </label>
              <label>
                Start month{" "}
                <input
                  required
                  type="month"
                  min={currentMonth()}
                  value={startMonth}
                  onChange={(e) => setStartMonth(e.target.value)}
                />
              </label>
              <label>
                Frequency{" "}
                <select value={recurrence} onChange={(e) => setRecurrence(e.target.value)}>
                  <option value="ONE_OFF">One-off</option>
                  <option value="MONTHLY">Monthly</option>
                </select>
              </label>
              <label>
                Place before{" "}
                <select value={before} onChange={(e) => setBefore(e.target.value)}>
                  <option value="">End of section</option>
                  {companies.map((company) => (
                    <option key={company} value={`company:${company}`}>
                      {company}
                    </option>
                  ))}
                  {manualOptions.map((item) => (
                    <option
                      key={`${item.documentId}:${item.contact}`}
                      value={`manual:${item.documentId}:${item.contact}`}
                    >
                      {item.contact} (manual)
                    </option>
                  ))}
                </select>
              </label>
              <button type="submit" disabled={manualAction.busy}>
                Save line
              </button>
              <button type="button" onClick={() => setAdding(false)}>
                Cancel
              </button>
              {validation ? <p role="alert">{validation}</p> : null}
              {manualAction.error ? <Failure error={manualAction.error} /> : null}
            </form>
          </td>
        </tr>
      ) : null}
      {rows.length === 0 ? (
        <tr>
          <td colSpan={9}>No {direction === "IN" ? "invoices" : "bills"} in saved months.</td>
        </tr>
      ) : (
        orderedGroups.map(([key, group]) => (
          <Fragment key={key}>
            {group.length > 1 ? (
              <tr>
                <th scope="row">
                  <div className="forecast-grid-item">
                    <button
                      type="button"
                      className="forecast-grid-disclosure"
                      aria-expanded={expanded.has(key)}
                      onClick={() =>
                        setExpanded((old) => {
                          const next = new Set(old);
                          if (next.has(key)) next.delete(key);
                          else next.add(key);
                          return next;
                        })
                      }
                    >
                      {expanded.has(key) ? "−" : "+"} {group[0]?.company} ({group.length})
                    </button>
                    <TransactionNoteEditor
                      label={`${group[0]!.company} company`}
                      note={grid.companyNotes[group[0]!.company] ?? null}
                      onSave={async (text) => {
                        try {
                          await api.saveForecastCompanyNote(entityId, group[0]!.company, text);
                          onSaved();
                        } catch (error) {
                          if (isAccessFailure(error)) onAccessDenied();
                          throw error;
                        }
                      }}
                    />
                  </div>
                </th>
                {grid.months.map((month) => {
                  const amount = group[0]?.companyAmounts[month.month];
                  return (
                    <td className="money" key={month.month}>
                      <AmountCell
                        api={api}
                        entityId={entityId}
                        month={month.month}
                        direction={direction}
                        scope="COMPANY"
                        itemKey={group[0]!.company}
                        label={`${group[0]!.company} company amount`}
                        amount={amount}
                        onSaved={onSaved}
                        onAccessDenied={onAccessDenied}
                      />
                    </td>
                  );
                })}
              </tr>
            ) : null}
            {(group.length === 1 || expanded.has(key) ? group : []).map((row) => (
              <tr key={`${row.documentId}:${row.contact}`}>
                <th scope="row">
                  <div className="forecast-grid-item">
                    <span>{row.contact}</span>
                    <TransactionNoteEditor
                      label={row.source === "COMPANY" ? `${row.company} company` : row.contact}
                      note={
                        row.source === "COMPANY"
                          ? (grid.companyNotes[row.company] ?? null)
                          : `${row.source}:${row.documentId}` in savedNotes
                            ? (savedNotes[`${row.source}:${row.documentId}`] ?? null)
                            : row.note
                      }
                      onSave={async (text) => {
                        try {
                          if (row.source === "COMPANY") {
                            await api.saveForecastCompanyNote(entityId, row.company, text);
                            onSaved();
                            return;
                          }
                          const result = await api.saveForecastTransactionNote(
                            entityId,
                            row.source,
                            row.documentId,
                            text,
                          );
                          setSaves((current) => ({
                            base: grid,
                            notes: {
                              ...(current.base === grid ? current.notes : {}),
                              [`${row.source}:${row.documentId}`]: result.note ?? null,
                            },
                          }));
                        } catch (error) {
                          if (isAccessFailure(error)) onAccessDenied();
                          throw error;
                        }
                      }}
                    />
                  </div>
                </th>
                {grid.months.map((month) => (
                  <td className="money" key={month.month}>
                    <AmountCell
                      api={api}
                      entityId={entityId}
                      month={month.month}
                      direction={direction}
                      scope={row.source}
                      itemKey={row.source === "COMPANY" ? row.company : row.documentId}
                      label={`${row.contact}${row.source === "COMPANY" ? " company" : ""} amount`}
                      amount={
                        row.source === "COMPANY"
                          ? row.companyAmounts[month.month]
                          : row.amounts[month.month]
                      }
                      onSaved={onSaved}
                      onAccessDenied={onAccessDenied}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </Fragment>
        ))
      )}
      {direction === "OUT" ? (
        <tr>
          <th scope="row">Staffing Expenses</th>
          {grid.months.map((month) => (
            <td className="money" key={month.month}>
              {month.staffingExpenses === null
                ? "\u2014"
                : formatDecimal(month.staffingExpenses, 2)}
            </td>
          ))}
        </tr>
      ) : null}
      <tr className="forecast-grid-summary">
        <th scope="row">Total {direction === "IN" ? "Money In" : "Money Out"}</th>
        {grid.months.map((month) => {
          const total = direction === "IN" ? month.moneyIn : month.moneyOut;
          return (
            <td className="money" key={month.month}>
              {total === null ? "\u2014" : formatDecimal(total, 2)}
            </td>
          );
        })}
      </tr>
    </>
  );
}
