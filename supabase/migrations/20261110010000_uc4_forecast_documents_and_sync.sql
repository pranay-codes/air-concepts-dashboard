-- FR-UC04-01/02/08/17: backend-only forecast mirror, sync diagnostics and history.
set lock_timeout = '5s';

create table public.forecast_documents (
  entity_id uuid not null references public.entities(id) on delete restrict,
  xero_document_id uuid not null,
  tenant_id uuid not null,
  document_type text not null check (document_type in ('ACCREC','ACCPAY')),
  number text,
  reference text,
  contact_name text,
  status text not null check (status in ('DRAFT','SUBMITTED','AUTHORISED','PAID','VOIDED','DELETED')),
  issue_date date not null,
  due_date date,
  xero_expected_date date,
  currency text not null,
  total numeric(18,2),
  total_tax numeric(18,2),
  amount_due numeric(18,2) not null check (amount_due >= 0),
  amount_paid numeric(18,2),
  amount_credited numeric(18,2),
  outstanding boolean not null,
  xero_updated_at timestamptz not null,
  first_seen_at timestamptz not null default now(),
  last_synced_at timestamptz not null default now(),
  settled_at timestamptz,
  primary key (entity_id,xero_document_id),
  check (outstanding = (status='AUTHORISED' and amount_due>0))
);
create index forecast_documents_outstanding on public.forecast_documents(entity_id,outstanding,document_type);

create table public.forecast_sync_runs (
  id uuid primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  trigger text not null check (trigger in ('SCHEDULED','MANUAL')),
  triggered_by uuid references public.staff_users(id) on delete restrict,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  status text not null check (status in ('RUNNING','SUCCEEDED','FAILED')),
  documents_seen integer not null default 0 check (documents_seen>=0),
  documents_inserted integer not null default 0 check (documents_inserted>=0),
  documents_updated integer not null default 0 check (documents_updated>=0),
  documents_settled integer not null default 0 check (documents_settled>=0),
  conflicts_flagged integer not null default 0 check (conflicts_flagged>=0),
  error_code text check (error_code ~ '^[A-Z0-9_]{1,80}$'),
  upstream_status integer check (upstream_status between 0 and 599),
  day_limit_remaining integer check (day_limit_remaining>=0),
  minute_limit_remaining integer check (minute_limit_remaining>=0),
  app_minute_limit_remaining integer check (app_minute_limit_remaining>=0),
  check ((trigger='MANUAL' and triggered_by is not null) or (trigger='SCHEDULED' and triggered_by is null))
);
create index forecast_sync_runs_history on public.forecast_sync_runs(entity_id,started_at desc);

create table public.forecast_audit (
  id bigserial primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  actor_id uuid references public.staff_users(id) on delete restrict,
  at timestamptz not null default now(),
  record_table text not null check (record_table in ('forecast_documents','forecast_sync_runs',
    'forecast_document_adjustments','forecast_split_lines','forecast_periods','forecast_manual_items','forecast_staffing','forecast_exports')),
  record_key text not null,
  action text not null check (action in ('INSERT','UPDATE','SOFT_DELETE','SYNC')),
  before jsonb not null default '{}'::jsonb check (jsonb_typeof(before)='object'),
  after jsonb not null default '{}'::jsonb check (jsonb_typeof(after)='object')
);
create index forecast_audit_history on public.forecast_audit(entity_id,at desc,id desc);

alter table public.forecast_documents enable row level security;
alter table public.forecast_sync_runs enable row level security;
alter table public.forecast_audit enable row level security;
revoke all on table public.forecast_documents,public.forecast_sync_runs,public.forecast_audit from public,anon,authenticated;
grant select,insert,update on table public.forecast_documents,public.forecast_sync_runs to service_role;
grant select,insert on table public.forecast_audit to service_role;
revoke all on sequence public.forecast_audit_id_seq from public,anon,authenticated;
grant usage,select on sequence public.forecast_audit_id_seq to service_role;
