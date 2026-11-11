-- FR-UC04-22/23/24: generated monthly forecast snapshots. Append-only: lines are inserted once and never updated or deleted.
set lock_timeout = '5s';

create table public.forecast_generated_months (
  entity_id uuid not null references public.entities(id) on delete restrict,
  forecast_month date not null check (forecast_month = (date_trunc('month', forecast_month::timestamp))::date),
  first_generated_at timestamptz not null default now(),
  last_generated_at timestamptz not null default now(),
  generated_by uuid not null references public.staff_users(id) on delete restrict,
  generation_count integer not null default 1 check (generation_count >= 1),
  primary key (entity_id, forecast_month),
  check (last_generated_at >= first_generated_at)
);

create table public.forecast_snapshot_lines (
  id bigserial primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  forecast_month date not null,
  generation_number integer not null check (generation_number >= 1),
  source text not null check (source in ('DOCUMENT','MANUAL','STAFFING')),
  source_id uuid not null,
  split_sequence integer check (split_sequence >= 1),
  split_count integer check (split_count >= 1),
  direction text not null check (direction in ('IN','OUT')),
  category text not null,
  document_number text,
  label text not null,
  issue_date date,
  due_date date,
  cash_date date not null,
  rule text not null,
  overdue boolean not null default false,
  amount numeric(18,2) not null check (amount >= 0),
  added_by uuid not null references public.staff_users(id) on delete restrict,
  added_at timestamptz not null default now(),
  check (source = 'DOCUMENT' or (split_sequence is null and split_count is null)),
  check (split_sequence is null or split_count is null or split_sequence <= split_count),
  foreign key (entity_id, forecast_month) references public.forecast_generated_months(entity_id, forecast_month) on delete restrict
);

-- A document is placed in at most one forecast month for its entity. The first split line (or the single line) is the placement.
create unique index forecast_snapshot_lines_document_once
  on public.forecast_snapshot_lines(entity_id, source_id)
  where source = 'DOCUMENT' and coalesce(split_sequence, 1) = 1;
-- Manual items and staffing lines are placed once per month.
create unique index forecast_snapshot_lines_non_document_once
  on public.forecast_snapshot_lines(entity_id, forecast_month, source, source_id)
  where source <> 'DOCUMENT';
create index forecast_snapshot_lines_month on public.forecast_snapshot_lines(entity_id, forecast_month);

alter table public.forecast_generated_months enable row level security;
alter table public.forecast_snapshot_lines enable row level security;
revoke all on table public.forecast_generated_months, public.forecast_snapshot_lines from public, anon, authenticated;
grant select, insert, update on table public.forecast_generated_months to service_role;
grant select, insert on table public.forecast_snapshot_lines to service_role;
revoke all on sequence public.forecast_snapshot_lines_id_seq from public, anon, authenticated;
grant usage, select on sequence public.forecast_snapshot_lines_id_seq to service_role;

-- Extend the audit record_table list (forward-only: the earlier constraint is replaced, history is untouched).
alter table public.forecast_audit drop constraint forecast_audit_record_table_check;
alter table public.forecast_audit add constraint forecast_audit_record_table_check check (record_table in (
  'forecast_documents','forecast_sync_runs','forecast_document_adjustments','forecast_split_lines','forecast_periods',
  'forecast_manual_items','forecast_staffing','forecast_exports','forecast_generated_months'));
