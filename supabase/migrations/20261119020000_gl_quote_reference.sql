-- Preserve Xero's complete Reference (up to 4,000 characters) without truncating Job IDs.
-- Forward-only expansion. Application code must flag references longer than the Job ID limit.
set lock_timeout = '5s';
alter table public.work_items add column xero_quote_reference text
  check (xero_quote_reference is null or length(xero_quote_reference) <= 4000);
