-- First Director bootstrap (UC5, FR-UC15-18).
--
-- DRAFT: not yet reviewed, and not run against any database.
-- A human runs this once against the Test database. It is not a migration: CI applies only
-- supabase/migrations, so CI never runs this file.
--
-- Before running:
--   1. In the Test Supabase project, create the Auth user for the Director:
--      Authentication > Users, with the email address set and the email confirmed.
--      Note the user's UUID.
--   2. Have the approved display name ready.
--
-- Run it from a Test-only session. The database URL carries the password, so keep it in a
-- secure store and do not paste it into chat or commit it:
--
--   psql "$TEST_DATABASE_URL" -v ON_ERROR_STOP=1 \
--     -v director_id='<auth user uuid>' \
--     -v director_email='<director email>' \
--     -v display_name='<approved display name>' \
--     -f infra/scripts/bootstrap-first-director.sql
--
-- Safety properties:
--   * One transaction. It commits only when exactly one row is inserted, otherwise it rolls back.
--   * Takes advisory lock 150016 first, so two bootstraps cannot run at once.
--   * Refuses if an active Director already exists.
--   * Inserts only if the Auth user exists with that UUID and that email, and the email is confirmed.
--     On a database without that Auth identity, nothing is inserted.
--   * Inputs reach SQL only as psql variables, quoted by psql (:'name'). They are never built into
--     the SQL text by hand. This is psql quoting, not a server-side bound parameter.
--   * Reads back only id, display name, role and active. The email is not printed.

\set ON_ERROR_STOP on
set lock_timeout = '5s';

begin;

select pg_advisory_xact_lock(150016);

with inserted as (
  insert into public.staff_users (id, email, display_name, role, active)
  select u.id, lower(u.email), :'display_name', 'DIRECTOR', true
  from auth.users u
  where u.id = :'director_id'::uuid
    and lower(u.email) = lower(:'director_email')
    and u.email_confirmed_at is not null
    and not exists (
      select 1 from public.staff_users s where s.role = 'DIRECTOR' and s.active
    )
  on conflict (id) do nothing
  returning id
)
select count(*) as inserted_count from inserted \gset

\if :inserted_count
  select id, display_name, role, active
  from public.staff_users
  where id = :'director_id'::uuid;
  commit;
  \echo 'Director created. The Director signs in with the emailed code.'
\else
  rollback;
  \echo 'Nothing created. Check that the Auth user exists with this UUID and email, that the email is confirmed, that this staff record does not already exist, and that no active Director exists.'
  \quit 1
\endif
