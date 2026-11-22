-- External writes are reserved before calling a provider. No request payloads or credentials are stored.
set lock_timeout = '5s';
create table public.external_operations (
  id uuid primary key,
  entity_id uuid not null references public.entities(id) on delete restrict,
  work_item_id uuid not null,
  kind text not null check (kind in ('VWORK_CREATE_JOB','XERO_CREATE_INVOICE','XERO_ATTACH_REPORT')),
  status text not null check (status in ('PENDING','SUCCEEDED','UNKNOWN','FAILED')),
  outcome text check (outcome in ('CREATED','LINKED_EXISTING','NOT_FOUND')),
  attempt smallint not null default 1 check (attempt between 1 and 20),
  lookup_reference text not null check (length(lookup_reference) between 1 and 255),
  request_fingerprint text not null check (request_fingerprint ~ '^[0-9a-f]{64}$'),
  external_id text check (external_id is null or length(external_id) between 1 and 255),
  error_code text check (error_code ~ '^[A-Z0-9_]{1,80}$'),
  upstream_status integer check (upstream_status between 0 and 599),
  requested_by uuid references public.staff_users(id) on delete restrict,
  requested_at timestamptz not null default now(),
  completed_at timestamptz,
  foreign key (entity_id,work_item_id) references public.work_items(entity_id,id) on delete restrict,
  unique (work_item_id,kind,attempt),
  check ((status in ('PENDING','UNKNOWN')) = (completed_at is null)),
  check ((status = 'SUCCEEDED' and outcome is not null and outcome in ('CREATED','LINKED_EXISTING') and external_id is not null)
    or (status = 'FAILED' and (outcome is null or outcome = 'NOT_FOUND') and external_id is null)
    or (status in ('PENDING','UNKNOWN') and outcome is null and external_id is null))
);
create unique index external_operations_one_active on public.external_operations(work_item_id,kind)
  where status in ('PENDING','UNKNOWN');
create unique index external_operations_one_success on public.external_operations(work_item_id,kind)
  where status='SUCCEEDED' and kind in ('VWORK_CREATE_JOB','XERO_CREATE_INVOICE');
create index external_operations_open on public.external_operations(entity_id,status) where status in ('PENDING','UNKNOWN');
alter table public.external_operations enable row level security;
revoke all on public.external_operations from public, anon, authenticated;
grant select, insert, update on public.external_operations to service_role;
