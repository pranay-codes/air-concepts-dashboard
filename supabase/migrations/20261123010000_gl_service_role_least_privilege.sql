-- Green line: narrow service_role to the intended privileges on the green-line tables.
-- The earlier migrations revoked browser roles but left Supabase's default ALL grant
-- for service_role, so audit events were not append-only. Forward-only. No DROP,
-- TRUNCATE or DELETE.
set lock_timeout = '5s';

revoke all on table
  public.green_line_entity_settings,
  public.work_items,
  public.work_item_events,
  public.vwork_connection,
  public.vwork_entity_templates,
  public.vwork_connection_events,
  public.external_operations,
  public.green_line_sync_runs
from service_role;

grant select, insert, update on table
  public.green_line_entity_settings,
  public.work_items,
  public.vwork_connection,
  public.external_operations,
  public.green_line_sync_runs
to service_role;

grant select, insert, update, delete on table public.vwork_entity_templates to service_role;

-- Audit events are append-only.
grant select, insert on table
  public.work_item_events,
  public.vwork_connection_events
to service_role;

revoke all on sequence
  public.work_item_events_id_seq,
  public.vwork_connection_events_id_seq
from service_role;

grant usage, select on sequence
  public.work_item_events_id_seq,
  public.vwork_connection_events_id_seq
to service_role;
