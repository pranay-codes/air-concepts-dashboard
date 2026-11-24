-- Green line: approved stage set (Quoted, To schedule, Job scheduled, Work in progress, Job completed, Invoiced, plus Closed).
-- Forward-only. TO_INVOICE and INVOICE_DRAFTED are retired: an item awaiting an invoice is JOB_COMPLETED, and a linked
-- invoice is recorded by xero_invoice_id rather than by a stage. WORK_IN_PROGRESS is new. SCHEDULED keeps its name and
-- now means Job scheduled. Existing rows and their history are remapped; no rows are deleted.

set local lock_timeout = '5s';

alter table public.work_items drop constraint if exists work_items_stage_check;

update public.work_items set stage = 'JOB_COMPLETED' where stage in ('TO_INVOICE', 'INVOICE_DRAFTED');

update public.work_item_events set from_stage = 'JOB_COMPLETED' where from_stage in ('TO_INVOICE', 'INVOICE_DRAFTED');
update public.work_item_events set to_stage = 'JOB_COMPLETED' where to_stage in ('TO_INVOICE', 'INVOICE_DRAFTED');

alter table public.work_items add constraint work_items_stage_check check (
  stage in ('QUOTED', 'TO_SCHEDULE', 'SCHEDULED', 'WORK_IN_PROGRESS', 'JOB_COMPLETED', 'INVOICED', 'CLOSED'));
