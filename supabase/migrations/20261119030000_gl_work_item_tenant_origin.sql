-- Preserve unknown historical origins rather than inferring them from a potentially changed mapping.
-- Forward-only expand step; no data is backfilled or removed.
set lock_timeout = '5s';
alter table public.work_items add column xero_tenant_id uuid;
create index work_items_entity_tenant on public.work_items(entity_id, xero_tenant_id);
