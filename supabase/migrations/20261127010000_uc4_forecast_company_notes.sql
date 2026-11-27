-- Internal company notes follow forecast company names within an entity, never Xero.
set lock_timeout = '5s';
create table public.forecast_company_notes (
  entity_id uuid not null references public.entities(id) on delete restrict,
  company_name text not null check (char_length(company_name) between 1 and 500),
  note text not null check (char_length(note) between 1 and 2000 and note = btrim(note)),
  created_by uuid not null references public.staff_users(id) on delete restrict,
  updated_by uuid not null references public.staff_users(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (entity_id, company_name)
);
alter table public.forecast_company_notes enable row level security;
revoke all on table public.forecast_company_notes from public, anon, authenticated;
grant select, insert, update, delete on table public.forecast_company_notes to service_role;
