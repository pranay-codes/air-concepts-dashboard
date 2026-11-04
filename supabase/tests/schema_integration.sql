-- DB-002 integration check: verifies the migrated schema, grants and RLS.
-- Read-only by design: it reads the catalog and writes nothing, so it is safe on the Test project.
-- Run with: supabase db query --remote --read-only -f supabase/tests/schema_integration.sql
-- Any failed assertion raises an exception and the command exits non-zero.

do $$
begin
  if to_regclass('public.entities') is null then
    raise exception 'FAIL: public.entities does not exist';
  end if;
  raise notice 'PASS: public.entities exists';
end $$;

do $$
begin
  if not (select relrowsecurity from pg_class where oid = 'public.entities'::regclass) then
    raise exception 'FAIL: RLS is not enabled on public.entities';
  end if;
  raise notice 'PASS: RLS enabled on public.entities';
end $$;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename = 'entities'
      and policyname = 'entities_select_authenticated'
      and cmd = 'SELECT'
      and roles @> array['authenticated']::name[]
  ) then
    raise exception 'FAIL: select policy for authenticated is missing';
  end if;
  raise notice 'PASS: select policy for authenticated present';
end $$;

do $$
begin
  if has_table_privilege('anon', 'public.entities', 'SELECT') then
    raise exception 'FAIL: anon can select from public.entities';
  end if;
  if not has_table_privilege('authenticated', 'public.entities', 'SELECT') then
    raise exception 'FAIL: authenticated cannot select from public.entities';
  end if;
  raise notice 'PASS: select granted to authenticated only';
end $$;

do $$
begin
  if has_table_privilege('anon', 'public.entities', 'INSERT')
    or has_table_privilege('anon', 'public.entities', 'UPDATE')
    or has_table_privilege('anon', 'public.entities', 'DELETE')
    or has_table_privilege('authenticated', 'public.entities', 'INSERT')
    or has_table_privilege('authenticated', 'public.entities', 'UPDATE')
    or has_table_privilege('authenticated', 'public.entities', 'DELETE') then
    raise exception 'FAIL: anon or authenticated can write to public.entities';
  end if;
  raise notice 'PASS: anon and authenticated have no write privileges';
end $$;
