import { useId } from "react";
import type { Invoice, Role, StaffOption } from "../api/contracts";

export function AssignmentPicker({
  invoice,
  role,
  options,
  disabled,
  onAssign,
}: {
  invoice: Invoice;
  role: Role;
  options: StaffOption[];
  disabled: boolean;
  onAssign(invoiceId: string, staffId: string | null): void;
}) {
  const id = useId();
  if (role === "STANDARD_USER" || invoice.type !== "ACCREC") return null;
  const historical =
    invoice.owner && !options.some((option) => option.id === invoice.owner?.id)
      ? invoice.owner
      : undefined;
  return (
    <div className="assignment-picker">
      <label htmlFor={id} className="visually-hidden">
        Owner for {invoice.number || "this invoice"}
      </label>
      <select
        id={id}
        data-assignment-invoice={invoice.id}
        value={invoice.owner?.id ?? ""}
        disabled={disabled}
        onChange={(event) => {
          if (disabled) return;
          const value = event.target.value;
          if (value && !options.some((option) => option.id === value)) return;
          if (value === (invoice.owner?.id ?? "")) return;
          onAssign(invoice.id, value || null);
        }}
      >
        <option value="">— (Unassigned)</option>
        {historical ? (
          <option value={historical.id} disabled>
            {historical.displayName}
            {historical.active ? " (Unavailable)" : " (Inactive)"}
          </option>
        ) : null}
        {options.map((option) => (
          <option key={option.id} value={option.id}>
            {option.displayName}
          </option>
        ))}
      </select>
    </div>
  );
}
