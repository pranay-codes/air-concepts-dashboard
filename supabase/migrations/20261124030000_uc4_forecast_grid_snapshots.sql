-- Separate append-only versions allow future months to be refreshed without altering historical forecasts.
set lock_timeout = '5s';
create table public.forecast_grid_revisions (
  entity_id uuid not null references public.entities(id) on delete restrict,
  forecast_month date not null check (forecast_month = date_trunc('month', forecast_month::timestamp)::date),
  revision integer not null check (revision > 0),
  created_at timestamptz not null default now(),
  created_by uuid not null references public.staff_users(id) on delete restrict,
  primary key (entity_id, forecast_month, revision)
);
create table public.forecast_grid_lines (
  id bigint generated always as identity primary key,
  entity_id uuid not null,
  forecast_month date not null,
  revision integer not null,
  document_id uuid not null,
  split_sequence integer,
  direction text not null check (direction in ('IN','OUT')),
  contact_name text not null,
  amount numeric(18,2) not null check (amount >= 0),
  foreign key (entity_id, forecast_month, revision) references public.forecast_grid_revisions(entity_id, forecast_month, revision) on delete restrict
);
create index forecast_grid_lines_revision on public.forecast_grid_lines(entity_id, forecast_month, revision);
create unique index forecast_grid_lines_document_once on public.forecast_grid_lines(entity_id, forecast_month, revision, document_id, coalesce(split_sequence, 0));
alter table public.forecast_grid_revisions enable row level security;
alter table public.forecast_grid_lines enable row level security;
revoke all on public.forecast_grid_revisions, public.forecast_grid_lines from public, anon, authenticated;
grant select, insert on public.forecast_grid_revisions, public.forecast_grid_lines to service_role;
revoke all on sequence public.forecast_grid_lines_id_seq from public, anon, authenticated;
grant usage, select on sequence public.forecast_grid_lines_id_seq to service_role;
