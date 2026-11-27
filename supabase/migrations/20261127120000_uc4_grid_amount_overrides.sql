create table public.forecast_grid_amount_overrides (
  entity_id uuid not null references public.entities(id) on delete restrict,
  forecast_month date not null check (extract(day from forecast_month) = 1),
  direction text not null check (direction in ('IN', 'OUT')),
  scope text not null check (scope in ('COMPANY', 'DOCUMENT', 'MANUAL')),
  scope_key text not null check (length(scope_key) between 1 and 500),
  display_company text,
  display_number text,
  amount numeric(18,2) not null check (amount >= 0),
  active boolean not null default true,
  updated_by uuid not null references public.staff_users(id) on delete restrict,
  updated_at timestamptz not null default now(),
  primary key (entity_id, forecast_month, direction, scope, scope_key),
  check (scope = 'COMPANY' or scope_key ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
);
alter table public.forecast_grid_amount_overrides enable row level security;
revoke all on public.forecast_grid_amount_overrides from public, anon, authenticated;
grant select, insert, update on public.forecast_grid_amount_overrides to service_role;
