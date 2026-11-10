-- FR-UC04-03/04/08/15/16/19: dashboard-owned assumptions, independent of the Xero mirror.
set lock_timeout = '5s';
create table public.forecast_document_adjustments (
  entity_id uuid not null,
  xero_document_id uuid not null,
  expected_date_override date,
  excluded boolean not null default false,
  exclusion_reason text,
  date_conflict_since timestamptz,
  conflict_previous_xero_date date,
  conflict_new_xero_date date,
  updated_by uuid references public.staff_users(id) on delete restrict,
  updated_at timestamptz not null default now(),
  primary key(entity_id,xero_document_id),
  foreign key(entity_id,xero_document_id) references public.forecast_documents(entity_id,xero_document_id) on delete restrict,
  check (not excluded or (exclusion_reason is not null and length(trim(exclusion_reason)) between 1 and 500))
);
create table public.forecast_split_lines (
  id uuid primary key,
  entity_id uuid not null,
  xero_document_id uuid not null,
  sequence smallint not null check (sequence>0),
  expected_date date not null,
  amount numeric(18,2) not null check (amount>0),
  unique(entity_id,xero_document_id,sequence),
  foreign key(entity_id,xero_document_id) references public.forecast_documents(entity_id,xero_document_id) on delete restrict
);
create table public.forecast_periods (
  entity_id uuid not null references public.entities(id) on delete restrict,
  start_month date not null check (extract(day from start_month)=1),
  opening_balance numeric(18,2) not null,
  opening_balance_source text,
  updated_by uuid not null references public.staff_users(id) on delete restrict,
  updated_at timestamptz not null default now(),
  primary key(entity_id,start_month)
);
create table public.forecast_manual_items (
  id uuid primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  direction text not null check (direction in ('IN','OUT')),
  category text not null check (category in ('DIRECT_DEBIT','LOAN','RENT','TAX','TRANSFER','OTHER_PAYMENT','OTHER_RECEIPT')),
  description text not null check (length(trim(description)) between 1 and 200),
  amount numeric(18,2) not null check (amount>0),
  recurrence text not null check (recurrence in ('ONE_OFF','MONTHLY')),
  day_of_month smallint check (day_of_month between 1 and 31),
  start_month date not null check (extract(day from start_month)=1),
  end_month date check (extract(day from end_month)=1 and end_month>=start_month),
  active boolean not null default true,
  created_by uuid not null references public.staff_users(id) on delete restrict,
  updated_by uuid not null references public.staff_users(id) on delete restrict,
  updated_at timestamptz not null default now(),
  unique(entity_id,id)
);
create table public.forecast_staffing (
  id uuid primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  label text not null check (length(trim(label)) between 1 and 160),
  monthly_cost numeric(18,2) not null check (monthly_cost>=0),
  pay_day_of_month smallint check (pay_day_of_month between 1 and 31),
  start_month date not null check (extract(day from start_month)=1),
  end_month date check (extract(day from end_month)=1 and end_month>=start_month),
  active boolean not null default true,
  created_by uuid not null references public.staff_users(id) on delete restrict,
  updated_by uuid not null references public.staff_users(id) on delete restrict,
  updated_at timestamptz not null default now(),
  unique(entity_id,id)
);
create table public.forecast_exports (
  id uuid primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  start_month date not null check (extract(day from start_month)=1),
  generated_by uuid not null references public.staff_users(id) on delete restrict,
  generated_at timestamptz not null default now(),
  template_version text not null,
  receipts_total numeric(18,2) not null,
  payments_total numeric(18,2) not null,
  closing_total numeric(18,2),
  reconciled boolean not null,
  warnings jsonb not null check (jsonb_typeof(warnings)='array')
);
create index forecast_manual_items_entity on public.forecast_manual_items(entity_id,active,start_month);
create index forecast_staffing_entity on public.forecast_staffing(entity_id,active,start_month);
create index forecast_exports_entity on public.forecast_exports(entity_id,generated_at desc);

alter table public.forecast_document_adjustments enable row level security;
alter table public.forecast_split_lines enable row level security;
alter table public.forecast_periods enable row level security;
alter table public.forecast_manual_items enable row level security;
alter table public.forecast_staffing enable row level security;
alter table public.forecast_exports enable row level security;
revoke all on table public.forecast_document_adjustments,public.forecast_split_lines,public.forecast_periods,
  public.forecast_manual_items,public.forecast_staffing,public.forecast_exports from public,anon,authenticated;
grant select,insert,update on table public.forecast_document_adjustments,public.forecast_split_lines,
  public.forecast_periods,public.forecast_manual_items,public.forecast_staffing to service_role;
grant select,insert on table public.forecast_exports to service_role;
-- Human-approved scoped replacement only; services constrain both entity and document IDs.
grant delete on table public.forecast_split_lines to service_role;
