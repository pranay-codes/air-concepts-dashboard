-- FR-UC15-01/02/03/04/10: encrypted shared OAuth grants, single-use state and unique tenant mappings.
set lock_timeout = '5s';
create table public.xero_grants (
  id uuid primary key,
  access_ciphertext text not null check (length(access_ciphertext) between 30 and 65536),
  refresh_ciphertext text not null check (length(refresh_ciphertext) between 30 and 65536),
  expires_at timestamptz not null,
  status text not null check (status in ('ACTIVE','REFRESH_PENDING','NEEDS_RECONNECTION')),
  refresh_pending_since timestamptz,
  retry_at timestamptz,
  error_code text check (error_code ~ '^[A-Z0-9_]{1,80}$'),
  updated_at timestamptz not null default now(),
  check ((status = 'REFRESH_PENDING') = (refresh_pending_since is not null))
);
create table public.xero_oauth_attempts (
  id uuid primary key,
  director_id uuid not null references public.staff_users(id) on delete restrict,
  entity_id uuid not null references public.entities(id) on delete restrict,
  state_hash text not null unique check (state_hash ~ '^[a-f0-9]{64}$'),
  expires_at timestamptz not null,
  status text not null check (status in ('AWAITING_CALLBACK','PROCESSING','READY','FAILED','COMPLETED')),
  grant_id uuid references public.xero_grants(id) on delete restrict,
  error_code text check (error_code ~ '^[A-Z0-9_]{1,80}$'),
  upstream_status integer check (upstream_status between 0 and 599),
  created_at timestamptz not null default now(),
  check (status not in ('READY','COMPLETED') or grant_id is not null)
);
create index xero_oauth_attempts_owner on public.xero_oauth_attempts(director_id, created_at);
create table public.xero_entity_connections (
  entity_id uuid primary key references public.entities(id) on delete restrict,
  grant_id uuid not null references public.xero_grants(id) on delete restrict,
  tenant_id uuid not null unique,
  connection_id uuid not null unique,
  tenant_name text not null check (length(tenant_name) between 1 and 500),
  verified_at timestamptz not null
);
create index xero_entity_connections_grant on public.xero_entity_connections(grant_id);
alter table public.xero_grants enable row level security;
alter table public.xero_oauth_attempts enable row level security;
alter table public.xero_entity_connections enable row level security;
revoke all on table public.xero_grants, public.xero_oauth_attempts, public.xero_entity_connections from public, anon, authenticated;
grant select, insert, update on table public.xero_grants, public.xero_oauth_attempts, public.xero_entity_connections to service_role;
