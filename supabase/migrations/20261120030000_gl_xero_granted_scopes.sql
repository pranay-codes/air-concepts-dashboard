-- Existing grants have unknown permissions until the provider supplies evidence.
set lock_timeout = '5s';
alter table public.xero_grants add column granted_scopes text[];
