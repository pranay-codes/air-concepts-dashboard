-- GL-01..05, GL-07, GL-08, GL-11: green-line work items, history and per-entity settings.
-- Forward-only. No DROP, TRUNCATE or DELETE.
set lock_timeout = '5s';

create table public.green_line_entity_settings (
  entity_id uuid primary key references public.entities(id) on delete restrict,
  enabled boolean not null default false,
  default_owner_id uuid references public.staff_users(id) on delete restrict,
  service_manager_id uuid references public.staff_users(id) on delete restrict,
  late_threshold_working_days smallint not null default 2 check (late_threshold_working_days between 1 and 30),
  backfill_days smallint not null default 90 check (backfill_days between 0 and 365),
  first_synced_at timestamptz,
  updated_by uuid references public.staff_users(id) on delete restrict,
  updated_at timestamptz not null default now()
);

create table public.work_items (
  id uuid primary key default gen_random_uuid(),
  entity_id uuid not null references public.entities(id) on delete restrict,
  -- Xero quote mirror (read-only cache)
  xero_quote_id uuid not null,
  quote_number text not null check (length(quote_number) between 1 and 255),
  job_id text check (job_id is null or length(job_id) between 1 and 255),
  xero_quote_status text not null check (xero_quote_status in ('DRAFT','SENT','DECLINED','ACCEPTED','INVOICED','DELETED')),
  xero_contact_id uuid,
  customer_name text check (customer_name is null or length(customer_name) <= 500),
  quote_total numeric(18,4),
  quote_currency char(3),
  quote_date date,
  quote_expiry_date date,
  xero_quote_updated_at timestamptz,
  -- Dashboard-owned workflow state
  stage text not null check (stage in ('QUOTED','TO_SCHEDULE','SCHEDULED','TO_INVOICE','INVOICE_DRAFTED','INVOICED','CLOSED')),
  close_reason text check (close_reason in ('LOST','PAID','NO_INVOICE_NEEDED')),
  closing_note text check (closing_note is null or length(closing_note) between 1 and 500),
  owner_id uuid references public.staff_users(id) on delete restrict,
  stage_entered_at timestamptz not null default now(),
  late_after date,
  follow_up_date date,
  notes text check (notes is null or length(notes) <= 4000),
  attention_codes text[] not null default '{}',
  -- vWork mirror
  vwork_job_id text check (vwork_job_id is null or length(vwork_job_id) between 1 and 120),
  vwork_job_number text,
  vwork_status text,
  vwork_search_state text not null default 'NOT_CHECKED'
    check (vwork_search_state in ('NOT_CHECKED','FOUND','NOT_FOUND','UNAVAILABLE','MULTIPLE')),
  technician_name text,
  scheduled_start timestamptz,
  completed_at timestamptz,
  technician_notes text check (technician_notes is null or length(technician_notes) <= 8000),
  report_status text not null default 'NOT_APPLICABLE'
    check (report_status in ('NOT_APPLICABLE','PENDING','ATTACHED','NOT_ATTACHED','CONFIRMED_MANUAL')),
  -- Xero invoice mirror
  xero_invoice_id uuid,
  invoice_number text,
  invoice_status text check (invoice_status is null or invoice_status in ('DRAFT','SUBMITTED','AUTHORISED','PAID','VOIDED','DELETED')),
  invoice_sent boolean,
  invoice_total numeric(18,4),
  invoice_amount_due numeric(18,4),
  invoice_due_date date,
  -- Housekeeping
  origin text not null default 'SYNC' check (origin in ('SYNC','BACKFILL')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version integer not null default 0 check (version >= 0),
  constraint work_items_entity_quote unique (entity_id, xero_quote_id),
  constraint work_items_entity_id_unique unique (entity_id, id),
  constraint work_items_closed_reason check ((stage = 'CLOSED') = (close_reason is not null)),
  constraint work_items_manual_close_note check (close_reason is null or close_reason = 'PAID' or closing_note is not null),
  constraint work_items_attention_codes check (attention_codes <@ array['NO_SERVICE_MANAGER','NO_DEFAULT_OWNER','OWNER_INACTIVE',
    'VWORK_SEARCH_UNAVAILABLE','VWORK_MULTIPLE_MATCHES','VWORK_JOB_CANCELLED','OPERATION_UNKNOWN','OPERATION_FAILED',
    'XERO_STATUS_REGRESSED','QUOTE_INVOICED_IN_XERO','INVOICE_MULTIPLE_MATCHES','INVOICE_VOIDED']::text[])
);
-- One vWork account serves every entity (D3), so a vWork job ID is unique across all entities, not per entity.
create unique index work_items_vwork_job on public.work_items(vwork_job_id) where vwork_job_id is not null;
create unique index work_items_entity_invoice on public.work_items(entity_id, xero_invoice_id) where xero_invoice_id is not null;
create index work_items_owner_open on public.work_items(owner_id, stage_entered_at) where stage <> 'CLOSED';
create index work_items_entity_stage on public.work_items(entity_id, stage, stage_entered_at);
create index work_items_entity_job_id on public.work_items(entity_id, lower(job_id)) where job_id is not null;
create index work_items_attention on public.work_items(entity_id) where cardinality(attention_codes) > 0;

create table public.work_item_events (
  id bigserial primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  work_item_id uuid,   -- null for entity-level events (settings, vWork connection)
  at timestamptz not null default now(),
  actor_id uuid references public.staff_users(id) on delete restrict,      -- null = system (sync)
  source text not null check (source in ('USER','XERO_SYNC','VWORK_SYNC','SYSTEM')),
  event_type text not null check (event_type ~ '^[A-Z][A-Z0-9_]{1,63}$'),
  from_stage text, to_stage text,
  from_owner_id uuid, to_owner_id uuid,
  before jsonb not null default '{}'::jsonb check (jsonb_typeof(before) = 'object'),
  after jsonb not null default '{}'::jsonb check (jsonb_typeof(after) = 'object'),
  check ((source = 'USER') = (actor_id is not null)),
  foreign key (entity_id, work_item_id) references public.work_items(entity_id, id) on delete restrict
);
create index work_item_events_item on public.work_item_events(work_item_id, at desc, id desc);
create index work_item_events_entity on public.work_item_events(entity_id, at desc, id desc);

alter table public.green_line_entity_settings enable row level security;
alter table public.work_items enable row level security;
alter table public.work_item_events enable row level security;
revoke all on table public.green_line_entity_settings, public.work_items, public.work_item_events from public, anon, authenticated;
grant select, insert, update on table public.green_line_entity_settings, public.work_items to service_role;
grant select, insert on table public.work_item_events to service_role;
revoke all on sequence public.work_item_events_id_seq from public, anon, authenticated;
grant usage, select on sequence public.work_item_events_id_seq to service_role;
