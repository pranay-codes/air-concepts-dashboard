-- FR-UC15-17/18: enforce passwordless, registered and active access at token issuance too.
set lock_timeout = '5s';

create function public.uc5_access_token_hook(event jsonb)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
begin
  if coalesce(event->>'authentication_method', '') not in ('otp', 'token_refresh')
      or not exists (
        select 1 from public.staff_users
        where id = nullif(event->>'user_id', '')::uuid and active = true
      ) then
    return jsonb_build_object('error', jsonb_build_object(
      'http_code', 403, 'message', 'An active registered dashboard account and email code are required.'));
  end if;
  return event;
exception when invalid_text_representation then
  return jsonb_build_object('error', jsonb_build_object(
    'http_code', 403, 'message', 'An active registered dashboard account is required.'));
end;
$$;

grant usage on schema public to supabase_auth_admin;
grant select (id, active) on table public.staff_users to supabase_auth_admin;
create policy staff_users_auth_hook on public.staff_users
  for select to supabase_auth_admin using (true);
revoke execute on function public.uc5_access_token_hook(jsonb) from public, anon, authenticated;
grant execute on function public.uc5_access_token_hook(jsonb) to supabase_auth_admin;
