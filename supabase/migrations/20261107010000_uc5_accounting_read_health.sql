-- FR-UC15-04/13/30: accounting permissions and limits belong to an entity connection, not every tenant on a shared grant.
set lock_timeout = '5s';
alter table public.xero_entity_connections
  add column read_error_code text check (read_error_code ~ '^[A-Z0-9_]{1,80}$'),
  add column read_upstream_status integer check (read_upstream_status between 0 and 599),
  add column read_retry_at timestamptz,
  add column read_updated_at timestamptz;
-- Existing backend-only RLS and grants continue to cover these columns. No browser grant is added.
