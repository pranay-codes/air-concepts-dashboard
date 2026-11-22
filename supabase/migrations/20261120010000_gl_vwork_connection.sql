-- One shared vWork account; no plaintext credentials or provider payloads are stored.
set lock_timeout = '5s';
create table public.vwork_connection (
  id smallint primary key default 1 check (id = 1),
  key_ciphertext text,
  status text not null check (status in ('UNVERIFIED','ACTIVE','INVALID','DISCONNECTED')),
  error_code text check (error_code ~ '^[A-Z0-9_]{1,80}$'),
  configured_by uuid not null references public.staff_users(id) on delete restrict,
  configured_at timestamptz not null default now(),
  verified_at timestamptz,
  retry_at timestamptz,
  updated_at timestamptz not null default now(),
  check ((status = 'DISCONNECTED') = (key_ciphertext is null))
);
alter table public.vwork_connection enable row level security;
revoke all on public.vwork_connection from public, anon, authenticated;
grant select, insert, update on public.vwork_connection to service_role;

create table public.vwork_entity_templates (
  template_ref text primary key check (length(template_ref) between 1 and 120),
  entity_id uuid not null references public.entities(id) on delete restrict,
  display_name text check (display_name is null or length(display_name) <= 160),
  is_default boolean not null default false,
  created_by uuid not null references public.staff_users(id) on delete restrict,
  created_at timestamptz not null default now()
);
create unique index vwork_entity_templates_one_default on public.vwork_entity_templates(entity_id) where is_default;
create index vwork_entity_templates_entity on public.vwork_entity_templates(entity_id);
alter table public.vwork_entity_templates enable row level security;
revoke all on public.vwork_entity_templates from public, anon, authenticated;
grant select, insert, update, delete on public.vwork_entity_templates to service_role;

-- Organisation-wide connection changes cannot use entity-scoped work_item_events.
create table public.vwork_connection_events (
  id bigserial primary key,
  actor_id uuid not null references public.staff_users(id) on delete restrict,
  event_type text not null default 'VWORK_CONNECTION_CHANGED' check (event_type = 'VWORK_CONNECTION_CHANGED'),
  status text not null check (status in ('UNVERIFIED','ACTIVE','INVALID','DISCONNECTED')),
  at timestamptz not null default now()
);
alter table public.vwork_connection_events enable row level security;
revoke all on public.vwork_connection_events from public, anon, authenticated;
grant select, insert on public.vwork_connection_events to service_role;
revoke all on sequence public.vwork_connection_events_id_seq from public, anon, authenticated;
grant usage, select on sequence public.vwork_connection_events_id_seq to service_role;
