-- Records execution evidence; individual mirror updates remain independently transactional.
set lock_timeout = '5s';
create table public.green_line_sync_runs (
  id uuid primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  system text not null check (system in ('XERO', 'VWORK')),
  trigger text not null check (trigger in ('SCHEDULED', 'MANUAL')),
  triggered_by uuid references public.staff_users(id) on delete restrict,
  started_at timestamptz not null,
  finished_at timestamptz,
  status text not null check (status in ('RUNNING', 'SUCCEEDED', 'FAILED')),
  items_seen integer not null default 0 check (items_seen >= 0),
  items_created integer not null default 0 check (items_created >= 0),
  items_updated integer not null default 0 check (items_updated >= 0),
  transitions integer not null default 0 check (transitions >= 0),
  error_code text check (error_code ~ '^[A-Z0-9_]{1,80}$'),
  upstream_status integer check (upstream_status between 100 and 599),
  modified_since timestamptz,
  check ((trigger = 'MANUAL') = (triggered_by is not null))
);
create index green_line_sync_runs_history on public.green_line_sync_runs (entity_id, system, started_at desc);
alter table public.green_line_sync_runs enable row level security;
revoke all on table public.green_line_sync_runs from public, anon, authenticated;
grant select, insert, update on table public.green_line_sync_runs to service_role;
