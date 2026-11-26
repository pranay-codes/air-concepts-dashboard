set lock_timeout = '5s';

alter table public.green_line_entity_settings
  drop constraint green_line_entity_settings_automatic_sync_interval_minute_check,
  add constraint green_line_auto_sync_interval_check
    check (automatic_sync_interval_minutes between 1 and 1440);
