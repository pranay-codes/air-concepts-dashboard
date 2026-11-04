-- DB-002: first migration, kept to the reference table needed for the first authenticated API test.
-- Each entity (services, commercial, hotas) has its own Xero organisation (AGENTS.md section 7).
-- Forward-only: fix later changes with a new migration, never by editing this file.

set lock_timeout = '5s';

create table if not exists public.entities (
  id uuid primary key default gen_random_uuid(),
  code text not null unique check (code in ('services', 'commercial', 'hotas')),
  display_name text not null,
  created_at timestamptz not null default now()
);

alter table public.entities enable row level security;

revoke all on table public.entities from anon, authenticated;
grant select on table public.entities to authenticated;

-- Read-only reference data: any signed-in user may read the three entity rows.
-- Writes are not granted to anon or authenticated; they happen only through migrations and seed.
create policy entities_select_authenticated
  on public.entities
  for select
  to authenticated
  using (true);
