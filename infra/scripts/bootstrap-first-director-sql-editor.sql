-- First Director bootstrap, Supabase SQL Editor version (UC5, FR-UC15-18).
--
-- DRAFT: not yet reviewed, and not run against any database.
-- Run once, by a human, in the TEST Supabase project's SQL Editor. CI never runs this file.
-- The psql version (bootstrap-first-director.sql) is the reviewed-variable form for psql.
--
-- Before running:
--   1. Test project only. Authentication > Users > Add user: the Director's email,
--      Auto Confirm User ticked. Copy the user's UUID.
--   2. Fill in the three values in the first statement. Keep the quotes. Double any
--      apostrophe inside a name, for example O''Brien.
--   3. Run the whole script once. Do not run lines separately.
--
-- If it reports an error, run "rollback;" before anything else. Nothing is committed on error.
--
-- Safety properties:
--   * One transaction. It commits only when exactly one row is inserted.
--   * Takes advisory lock 150016 first, so two bootstraps cannot run at once.
--   * Refuses if an active Director already exists.
--   * Inserts only if the Auth user exists with that UUID and that email, and the email is confirmed.
--   * The values are typed once, in the set_config line. The insert reads them back with
--     current_setting, so they are data, not SQL text.
--   * Shows only id, display name, role and active. The email is not printed.

begin;

-- Fill in the three values. Transaction-local, so they disappear at commit or rollback.
select
  set_config('bootstrap.director_id', '<auth user uuid>', true),
  set_config('bootstrap.director_email', '<director email>', true),
  set_config('bootstrap.display_name', '<approved display name>', true);

set local lock_timeout = '5s';
select pg_advisory_xact_lock(150016);

do $$
declare
  v_rows integer;
begin
  if exists (select 1 from public.staff_users where role = 'DIRECTOR' and active) then
    raise exception 'An active Director already exists. Nothing changed.';
  end if;

  insert into public.staff_users (id, email, display_name, role, active)
  select u.id, lower(u.email), current_setting('bootstrap.display_name'), 'DIRECTOR', true
  from auth.users u
  where u.id = current_setting('bootstrap.director_id')::uuid
    and lower(u.email) = lower(current_setting('bootstrap.director_email'))
    and u.email_confirmed_at is not null
  on conflict (id) do nothing;

  get diagnostics v_rows = row_count;
  if v_rows <> 1 then
    raise exception 'Nothing created. Check the Auth user UUID and email, that the email is confirmed, and that this staff record does not already exist. Nothing changed.';
  end if;
end $$;

select id, display_name, role, active
from public.staff_users
where id = current_setting('bootstrap.director_id')::uuid;

commit;
