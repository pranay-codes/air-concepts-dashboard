import type { InvoiceQuery } from "../api/contracts";
import type { DatePreset } from "../ui/format";

interface Props {
  query: InvoiceQuery;
  search: string;
  preset: DatePreset;
  invalidRange: boolean;
  onSearch(value: string): void;
  onChange(patch: Partial<InvoiceQuery>): void;
  onPreset(value: DatePreset): void;
  onClear(): void;
}
export function InvoiceFilters({
  query,
  search,
  preset,
  invalidRange,
  onSearch,
  onChange,
  onPreset,
  onClear,
}: Props) {
  return (
    <fieldset className="invoice-filters">
      <legend>Search and filters</legend>
      <div className="filter-grid">
        <div className="search-field">
          <label htmlFor="invoice-search">Search invoices</label>
          <input
            id="invoice-search"
            type="search"
            maxLength={200}
            value={search}
            placeholder="Number, reference or contact"
            onChange={(event) => onSearch(event.target.value)}
            autoComplete="off"
          />
        </div>
        <div>
          <label htmlFor="invoice-status">Status</label>
          <select
            id="invoice-status"
            value={query.status ?? "ALL"}
            onChange={(event) =>
              onChange({
                status:
                  event.target.value === "ALL"
                    ? undefined
                    : (event.target.value as InvoiceQuery["status"]),
              })
            }
          >
            <option value="ALL">All statuses</option>
            {["DRAFT", "SUBMITTED", "AUTHORISED", "PAID", "VOIDED", "DELETED"].map((status) => (
              <option key={status} value={status}>
                {status[0]}
                {status.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="invoice-date-field">Date field</label>
          <select
            id="invoice-date-field"
            value={query.dateField ?? "INVOICE_DATE"}
            onChange={(event) =>
              onChange({ dateField: event.target.value as InvoiceQuery["dateField"] })
            }
          >
            <option value="INVOICE_DATE">Invoice date</option>
            <option value="DUE_DATE">Due date</option>
          </select>
        </div>
        <div>
          <label htmlFor="invoice-date-range">Date range</label>
          <select
            id="invoice-date-range"
            value={preset}
            onChange={(event) => onPreset(event.target.value as DatePreset)}
          >
            <option value="ALL_DATES">All dates</option>
            <option value="THIS_MONTH">This month</option>
            <option value="LAST_THREE_MONTHS">Last 3 months</option>
            <option value="THIS_YEAR">This year</option>
            <option value="CUSTOM">Custom dates</option>
          </select>
        </div>
        {preset === "CUSTOM" ? (
          <>
            <div>
              <label htmlFor="invoice-from">From date</label>
              <input
                id="invoice-from"
                type="date"
                min="0001-01-01"
                max="9998-12-31"
                value={query.from ?? ""}
                aria-invalid={invalidRange}
                aria-describedby={
                  invalidRange ? "invoice-date-error invoice-date-help" : "invoice-date-help"
                }
                onChange={(event) => onChange({ from: event.target.value || undefined })}
              />
            </div>
            <div>
              <label htmlFor="invoice-to">To date</label>
              <input
                id="invoice-to"
                type="date"
                min="0001-01-01"
                max="9998-12-31"
                value={query.to ?? ""}
                aria-invalid={invalidRange}
                aria-describedby={
                  invalidRange ? "invoice-date-error invoice-date-help" : "invoice-date-help"
                }
                onChange={(event) => onChange({ to: event.target.value || undefined })}
              />
            </div>
          </>
        ) : null}
        <div>
          <label htmlFor="invoice-sort">Sort by</label>
          <select
            id="invoice-sort"
            value={query.sort ?? "DATE"}
            onChange={(event) => onChange({ sort: event.target.value as InvoiceQuery["sort"] })}
          >
            <option value="DATE">Invoice date</option>
            <option value="DUE_DATE">Due date</option>
            <option value="AMOUNT">Total</option>
            <option value="STATUS">Status</option>
            <option value="CONTACT">Contact</option>
          </select>
        </div>
        <div>
          <label htmlFor="invoice-direction">Sort direction</label>
          <select
            id="invoice-direction"
            value={query.direction ?? "DESC"}
            onChange={(event) =>
              onChange({ direction: event.target.value as InvoiceQuery["direction"] })
            }
          >
            <option value="DESC">Descending</option>
            <option value="ASC">Ascending</option>
          </select>
        </div>
      </div>
      <div className="filter-actions">
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={query.overdue ?? false}
            onChange={(event) => onChange({ overdue: event.target.checked })}
          />
          Overdue only
        </label>
        <button type="button" onClick={onClear}>
          Clear filters
        </button>
      </div>
      <p id="invoice-date-help" className="supporting">
        Date ranges include both ends. Last 3 months includes this month and the previous two, using
        the Auckland calendar.
      </p>
      {invalidRange ? (
        <p id="invoice-date-error" role="alert" className="notice error">
          Start date must be on or before end date.
        </p>
      ) : null}
    </fieldset>
  );
}
