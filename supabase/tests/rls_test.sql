-- Run: psql -v ON_ERROR_STOP=1 -f supabase_stub.sql -f ../migrations/*.sql -f rls_test.sql
-- Each block acts as one user and checks what RLS allows. Any failed check raises.
\set ON_ERROR_STOP 1

insert into auth.users values
  ('00000000-0000-0000-0000-00000000000a', 'riya@test'),
  ('00000000-0000-0000-0000-00000000000b', 'arjun@test'),
  ('00000000-0000-0000-0000-00000000000c', 'stranger@test');

create or replace function pg_temp.as_user(u text) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', u, 'role', 'authenticated')::text, false);
  execute 'set role authenticated';
end $$;

create or replace function pg_temp.expect_error(stmt text, label text) returns void language plpgsql as $$
begin
  begin
    execute stmt;
  exception when others then
    raise notice 'ok: % (blocked: %)', label, sqlerrm;
    return;
  end;
  raise exception 'FAIL: % was allowed', label;
end $$;

create or replace function pg_temp.expect(cond boolean, label text) returns void language plpgsql as $$
begin
  if not cond then raise exception 'FAIL: %', label; end if;
  raise notice 'ok: %', label;
end $$;

-- Each user creates their own profile.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect(app.username_available('riya.walks'), 'username free before sign-up');
insert into app.profiles (id, display_name, username, birthdate) values (auth.uid(), 'Riya', 'riya.walks', '2001-03-14');
select pg_temp.expect(not app.username_available('Riya.Walks'), 'username taken, case-insensitive');
select pg_temp.expect_error($$insert into app.profiles (id, display_name, username) values ('00000000-0000-0000-0000-00000000000b', 'Fake', 'fake')$$, 'creating a profile for someone else');
select pg_temp.expect_error($$update app.profiles set plan = 'pro' where id = auth.uid()$$, 'upgrading own plan to pro');
select pg_temp.expect_error($$update app.profiles set birthdate = '1990-01-01' where id = auth.uid()$$, 'changing birthdate');
insert into app.daily_steps (user_id, day, steps) values (auth.uid(), current_date, 6240);
-- What the app's upsert sends: the same row again with new numbers.
insert into app.daily_steps (user_id, day, steps, distance_m) values (auth.uid(), current_date, 6240, 4800)
  on conflict (user_id, day) do update set user_id = excluded.user_id, day = excluded.day,
  steps = excluded.steps, distance_m = excluded.distance_m;
select pg_temp.expect((select distance_m from app.daily_steps where user_id = auth.uid()) = 4800, 'upserting own steps');
reset role;

select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
insert into app.profiles (id, display_name, username) values (auth.uid(), 'Arjun', 'arjun');
insert into app.daily_steps (user_id, day, steps) values (auth.uid(), current_date, 9120);
select pg_temp.expect((select count(*) from app.profiles) = 1, 'not friends yet: sees only own profile');
select pg_temp.expect((select count(*) from app.daily_steps) = 1, 'not friends yet: sees only own steps');
select pg_temp.expect((select count(*) from app.find_by_username('riya.walks')) = 1, 'finds Riya by exact username');
insert into app.friendships (requester, addressee) values (auth.uid(), '00000000-0000-0000-0000-00000000000a');
with u as (update app.friendships set status = 'accepted' where requester = auth.uid() returning 1)
select pg_temp.expect((select count(*) from u) = 0, 'requester cannot accept own request');
select pg_temp.expect_error($$insert into app.water_reminders (from_user, to_user) values (auth.uid(), '00000000-0000-0000-0000-00000000000a')$$, 'water reminder before friendship accepted');
reset role;

select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select username from app.friend_requests()) = 'arjun', 'sees who sent a pending request');
update app.friendships set status = 'accepted' where addressee = auth.uid();
select pg_temp.expect((select count(*) from app.friend_requests()) = 0, 'accepted request leaves the list');
select pg_temp.expect((select count(*) from app.profiles) = 2, 'friends: sees both profiles');
select pg_temp.expect((select count(*) from app.daily_steps) = 2, 'friends: sees both step rows');
select pg_temp.expect_error($$insert into app.daily_steps (user_id, day, steps) values ('00000000-0000-0000-0000-00000000000b', current_date - 1, 99999)$$, 'writing a friend''s steps');
select pg_temp.expect_error($$insert into app.daily_steps (user_id, day, steps) values ('00000000-0000-0000-0000-00000000000b', current_date, 99999) on conflict (user_id, day) do update set steps = excluded.steps$$, 'upserting over a friend''s steps');
insert into app.water_reminders (from_user, to_user)
  select auth.uid(), '00000000-0000-0000-0000-00000000000b' from generate_series(1, 5);
select pg_temp.expect_error($$insert into app.water_reminders (from_user, to_user) values (auth.uid(), '00000000-0000-0000-0000-00000000000b')$$, '6th water reminder on free plan');
insert into app.groups (name, created_by) values ('Morning walkers', auth.uid());
insert into app.group_members (group_id, user_id) select id, auth.uid() from app.groups;
insert into app.group_members (group_id, user_id) select id, '00000000-0000-0000-0000-00000000000b' from app.groups;
reset role;

-- A stranger sees nobody and can't join themselves into someone's leaderboard data.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
insert into app.profiles (id, display_name, username) values (auth.uid(), 'Stranger', 'stranger');
select pg_temp.expect((select count(*) from app.profiles) = 1, 'stranger: sees only own profile');
select pg_temp.expect((select count(*) from app.daily_steps) = 0, 'stranger: sees no steps');
select pg_temp.expect((select count(*) from app.groups) = 0, 'stranger: sees no groups');
select pg_temp.expect((select count(*) from app.water_reminders) = 0, 'stranger: sees no reminders');
select pg_temp.expect((select count(*) from app.friend_requests()) = 0, 'stranger: no requests to see');
reset role;
select set_config('test.group', (select id::text from app.groups limit 1), false);
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect_error($$insert into app.group_members (group_id, user_id) values (current_setting('test.group')::uuid, '00000000-0000-0000-0000-00000000000a')$$, 'adding someone else to a group');
select pg_temp.expect_error($$insert into app.group_members (group_id, user_id) values (current_setting('test.group')::uuid, auth.uid())$$, 'joining a group by id without its code');
select pg_temp.expect(app.join_group('not-a-code') is null, 'wrong invite code joins nothing');
reset role;
select set_config('test.code', (select invite_code from app.groups limit 1), false);
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect(app.join_group(current_setting('test.code')) = current_setting('test.group')::uuid, 'joins with the invite code');
select pg_temp.expect((select count(*) from app.group_members) = 3, 'new member sees the whole group');
select pg_temp.expect(app.join_group(upper(current_setting('test.code'))) is not null, 'joining twice is harmless');
delete from app.group_members where user_id = auth.uid();
select pg_temp.expect_error($$insert into app.challenges (title, starts_at, ends_at) values ('x', now(), now() + interval '1 day')$$, 'creating a challenge');
reset role;

-- An admin (listed in stepq.admins) can read every row, for the stepq.in dashboard; can't write.
insert into auth.users values ('00000000-0000-0000-0000-0000000000ad', 'admin@test');
insert into stepq.admins values ('00000000-0000-0000-0000-0000000000ad', 'admin@test');
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
select pg_temp.expect((select count(*) from app.profiles) = 3, 'admin: reads all profiles');
select pg_temp.expect((select count(*) from app.water_reminders) = 5, 'admin: reads all reminders');
with u as (update app.profiles set display_name = 'x' returning 1)
select pg_temp.expect((select count(*) from u) = 0, 'admin: cannot edit profiles');
reset role;

-- Anonymous (no sign-in) gets nothing at all.
select set_config('request.jwt.claims', '', false);
set role anon;
select pg_temp.expect_error('select * from app.profiles', 'anon reading profiles');
select pg_temp.expect_error($$select app.username_available('x')$$, 'anon checking usernames');
reset role;

\echo 'All RLS checks passed.'
