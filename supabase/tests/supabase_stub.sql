-- Minimal stand-ins for what Supabase provides, so the migration can be tested on plain Postgres.
create schema if not exists auth;
create table if not exists auth.users (id uuid primary key, email text);
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub', '')::uuid
$$;
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
end $$;
grant usage on schema auth to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;

-- The website's admin check (apps/web/src/server/admin/schema.ts in the stepq repo).
create schema if not exists stepq;
create table if not exists stepq.admins (user_id uuid primary key, email text not null);
create or replace function stepq.is_admin() returns boolean
language sql stable security definer set search_path = ''
as $fn$
  select exists (
    select 1 from stepq.admins
    where user_id = (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')::uuid
  )
$fn$;
grant usage on schema stepq to authenticated;
grant execute on function stepq.is_admin() to authenticated;
