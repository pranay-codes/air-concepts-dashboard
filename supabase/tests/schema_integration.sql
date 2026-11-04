-- DB-002 integration check: verifies the migrated schema, grants and RLS.
-- Read-only by design: one SELECT that writes nothing, so it runs under `db query --read-only` on any target.
-- Each column returns PASS, or raises an error naming the failed check, so the CLI exits non-zero.
-- Run on the throwaway database:  supabase db query --read-only -f supabase/tests/schema_integration.sql
-- Run on Test:                    supabase db query --remote --read-only -f supabase/tests/schema_integration.sql

select
  coalesce((case when to_regclass('public.entities') is null
    then 'FAIL: public.entities does not exist' end)::int::text,
    'PASS: public.entities exists') as table_exists,
  coalesce((case when not coalesce((select relrowsecurity from pg_class where oid = to_regclass('public.entities')), false)
    then 'FAIL: RLS is not enabled on public.entities' end)::int::text,
    'PASS: RLS enabled on public.entities') as rls_enabled,
  coalesce((case when not exists (
      select 1 from pg_policies
      where schemaname = 'public'
        and tablename = 'entities'
        and policyname = 'entities_select_authenticated'
        and cmd = 'SELECT'
        and roles @> array['authenticated']::name[])
    then 'FAIL: select policy for authenticated is missing' end)::int::text,
    'PASS: select policy for authenticated present') as select_policy,
  coalesce((case when has_table_privilege('anon', 'public.entities', 'SELECT')
    then 'FAIL: anon can select from public.entities' end)::int::text,
    'PASS: anon cannot select from public.entities') as anon_select,
  coalesce((case when not has_table_privilege('authenticated', 'public.entities', 'SELECT')
    then 'FAIL: authenticated cannot select from public.entities' end)::int::text,
    'PASS: authenticated can select from public.entities') as authenticated_select,
  coalesce((case when has_table_privilege('anon', 'public.entities', 'INSERT')
      or has_table_privilege('anon', 'public.entities', 'UPDATE')
      or has_table_privilege('anon', 'public.entities', 'DELETE')
      or has_table_privilege('authenticated', 'public.entities', 'INSERT')
      or has_table_privilege('authenticated', 'public.entities', 'UPDATE')
      or has_table_privilege('authenticated', 'public.entities', 'DELETE')
    then 'FAIL: anon or authenticated can write to public.entities' end)::int::text,
    'PASS: anon and authenticated have no write privileges') as no_writes;
