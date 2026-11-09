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
  coalesce((case when has_table_privilege('authenticated', 'public.entities', 'SELECT')
    then 'FAIL: authenticated can bypass API active-user checks on entities' end)::int::text,
    'PASS: entity reads require backend active-user authorisation') as authenticated_select,
  coalesce((case when to_regclass('public.staff_users') is null
      or not coalesce((select relrowsecurity from pg_class where oid = to_regclass('public.staff_users')), false)
      or has_table_privilege('authenticated', 'public.staff_users', 'SELECT')
      or has_table_privilege('anon', 'public.staff_users', 'SELECT')
    then 'FAIL: staff table missing, RLS disabled or staff readable from browser' end)::int::text,
    'PASS: staff roles are server-owned with RLS') as staff_security,
  coalesce((case when to_regclass('public.staff_provisions') is null
      or not coalesce((select relrowsecurity from pg_class where oid = to_regclass('public.staff_provisions')), false)
      or has_table_privilege('authenticated', 'public.staff_provisions', 'SELECT')
      or has_table_privilege('anon', 'public.staff_provisions', 'SELECT')
    then 'FAIL: pending staff provisions are missing or browser-readable' end)::int::text,
    'PASS: pending staff provisions are server-owned with RLS') as staff_provision_security,
  coalesce((case when to_regprocedure('public.uc5_access_token_hook(jsonb)') is null
      or has_function_privilege('authenticated', 'public.uc5_access_token_hook(jsonb)', 'EXECUTE')
      or has_function_privilege('anon', 'public.uc5_access_token_hook(jsonb)', 'EXECUTE')
      or not has_function_privilege('supabase_auth_admin', 'public.uc5_access_token_hook(jsonb)', 'EXECUTE')
    then 'FAIL: Auth token hook is missing or has unsafe execution grants' end)::int::text,
    'PASS: token hook execution is restricted to Auth administration') as auth_hook_security,
  coalesce((case when exists (
      select 1 from unnest(array['public.xero_grants','public.xero_oauth_attempts','public.xero_entity_connections']) as tables(name)
      where to_regclass(name) is null
        or not coalesce((select relrowsecurity from pg_class where oid=to_regclass(name)),false)
        or has_table_privilege('authenticated',name,'SELECT')
        or has_table_privilege('anon',name,'SELECT'))
    then 'FAIL: Xero tables missing, RLS disabled or browser-readable' end)::int::text,
    'PASS: Xero grants, attempts and mappings are server-owned with RLS') as xero_security,
  coalesce((case when (select count(*) from information_schema.columns where table_schema='public'
      and table_name='xero_entity_connections' and column_name in
        ('read_error_code','read_upstream_status','read_retry_at','read_updated_at')) <> 4
    then 'FAIL: tenant accounting read-health columns are missing' end)::int::text,
    'PASS: tenant accounting read-health columns present') as xero_read_health,
  coalesce((case when to_regclass('public.invoice_assignments') is null
      or not coalesce((select relrowsecurity from pg_class where oid=to_regclass('public.invoice_assignments')),false)
      or has_table_privilege('authenticated','public.invoice_assignments','SELECT,INSERT,UPDATE,DELETE')
      or has_table_privilege('anon','public.invoice_assignments','SELECT,INSERT,UPDATE,DELETE')
    then 'FAIL: sales assignments missing or browser-accessible' end)::int::text,
    'PASS: sales assignments are server-owned with RLS') as assignment_security,
  coalesce((case when has_table_privilege('anon', 'public.entities', 'INSERT')
      or has_table_privilege('anon', 'public.entities', 'UPDATE')
      or has_table_privilege('anon', 'public.entities', 'DELETE')
      or has_table_privilege('authenticated', 'public.entities', 'INSERT')
      or has_table_privilege('authenticated', 'public.entities', 'UPDATE')
      or has_table_privilege('authenticated', 'public.entities', 'DELETE')
    then 'FAIL: anon or authenticated can write to public.entities' end)::int::text,
    'PASS: anon and authenticated have no write privileges') as no_writes;
