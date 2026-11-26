set lock_timeout = '5s';

alter table public.green_line_entity_settings
  add column automatic_sync_enabled boolean not null default false,
  add column automatic_sync_interval_minutes integer not null default 10
    check (automatic_sync_interval_minutes between 10 and 1440),
  add column automatic_sync_next_at timestamptz;

create table public.vwork_sync_rate_limit (
  connection_id integer primary key references public.vwork_connection(id),
  connection_revision text not null,
  retry_at timestamptz not null
);
alter table public.vwork_sync_rate_limit enable row level security;
revoke all on table public.vwork_sync_rate_limit from public, anon, authenticated, service_role;
grant select, insert, update on table public.vwork_sync_rate_limit to service_role;
