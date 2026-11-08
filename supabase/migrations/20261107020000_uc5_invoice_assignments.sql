-- FR-UC15-07/19/32: sales ownership is dashboard metadata; no ledger records are copied or edited.
set lock_timeout = '5s';
create table public.invoice_assignments (
  entity_id uuid not null references public.entities(id) on delete restrict,
  invoice_id uuid not null,
  staff_id uuid references public.staff_users(id) on delete restrict,
  assigned_by uuid not null references public.staff_users(id) on delete restrict,
  updated_at timestamptz not null default now(),
  primary key (entity_id, invoice_id)
);
create index invoice_assignments_staff on public.invoice_assignments(staff_id);
alter table public.invoice_assignments enable row level security;
revoke all on table public.invoice_assignments from public, anon, authenticated;
grant select, insert, update on table public.invoice_assignments to service_role;
