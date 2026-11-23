-- Green line: allow one Xero quote write, "mark quote accepted", through the external operation ledger.
-- Forward-only. No DROP TABLE, TRUNCATE or unscoped DELETE. Existing check constraints are located by
-- definition, not by assumed name, and replaced with versions that also admit the new kind and outcome.
set lock_timeout = '5s';

do $$
declare
  c record;
begin
  for c in
    select conname from pg_constraint
    where conrelid = 'public.external_operations'::regclass and contype = 'c'
      and (pg_get_constraintdef(oid) like '%XERO_ATTACH_REPORT%'
        or pg_get_constraintdef(oid) like '%LINKED_EXISTING%')
  loop
    execute format('alter table public.external_operations drop constraint %I', c.conname);
  end loop;
end $$;

alter table public.external_operations
  add constraint external_operations_kind_check
  check (kind in ('VWORK_CREATE_JOB','XERO_CREATE_INVOICE','XERO_ATTACH_REPORT','XERO_ACCEPT_QUOTE'));

alter table public.external_operations
  add constraint external_operations_outcome_check
  check (outcome in ('CREATED','LINKED_EXISTING','NOT_FOUND','ACCEPTED'));

alter table public.external_operations
  add constraint external_operations_success_outcome_check
  check ((status = 'SUCCEEDED' and outcome is not null and outcome in ('CREATED','LINKED_EXISTING','ACCEPTED') and external_id is not null)
    or (status = 'FAILED' and (outcome is null or outcome = 'NOT_FOUND') and external_id is null)
    or (status in ('PENDING','UNKNOWN') and outcome is null and external_id is null));

-- Accepting a quote is not repeatable once it has succeeded, so keep one successful acceptance per work item.
create unique index external_operations_one_quote_accept on public.external_operations(work_item_id,kind)
  where status = 'SUCCEEDED' and kind = 'XERO_ACCEPT_QUOTE';
