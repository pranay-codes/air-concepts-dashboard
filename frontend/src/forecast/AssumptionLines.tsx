import { useCallback, useState } from "react";
import { ApiError } from "../api/client";
import { useResource } from "../api/useResource";
import { useAction } from "../api/useAction";
import { Failure, Loading, Notice } from "../ui/Feedback";
import { Modal } from "../ui/Modal";
import { formatMoney } from "../ui/format";
import { type ManualItem, type StaffingLine, decimal, cents } from "./contracts";
import { type ForecastContext, handleAccess, useDirty } from "./context";
type Line = ManualItem | StaffingLine;
const label = (line: Line) => ("description" in line ? line.description : line.label);
const cost = (line: Line) => ("amount" in line ? line.amount : line.monthlyCost);
export function AssumptionLines(
  props: ForecastContext & { kind: "manual" | "staffing"; revision: number },
) {
  const [inactive, setInactive] = useState(false);
  const [editing, setEditing] = useState<{ line?: Line } | null>(null);
  const action = useAction(`${props.subject}:${props.entity.id}:${props.kind}:deactivate`);
  const load = useCallback(
    async (signal: AbortSignal): Promise<Line[]> => {
      try {
        return props.kind === "manual"
          ? await props.api.forecastManualItems(props.entity.id, inactive, signal)
          : await props.api.forecastStaffing(props.entity.id, inactive, signal);
      } catch (error) {
        if (!signal.aborted) handleAccess(props.onAccessDenied, error);
        throw error;
      }
    },
    [props.api, props.entity.id, props.kind, inactive, props.onAccessDenied],
  );
  const resource = useResource(
    `${props.subject}:${props.entity.id}:${props.month}:${props.kind}:${inactive}:${props.revision}`,
    load,
  );
  async function deactivate(line: Line) {
    if (
      !window.confirm(
        `Deactivate ${label(line)} (${formatMoney(cost(line), "NZD")}) for ${props.entity.displayName}? It will stop contributing to forecasts.`,
      )
    )
      return;
    const result = await action.run<Line>(
      (signal) =>
        props.kind === "manual"
          ? props.api.deactivateForecastManual(props.entity.id, line.id, signal)
          : props.api.deactivateForecastStaffing(props.entity.id, line.id, signal),
      (error) => handleAccess(props.onAccessDenied, error),
    );
    if (result) props.onChanged();
  }
  return (
    <section>
      <h2>{props.kind === "manual" ? "Manual items" : "Staffing"}</h2>
      <p>
        {props.kind === "manual"
          ? "GST-inclusive cash amounts for items outside Xero."
          : "Gross monthly cash costs. Access is restricted to Director and Financial Admin."}
      </p>
      <div className="forecast-actions">
        <button type="button" onClick={() => setEditing({})}>
          Add {props.kind === "manual" ? "manual item" : "staffing line"}
        </button>
        <label>
          <input
            type="checkbox"
            checked={inactive}
            onChange={(e) => setInactive(e.target.checked)}
          />{" "}
          Show inactive
        </label>
      </div>
      {resource.status === "loading" ? <Loading /> : null}
      {resource.error ? <Failure error={resource.error} onRetry={resource.refresh} /> : null}
      {action.error ? <Failure error={action.error} /> : null}
      {resource.data?.length === 0 ? (
        <Notice>
          No {props.kind === "manual" ? "manual items" : "staffing lines"} recorded. Add a line to
          include it in the forecast.
        </Notice>
      ) : null}
      {resource.data?.map((line) => (
        <article className="forecast-card" key={line.id}>
          <h3>{label(line)}</h3>
          <p>
            {formatMoney(cost(line), "NZD")}{" "}
            {"recurrence" in line
              ? line.recurrence === "MONTHLY"
                ? "monthly"
                : "one-off"
              : "per month"}
          </p>
          <p>
            {line.startMonth} to {line.endMonth ?? "ongoing"}
          </p>
          {!line.active ? <span className="badge warning">Inactive</span> : null}
          <div className="forecast-row-actions">
            <button type="button" onClick={() => setEditing({ line })}>
              Edit {label(line)}
            </button>
            {line.active ? (
              <button type="button" disabled={action.busy} onClick={() => void deactivate(line)}>
                Deactivate {label(line)}
              </button>
            ) : null}
          </div>
        </article>
      ))}
      {editing ? (
        <LineForm {...props} line={editing.line} onClose={() => setEditing(null)} />
      ) : null}
    </section>
  );
}
function LineForm(
  props: ForecastContext & { kind: "manual" | "staffing"; line?: Line; onClose(): void },
) {
  const line = props.line;
  const manual = line && "description" in line ? line : undefined;
  const [form, setForm] = useState({
    label: line ? label(line) : "",
    amount: line ? cost(line) : "",
    start: line?.startMonth ?? props.month,
    end: line?.endMonth ?? "",
    day: String(line ? (("dayOfMonth" in line ? line.dayOfMonth : line.payDayOfMonth) ?? 1) : 1),
    direction: manual?.direction ?? "OUT",
    category: manual?.category ?? "OTHER_PAYMENT",
    recurrence: manual?.recurrence ?? "MONTHLY",
    active: line?.active ?? true,
  });
  const [dirty, setDirty] = useState(false);
  const [validation, setValidation] = useState("");
  useDirty(dirty, props.onDirty);
  const action = useAction(
    `${props.subject}:${props.entity.id}:${props.kind}:${line?.id ?? "new"}`,
  );
  const close = () => {
    if (!dirty || window.confirm("Discard this forecast item edit?")) props.onClose();
  };
  const field = (
    name: "label" | "amount" | "start" | "end" | "day" | "direction" | "category" | "recurrence",
    value: string,
  ) => setForm((current) => ({ ...current, [name]: value }));
  return (
    <Modal
      title={`${line ? "Edit" : "Add"} ${props.kind === "manual" ? "manual item" : "staffing line"}`}
      onClose={close}
    >
      <p>{props.entity.displayName} · NZD</p>
      <form
        className="forecast-form"
        onChange={() => setDirty(true)}
        onSubmit={async (event) => {
          event.preventDefault();
          const amount = decimal(form.amount);
          if (amount === null || (cents(amount) ?? -1n) < (props.kind === "manual" ? 1n : 0n)) {
            setValidation("Enter a valid cash amount with up to two decimal places.");
            return;
          }
          if (form.end && form.end < form.start) {
            setValidation("End month must be on or after start month.");
            return;
          }
          setValidation("");
          const result = await action.run<Line>(
            (signal) =>
              props.kind === "manual"
                ? props.api.saveForecastManual(
                    props.entity.id,
                    {
                      description: form.label,
                      amount,
                      direction: form.direction,
                      category: form.category,
                      recurrence: form.recurrence,
                      startMonth: form.start,
                      endMonth: form.end || null,
                      dayOfMonth: Number(form.day),
                      active: form.active,
                    },
                    line?.id,
                    signal,
                  )
                : props.api.saveForecastStaffing(
                    props.entity.id,
                    {
                      label: form.label,
                      monthlyCost: amount,
                      payDayOfMonth: Number(form.day),
                      startMonth: form.start,
                      endMonth: form.end || null,
                      active: form.active,
                    },
                    line?.id,
                    signal,
                  ),
            (error) => handleAccess(props.onAccessDenied, error),
          );
          if (result) {
            props.onChanged();
            props.onClose();
          }
        }}
      >
        <label>
          {props.kind === "manual" ? "Description" : "Staffing label"}
          <input
            data-autofocus
            required
            maxLength={props.kind === "manual" ? 200 : 160}
            value={form.label}
            onChange={(e) => field("label", e.target.value)}
          />
        </label>
        <label>
          {props.kind === "manual" ? "Cash amount (NZD)" : "Monthly cash cost (NZD)"}
          <input
            inputMode="decimal"
            required
            value={form.amount}
            onChange={(e) => field("amount", e.target.value)}
          />
        </label>
        {props.kind === "manual" ? (
          <>
            <label>
              Direction
              <select value={form.direction} onChange={(e) => field("direction", e.target.value)}>
                <option value="IN">Receipt</option>
                <option value="OUT">Payment</option>
              </select>
            </label>
            <label>
              Category
              <select value={form.category} onChange={(e) => field("category", e.target.value)}>
                {[
                  ["DIRECT_DEBIT", "Direct debit"],
                  ["LOAN", "Loan"],
                  ["RENT", "Rent"],
                  ["TAX", "Tax"],
                  ["TRANSFER", "Transfer"],
                  ["OTHER_PAYMENT", "Other payment"],
                  ["OTHER_RECEIPT", "Other receipt"],
                ].map(([v, l]) => (
                  <option key={v} value={v}>
                    {l}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Recurrence
              <select value={form.recurrence} onChange={(e) => field("recurrence", e.target.value)}>
                <option value="MONTHLY">Monthly</option>
                <option value="ONE_OFF">One-off</option>
              </select>
            </label>
          </>
        ) : null}
        <label>
          Start month
          <input
            type="month"
            required
            value={form.start}
            onChange={(e) => field("start", e.target.value)}
          />
        </label>
        <label>
          End month (optional, inclusive)
          <input
            type="month"
            min={form.start}
            value={form.end}
            onChange={(e) => field("end", e.target.value)}
          />
        </label>
        <label>
          Payment day
          <input
            type="number"
            min={1}
            max={31}
            required
            value={form.day}
            onChange={(e) => field("day", e.target.value)}
          />
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => setForm((current) => ({ ...current, active: e.target.checked }))}
          />{" "}
          Active in forecasts
        </label>
        {validation ? <p role="alert">{validation}</p> : null}
        {action.error ? <Failure error={action.error} /> : null}
        {action.error instanceof ApiError ? (
          <ul>
            {Object.entries(action.error.fieldErrors).map(([field, message]) => (
              <li key={field}>
                {field}: {message}
              </li>
            ))}
          </ul>
        ) : null}
        <div className="forecast-actions">
          <button type="submit" disabled={action.busy}>
            {action.busy ? "Saving…" : "Save item"}
          </button>
          <button type="button" onClick={close}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
