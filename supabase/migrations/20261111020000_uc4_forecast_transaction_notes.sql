-- UC4 forecast transaction notes: internal notes on Xero-derived, manual and staffing forecast items.
-- Stored only in the application database; never sent to Xero. Keyed by the stable transaction identity
-- (entity, source, source_id), not by forecast month, row position or split sequence, so a note survives
-- forecast regeneration, repeated syncs, date or amount changes and split changes.
set lock_timeout = '5s';

create table public.forecast_transaction_notes (
  id uuid primary key default gen_random_uuid(),
  entity_id uuid not null references public.entities(id) on delete restrict,
  source text not null check (source in ('DOCUMENT','MANUAL','STAFFING')),
  source_id uuid not null,
  note text not null check (char_length(note) between 1 and 2000 and note = btrim(note)),
  created_by uuid not null references public.staff_users(id) on delete restrict,
  updated_by uuid not null references public.staff_users(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (updated_at >= created_at),
  unique (entity_id, source, source_id)
);

alter table public.forecast_transaction_notes enable row level security;
revoke all on table public.forecast_transaction_notes from public, anon, authenticated;
grant select, insert, update, delete on table public.forecast_transaction_notes to service_role;
