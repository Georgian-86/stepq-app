-- Play Store readiness:
-- 1. app.delete_my_account(): people can delete their account from inside the app (required by
--    Google Play). Groups they created are handed to the longest-standing other member, so their
--    group mates keep the group; groups with no one else in them are deleted. Everything else
--    the account owns cascades from app.profiles / auth.users.
-- 2. app.water_inbox(since): water reminders friends sent me since a time, with the sender's
--    name, so the app can show them as notifications without push.
-- Re-runnable.

create or replace function app.delete_my_account()
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  me uuid := auth.uid();
  g record;
  heir uuid;
begin
  if me is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;

  for g in select id from app.groups where created_by = me loop
    select m.user_id into heir
    from app.group_members m
    where m.group_id = g.id and m.user_id <> me
    order by m.joined_at, m.user_id
    limit 1;
    if heir is not null then
      update app.groups set created_by = heir where id = g.id;
    end if;
  end loop;

  delete from app.profiles where id = me;
  delete from auth.users where id = me;
end;
$$;

revoke all on function app.delete_my_account() from public, anon;
grant execute on function app.delete_my_account() to authenticated;

create or replace function app.water_inbox(since timestamptz)
returns table (id bigint, from_name text, sent_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  select r.id, p.display_name, r.sent_at
  from app.water_reminders r
  join app.profiles p on p.id = r.from_user
  where r.to_user = auth.uid()
    and r.sent_at > greatest(since, now() - interval '2 days')
  order by r.sent_at desc
  limit 20
$$;

revoke all on function app.water_inbox(timestamptz) from public, anon;
grant execute on function app.water_inbox(timestamptz) to authenticated;
