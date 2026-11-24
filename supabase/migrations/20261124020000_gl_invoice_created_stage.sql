-- Green line: add the Invoice created stage (GL-STATUS-08). Stage is the single source of truth for where an item is.
-- An item whose Xero invoice exists but has not been sent is INVOICE_CREATED. Sent and authorised moves it to INVOICED.
-- Forward-only: no rows are deleted. Existing items at JOB_COMPLETED that already have a linked Xero invoice are
-- remapped to INVOICE_CREATED, because that is what they are. Lock timeout keeps the change from queueing behind traffic.

set local lock_timeout = '5s';

alter table public.work_items drop constraint if exists work_items_stage_check;

update public.work_items set stage = 'INVOICE_CREATED' where stage = 'JOB_COMPLETED' and xero_invoice_id is not null;

alter table public.work_items add constraint work_items_stage_check check (
  stage in ('QUOTED', 'TO_SCHEDULE', 'SCHEDULED', 'WORK_IN_PROGRESS', 'JOB_COMPLETED', 'INVOICE_CREATED', 'INVOICED', 'CLOSED'));
