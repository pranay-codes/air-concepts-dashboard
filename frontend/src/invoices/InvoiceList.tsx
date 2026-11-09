import type { Invoice, InvoiceQuery, InvoiceType } from "../api/contracts";
import type { ReactNode } from "react";
import { InvoiceStatus } from "../ui/Feedback";
import { Loading } from "../ui/Feedback";
import { formatDate, formatMoney } from "../ui/format";

type Sort = NonNullable<InvoiceQuery["sort"]>;
interface Props {
  renderOwner?(invoice: Invoice): ReactNode;
  pending?: boolean;
  items: Invoice[];
  type: InvoiceType;
  sort: Sort;
  direction: "ASC" | "DESC";
  selected: string | null;
  onSort(sort: Sort): void;
  onOpen(id: string, trigger: HTMLButtonElement): void;
}
function OwnerName({ invoice }: { invoice: Invoice }) {
  return invoice.owner ? (
    <span>
      {invoice.owner.displayName}
      {invoice.owner.active ? "" : " (Inactive)"}
    </span>
  ) : (
    <span aria-label="Unassigned">—</span>
  );
}
export function InvoiceList({
  items,
  type,
  sort,
  direction,
  selected,
  onSort,
  onOpen,
  pending = false,
  renderOwner,
}: Props) {
  const sales = type === "ACCREC";
  const sortable = (label: string, value: Sort) => (
    <th
      scope="col"
      aria-sort={sort === value ? (direction === "ASC" ? "ascending" : "descending") : undefined}
    >
      <button className="sort-button" type="button" onClick={() => onSort(value)}>
        {label}
        {sort === value ? (direction === "ASC" ? " ↑" : " ↓") : ""}
      </button>
    </th>
  );
  const open = (record: Invoice) => (
    <button
      type="button"
      className="text-button"
      aria-pressed={selected === record.id}
      onClick={(event) => onOpen(record.id, event.currentTarget)}
    >
      {record.number || "Not recorded"}
    </button>
  );
  return (
    <>
      <div
        className="invoice-table-wrap"
        role="region"
        aria-label={`${sales ? "Sales invoices" : "Supplier bills"} table`}
        tabIndex={0}
      >
        <table className="financial-table">
          <caption>{sales ? "Sales invoices" : "Supplier bills"}</caption>
          <thead>
            <tr>
              <th scope="col">{sales ? "Invoice" : "Bill"} number</th>
              {sortable("Contact", "CONTACT")}
              <th scope="col">Reference</th>
              {sortable("Invoice date", "DATE")}
              {sortable("Due date", "DUE_DATE")}
              {sortable("Total", "AMOUNT")}
              <th scope="col" className="money">
                Remaining
              </th>
              {sortable("Status", "STATUS")}
              {sales ? <th scope="col">Owner</th> : null}
            </tr>
          </thead>
          <tbody>
            {pending ? (
              <tr>
                <td colSpan={sales ? 9 : 8}>
                  <Loading label="Fetching records from Xero…" />
                </td>
              </tr>
            ) : null}
            {items.map((record) => (
              <tr key={record.id} className={selected === record.id ? "selected-row" : undefined}>
                <th scope="row">{open(record)}</th>
                <td>{record.contact || "Not recorded"}</td>
                <td>{record.reference || "Not recorded"}</td>
                <td>{formatDate(record.date)}</td>
                <td>{formatDate(record.dueDate)}</td>
                <td className="money">{formatMoney(record.total, record.currency)}</td>
                <td className="money">{formatMoney(record.amountDue, record.currency)}</td>
                <td>
                  <InvoiceStatus invoice={record} />
                </td>
                {sales ? (
                  <td>{renderOwner ? renderOwner(record) : <OwnerName invoice={record} />}</td>
                ) : null}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="invoice-cards">
        {pending ? <Loading label="Fetching records from Xero…" /> : null}
        {items.map((record) => (
          <article
            key={record.id}
            className={`card invoice-card${selected === record.id ? " selected-row" : ""}`}
          >
            <h3>{open(record)}</h3>
            <p>{record.contact || "Contact not recorded"}</p>
            <InvoiceStatus invoice={record} />
            <dl>
              <div>
                <dt>Total (including GST)</dt>
                <dd className="money">{formatMoney(record.total, record.currency)}</dd>
              </div>
              <div>
                <dt>Remaining</dt>
                <dd className="money">{formatMoney(record.amountDue, record.currency)}</dd>
              </div>
              <div>
                <dt>Reference</dt>
                <dd>{record.reference || "Not recorded"}</dd>
              </div>
              <div>
                <dt>Invoice date</dt>
                <dd>{formatDate(record.date)}</dd>
              </div>
              <div>
                <dt>Due date</dt>
                <dd>{formatDate(record.dueDate)}</dd>
              </div>
              {sales ? (
                <div>
                  <dt>Owner</dt>
                  <dd>{renderOwner ? renderOwner(record) : <OwnerName invoice={record} />}</dd>
                </div>
              ) : null}
            </dl>
          </article>
        ))}
      </div>
      <p className="supporting">
        Xero is the source of truth. Totals include GST where applicable. Each amount shows its
        recorded currency.{sales ? " — means Unassigned." : ""}
      </p>
    </>
  );
}
