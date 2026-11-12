-- A15–A17: join a group with its invite code, and only that way.
--
-- members_insert used to let anyone insert themselves into any group whose id they had. Now
-- a creator adds themselves and their friends (A16); everyone else joins through
-- app.join_group(code), which checks the invite code.
drop policy if exists members_insert on app.group_members;
create policy members_insert on app.group_members for insert to authenticated
  with check (
    exists (select 1 from app.groups g where g.id = group_id and g.created_by = auth.uid())
    and (user_id = auth.uid() or app.are_friends(auth.uid(), user_id))
  );

-- Joins the caller to the group with this invite code. Returns the group id, or null when no
-- group has that code. Joining twice is harmless.
create or replace function app.join_group(code text) returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  gid uuid;
begin
  if auth.uid() is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  select id into gid from app.groups where invite_code = lower(trim(code));
  if gid is null then
    return null;
  end if;
  insert into app.group_members (group_id, user_id) values (gid, auth.uid())
    on conflict do nothing;
  return gid;
end
$$;

revoke all on function app.join_group(text) from public, anon;
grant execute on function app.join_group(text) to authenticated;
