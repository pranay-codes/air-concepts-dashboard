import type { Entity, Invoice, Profile } from "../api/contracts";

export const ENTITY_A = "00000000-0000-4000-8000-000000000001";
export const ENTITY_B = "00000000-0000-4000-8000-000000000002";
export const INVOICE_A = "00000000-0000-4000-8000-000000000010";
export const STAFF_A = "00000000-0000-4000-8000-000000000020";
export const entities: Entity[] = [
  { id: ENTITY_A, displayName: "First entity" },
  { id: ENTITY_B, displayName: "Additional entity with a long organisation name" },
];
export const profile: Profile = { subject: STAFF_A, displayName: "Alex Example", role: "DIRECTOR" };
export function invoice(overrides: Partial<Invoice> = {}): Invoice {
  return {
    id: INVOICE_A,
    type: "ACCREC",
    number: "INV-001",
    reference: "Job 104",
    contact: "Acme Example",
    date: "2026-11-01",
    dueDate: "2026-11-05",
    total: "115.00",
    amountDue: "105.00",
    currency: "NZD",
    status: "AUTHORISED",
    overdue: true,
    ...overrides,
  };
}
