-- FR-UC04-03: optional placement of a manual line within its section.
set lock_timeout = '5s';
alter table public.forecast_manual_items
  add column grid_before_company text check (grid_before_company is null or length(grid_before_company) <= 248);

-- Manual contributions are captured for each generated current/future month. Once a month
-- passes, later item edits do not update its saved contribution.
create table public.forecast_grid_manual_snapshots (
  entity_id uuid not null,
  forecast_month date not null check (extract(day from forecast_month) = 1),
  manual_item_id uuid not null,
  direction text not null check (direction in ('IN', 'OUT')),
  description text not null,
  amount numeric(18,2) not null check (amount > 0),
  active boolean not null,
  primary key (entity_id, forecast_month, manual_item_id),
  foreign key (entity_id, manual_item_id) references public.forecast_manual_items(entity_id, id) on delete restrict
);
alter table public.forecast_grid_manual_snapshots enable row level security;
revoke all on public.forecast_grid_manual_snapshots from public, anon, authenticated;
grant select, insert, update on public.forecast_grid_manual_snapshots to service_role;

insert into public.forecast_grid_manual_snapshots(entity_id, forecast_month, manual_item_id, direction, description, amount, active)
select months.entity_id, months.forecast_month, item.id, item.direction, item.description, item.amount,
       item.active and months.forecast_month >= item.start_month
       and (item.end_month is null or months.forecast_month <= item.end_month)
       and (item.recurrence <> 'ONE_OFF' or months.forecast_month = item.start_month)
from (
  select entity_id, forecast_month from public.forecast_grid_revisions
  union
  select entity_id, forecast_month from public.forecast_generated_months
) months
join public.forecast_manual_items item on item.entity_id = months.entity_id
where months.forecast_month >= date_trunc('month', now() at time zone 'Pacific/Auckland')::date;
