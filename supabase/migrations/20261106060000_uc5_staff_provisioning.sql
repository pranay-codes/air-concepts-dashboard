-- FR-UC15-16: reserve identity before an external Auth create; retries reconcile the same UUID.
set lock_timeout = '5s';
create table public.staff_provisions (
  id uuid primary key default gen_random_uuid(),
  email text not null unique check (email = lower(email) and length(email) between 3 and 254),
  display_name text not null check (length(trim(display_name)) between 1 and 160),
  role text not null check (role in ('DIRECTOR', 'FINANCIAL_ADMIN', 'STANDARD_USER')),
  created_at timestamptz not null default now()
);
alter table public.staff_provisions enable row level security;
revoke all on table public.staff_provisions from public, anon, authenticated;
grant select, insert on table public.staff_provisions to service_role;
