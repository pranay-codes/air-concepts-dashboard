-- FR-UC15-01/02: entity rename and archive; show who connected each organisation.
-- Forward-only and additive. Archiving hides an entity but removes no invoices, assignments or Xero grants.
set lock_timeout = '5s';

-- Existing rows stay active (archived_at is null). Existing connections have no recorded Director until reconnected.
alter table public.entities add column archived_at timestamptz;

alter table public.xero_entity_connections
  add column connected_by uuid references public.staff_users(id) on delete restrict;

-- Existing RLS and service-role-only grants on both tables continue to cover the new columns. No browser grant is added.
