-- FR-UC15-02/12/16/18/19: dynamic entities and server-owned active staff roles.
-- Forward-only; no rows or financial history are removed.
set lock_timeout = '5s';

-- Remove the legacy fixed-name restriction, retaining the existing unique code/index.
alter table public.entities drop constraint entities_code_check;
alter table public.entities add constraint entities_code_format
  check (length(code) between 1 and 80 and code ~ '^[a-z0-9][a-z0-9_-]*$');
alter table public.entities add constraint entities_display_name_length
  check (length(trim(display_name)) between 1 and 160);

create table public.staff_users (
  id uuid primary key references auth.users(id) on delete restrict,
  email text not null check (length(email) between 3 and 254),
  display_name text not null check (length(trim(display_name)) between 1 and 160),
  role text not null check (role in ('DIRECTOR', 'FINANCIAL_ADMIN', 'STANDARD_USER')),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index staff_users_email_unique on public.staff_users (lower(email));

alter table public.staff_users enable row level security;
revoke all on table public.staff_users from public, anon, authenticated;
grant select, insert, update on table public.staff_users to service_role;

-- Browser access goes through the API, which checks current active status on every call.
-- Keep the previous policy immutable in migration history; no browser table grants remain.
revoke all on table public.entities from anon, authenticated;
grant select, insert, update on table public.entities to service_role;
