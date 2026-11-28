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
  select auth.uid(), '00000000-0000-0000-0000-00000000000b' from generate_series(1, 3);
select pg_temp.expect_error($$insert into app.water_reminders (from_user, to_user) values (auth.uid(), '00000000-0000-0000-0000-00000000000b')$$, '4th water reminder on free plan');
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
reset role;
insert into app.challenges (title, target_steps, starts_at, ends_at) values ('Walk 50k', 50000, now() - interval '1 day', now() + interval '6 days');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
insert into app.challenge_entries (challenge_id, user_id) select id, auth.uid() from app.challenges;
select pg_temp.expect((select joined from app.challenge_counts()) = 1, 'challenge count includes my entry');
select pg_temp.expect_error($$insert into app.challenge_entries (challenge_id, user_id) select id, '00000000-0000-0000-0000-00000000000a' from app.challenges$$, 'joining someone else to a challenge');
select pg_temp.expect_error($$insert into app.challenges (title, starts_at, ends_at) values ('x', now(), now() + interval '1 day')$$, 'creating a challenge');
reset role;

-- QRuns: anyone signed in sees the feed with the host's name, but can only post and join as themselves.
-- Posting needs Pro and an admin's OK (see the Pro checks below); the stranger has both here.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect_error($$insert into app.qruns (created_by, title, place_name, starts_at) values (auth.uid(), 'Free run', 'X', now() + interval '1 day')$$, 'free member posting a QRun');
reset role;
update app.profiles set plan = 'pro', qrun_host = true where id = '00000000-0000-0000-0000-00000000000c';
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
insert into app.qruns (created_by, title, place_name, starts_at) values (auth.uid(), 'Morning run', 'Carter Road', now() + interval '1 day');
insert into app.qrun_participants (qrun_id, user_id) select id, auth.uid() from app.qruns;
select pg_temp.expect_error($$insert into app.qruns (created_by, title, place_name, starts_at) values (auth.uid(), 'Past', 'X', now() - interval '1 hour')$$, 'posting a QRun in the past');
select pg_temp.expect_error($$insert into app.qruns (created_by, title, place_name, starts_at) values ('00000000-0000-0000-0000-00000000000a', 'Fake', 'X', now() + interval '1 day')$$, 'posting a QRun as someone else');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select count(*) from app.profiles where username = 'stranger') = 0, 'host profile stays hidden from strangers');
select pg_temp.expect((select host_name from app.qrun_feed()) = 'Stranger', 'feed shows the host''s name');
select pg_temp.expect((select going from app.qrun_feed()) = 1 and not (select joined from app.qrun_feed()), 'feed counts going, not joined yet');
select pg_temp.expect_error($$insert into app.qrun_participants (qrun_id, user_id) select id, '00000000-0000-0000-0000-00000000000b' from app.qruns$$, 'joining someone else to a QRun');
insert into app.qrun_participants (qrun_id, user_id) select id, auth.uid() from app.qruns;
select pg_temp.expect((select joined from app.qrun_feed()) and (select going from app.qrun_feed()) = 2, 'joined QRun shows as joined');
with u as (update app.qruns set title = 'Hijacked' returning 1)
select pg_temp.expect((select count(*) from u) = 0, 'editing someone else''s QRun');
with d as (delete from app.qruns returning 1)
select pg_temp.expect((select count(*) from d) = 0, 'deleting someone else''s QRun');
delete from app.qrun_participants where user_id = auth.uid();
reset role;
set role anon;
select pg_temp.expect_error('select * from app.qrun_feed()', 'anon reading the QRun feed');
reset role;

-- Country rankings: everyone in my country, with only name, avatar and totals.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
update app.profiles set country = 'IN' where id = auth.uid();
select pg_temp.expect_error($$update app.profiles set country = 'india' where id = auth.uid()$$, 'country must be a 2-letter code');
with u as (update app.profiles set country = 'US' where id = '00000000-0000-0000-0000-00000000000c' returning 1)
select pg_temp.expect((select count(*) from u) = 0, 'setting someone else''s country');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
update app.profiles set country = 'IN' where id = auth.uid();
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
update app.profiles set country = 'IN' where id = auth.uid();
select pg_temp.expect((select count(*) from app.country_ranking(current_date - 6, current_date)) = 3, 'ranking includes strangers in my country');
select pg_temp.expect((select display_name from app.country_ranking(current_date - 6, current_date) where rank = 1) = 'Arjun', 'most steps ranks first');
select pg_temp.expect((select rank from app.country_ranking(current_date - 6, current_date) where is_me) = 3, 'I''m ranked even with no steps');
select pg_temp.expect((select count(*) from app.daily_steps) = 0, 'stranger still can''t read anyone''s daily steps');
select pg_temp.expect((select count(*) from app.country_ranking(current_date - 6, current_date, 1)) = 2, 'top rows plus my own row');
select pg_temp.expect((select count(*) from app.country_ranking(current_date - 365, current_date)) = 0, 'ranking spans at most 40 days');
update app.profiles set country = 'US' where id = auth.uid();
select pg_temp.expect((select count(*) from app.country_ranking(current_date - 6, current_date)) = 1, 'other countries are ranked separately');
reset role;
set role anon;
select pg_temp.expect_error($$select * from app.country_ranking(current_date - 6, current_date)$$, 'anon reading rankings');
reset role;

-- Water reminders reach the friend they were sent to, with the sender's name.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
select pg_temp.expect((select count(*) from app.water_inbox(now() - interval '1 hour')) = 3, 'inbox: reminders sent to me');
select pg_temp.expect((select bool_and(from_name = 'Riya') from app.water_inbox(now() - interval '1 hour')), 'inbox: sender name');
select pg_temp.expect((select count(*) from app.water_inbox(now() + interval '1 minute')) = 0, 'inbox: nothing newer than since');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select count(*) from app.water_inbox(now() - interval '1 hour')) = 0, 'inbox: not reminders I sent');
reset role;

-- Deleting an account removes it; a group it created passes to the next member.
insert into auth.users values ('00000000-0000-0000-0000-00000000000d', 'leaver@test');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000d');
insert into app.profiles (id, display_name, username) values (auth.uid(), 'Leaver', 'leaver');
insert into app.groups (name, created_by) values ('Shared walk', auth.uid()), ('Solo walk', auth.uid());
insert into app.group_members (group_id, user_id) select id, auth.uid() from app.groups where created_by = auth.uid();
reset role;
insert into app.group_members (group_id, user_id) select id, '00000000-0000-0000-0000-00000000000b' from app.groups where name = 'Shared walk';
select pg_temp.as_user('00000000-0000-0000-0000-00000000000d');
select app.delete_my_account();
reset role;
select pg_temp.expect(not exists (select 1 from auth.users where id = '00000000-0000-0000-0000-00000000000d'), 'delete: auth user gone');
select pg_temp.expect(not exists (select 1 from app.profiles where id = '00000000-0000-0000-0000-00000000000d'), 'delete: profile gone');
select pg_temp.expect((select created_by from app.groups where name = 'Shared walk') = '00000000-0000-0000-0000-00000000000b', 'delete: shared group handed to a member');
select pg_temp.expect(not exists (select 1 from app.groups where name = 'Solo walk'), 'delete: solo group removed');
select pg_temp.expect((select count(*) from app.profiles) = 3, 'delete: other accounts untouched');
select pg_temp.expect((select email from app.deleted_accounts where user_id = '00000000-0000-0000-0000-00000000000d') = 'leaver@test', 'delete: registration details kept for the IT Rules 180 days');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
select pg_temp.expect((select count(*) from app.deleted_accounts) = 0, 'delete: archived details hidden from users');
reset role;
select set_config('request.jwt.claims', '', false);
set role anon;
select pg_temp.expect_error($$select app.delete_my_account()$$, 'anon deleting an account');
reset role;

-- An admin (listed in stepq.admins) can read every row, for the stepq.in dashboard; can't write.
insert into auth.users values ('00000000-0000-0000-0000-0000000000ad', 'admin@test');
insert into stepq.admins values ('00000000-0000-0000-0000-0000000000ad', 'admin@test');
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
select pg_temp.expect((select count(*) from app.profiles) = 3, 'admin: reads all profiles');
select pg_temp.expect((select count(*) from app.water_reminders) = 3, 'admin: reads all reminders');
with u as (update app.profiles set display_name = 'x' returning 1)
select pg_temp.expect((select count(*) from u) = 0, 'admin: cannot edit profiles');
reset role;

-- Add friends: search as you type, people you may know, contacts. Public fields only.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect((select count(*) from app.search_people('ri')) = 1, 'search finds Riya by the start of her name');
select pg_temp.expect((select count(*) from app.search_people('@riya.w')) = 1, 'search by username with @');
select pg_temp.expect((select count(*) from app.search_people('r')) = 0, 'search needs 2 characters');
select pg_temp.expect((select count(*) from app.search_people('%%')) = 0, 'search wildcards are literal');
select pg_temp.expect((select count(*) from app.search_people('riya_')) = 0, 'underscore is not a wildcard');
select pg_temp.expect((select count(*) from app.search_people('stranger')) = 0, 'search leaves out myself');
select pg_temp.expect((select count(*) from app.people_you_may_know()) = 0, 'no friends, no suggestions');
insert into app.friendships (requester, addressee) values (auth.uid(), '00000000-0000-0000-0000-00000000000b');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
update app.friendships set status = 'accepted' where addressee = auth.uid();
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect((select username from app.people_you_may_know()) = 'riya.walks', 'friend of a friend is suggested');
select pg_temp.expect((select mutual from app.people_you_may_know()) = 1, 'suggestion counts mutual friends');
select pg_temp.expect((select mutual from app.search_people('riya')) = 1, 'search shows mutual friends');
select pg_temp.expect(
  (select username from app.match_contacts(array[encode(sha256(convert_to('Riya@Test ', 'UTF8')), 'hex')])) is null,
  'contacts: hash must be of the trimmed, lower-cased email');
select pg_temp.expect(
  (select username from app.match_contacts(array[encode(sha256(convert_to('riya@test', 'UTF8')), 'hex')])) = 'riya.walks',
  'contacts: matching email hash finds Riya');
select pg_temp.expect(
  (select count(*) from app.match_contacts(array[encode(sha256(convert_to('stranger@test', 'UTF8')), 'hex')])) = 0,
  'contacts: leaves out myself');
select pg_temp.expect((select count(*) from app.people_you_may_know() where username = 'arjun') = 0, 'friends are not suggested');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select username from app.people_you_may_know()) = 'stranger', 'suggestions work both ways');
-- A pending request (either way) drops them from suggestions.
insert into app.friendships (requester, addressee) values (auth.uid(), '00000000-0000-0000-0000-00000000000c');
select pg_temp.expect((select count(*) from app.people_you_may_know()) = 0, 'requested people are not suggested');
delete from app.friendships where addressee = '00000000-0000-0000-0000-00000000000c';
reset role;

-- Daily goal changes wait for the next day.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
update app.profiles set next_daily_goal = 12000, next_goal_from = current_date + 1 where id = auth.uid();
select pg_temp.expect((select next_daily_goal from app.profiles where id = auth.uid()) = 12000, 'setting tomorrow''s goal');
select pg_temp.expect_error($$update app.profiles set next_daily_goal = 50 where id = auth.uid()$$, 'goal below 1,000');
with u as (update app.profiles set next_daily_goal = 9000 where id = '00000000-0000-0000-0000-00000000000b' returning 1)
select pg_temp.expect((select count(*) from u) = 0, 'setting a friend''s goal');
reset role;

-- Nov 28: Postgres keeps today's goal whatever the app sends. The stranger has no saved day
-- yet, which is when a lower goal could otherwise have judged today.
create or replace function pg_temp.utc_today() returns date language sql as $$ select (now() at time zone 'utc')::date $$;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
update app.profiles set daily_goal = 3000 where id = auth.uid();
select pg_temp.expect((select daily_goal from app.profiles where id = auth.uid()) = 8000, 'goal: writing daily_goal keeps today''s goal');
select pg_temp.expect((select next_daily_goal = 3000 and next_goal_from = pg_temp.utc_today() + 1 from app.profiles where id = auth.uid()), 'goal: writing daily_goal becomes tomorrow''s goal');
update app.profiles set next_daily_goal = 2000, next_goal_from = pg_temp.utc_today() - 1 where id = auth.uid();
select pg_temp.expect((select next_goal_from > pg_temp.utc_today() - 1 from app.profiles where id = auth.uid()), 'goal: a change can''t start in the past');
select pg_temp.expect((select daily_goal = 8000 and next_daily_goal = 5000 and next_goal_from = pg_temp.utc_today() + 1 from app.set_daily_goal(5000, pg_temp.utc_today())), 'goal: set_daily_goal starts tomorrow');
select pg_temp.expect((select next_goal_from = pg_temp.utc_today() + 1 from app.set_daily_goal(4000, date '2000-01-01')), 'goal: a made-up date is ignored');
select pg_temp.expect((select daily_goal = 8000 and next_daily_goal = 4000 from app.sync_my_goal(pg_temp.utc_today())), 'goal: sync keeps today''s goal');
select pg_temp.expect_error($$select app.set_daily_goal(500, current_date)$$, 'goal: set_daily_goal below 1,000');
select pg_temp.expect_error($$insert into app.goal_schedule (user_id, effective_from, goal) values (auth.uid(), current_date, 1000)$$, 'goal: writing the schedule directly');
select pg_temp.expect_error($$delete from app.goal_schedule where user_id = auth.uid()$$, 'goal: deleting the schedule');
select pg_temp.expect_error($$select app.goal_on(auth.uid(), current_date)$$, 'goal: calling goal_on');
insert into app.daily_steps (user_id, day, steps) values (auth.uid(), pg_temp.utc_today(), 4500);
select pg_temp.expect((select goal from app.daily_steps where user_id = auth.uid() and day = pg_temp.utc_today()) = 8000, 'goal: today is judged on the goal it started with');
-- With today saved, even an old app's "from today" can only mean tomorrow.
update app.profiles set next_daily_goal = 6000, next_goal_from = pg_temp.utc_today() where id = auth.uid();
select pg_temp.expect((select next_goal_from = pg_temp.utc_today() + 1 from app.profiles where id = auth.uid()), 'goal: change after today''s first save starts tomorrow');
-- Picking today's goal again cancels the change.
select pg_temp.expect((select daily_goal = 8000 and next_daily_goal is null from app.set_daily_goal(8000, pg_temp.utc_today())), 'goal: picking today''s goal cancels');
select pg_temp.expect((select count(*) from app.goal_schedule where user_id = auth.uid()) = 1, 'goal: reads own schedule');
select pg_temp.expect((select count(*) from app.goal_schedule) = 1, 'goal: can''t read others'' schedules');
select app.set_daily_goal(6000, pg_temp.utc_today());
reset role;
-- Tomorrow arrives: the change takes over on the next sync, and today's row keeps 8,000.
update app.goal_schedule set effective_from = pg_temp.utc_today() where user_id = '00000000-0000-0000-0000-00000000000c' and goal = 6000;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect((select daily_goal = 6000 and next_daily_goal is null from app.sync_my_goal(pg_temp.utc_today())), 'goal: a started change becomes daily_goal');
select pg_temp.expect((select daily_goal from app.profiles where id = auth.uid()) = 6000, 'goal: sync saves daily_goal');
select pg_temp.expect((select goal from app.daily_steps where user_id = auth.uid() and day = pg_temp.utc_today()) = 8000, 'goal: a saved day keeps its goal');
reset role;
-- Put the stranger back as they were for the checks below.
delete from app.daily_steps where user_id = '00000000-0000-0000-0000-00000000000c';
delete from app.goal_schedule where user_id = '00000000-0000-0000-0000-00000000000c' and effective_from > date '2000-01-01';
update app.profiles set daily_goal = 8000, next_daily_goal = null, next_goal_from = null where id = '00000000-0000-0000-0000-00000000000c';

-- Paid QRuns: only StepQ admins can post one or set a price.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect_error($$insert into app.qruns (created_by, title, place_name, starts_at, price_inr) values (auth.uid(), 'Paid', 'X', now() + interval '1 day', 299)$$, 'member posting a paid QRun');
select pg_temp.expect_error($$update app.qruns set price_inr = 99 where created_by = auth.uid()$$, 'member making own QRun paid');
select pg_temp.expect((select bool_and(price_inr = 0) from app.qruns), 'member QRuns are free');
select pg_temp.expect(not app.i_am_admin(), 'member is not an admin');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
insert into app.profiles (id, display_name, username) values (auth.uid(), 'StepQ', 'stepq');
select pg_temp.expect(app.i_am_admin(), 'admin is an admin');
insert into app.qruns (created_by, title, place_name, starts_at, price_inr) values (auth.uid(), 'Sunday 10K', 'Marine Drive', now() + interval '2 days', 499);
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select price_inr from app.qruns where title = 'Sunday 10K') = 499, 'admin''s paid QRun shows its price');
reset role;

-- Push tokens: a phone registers for its user; signing in as someone else moves it to them.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select app.register_push_token('fcm-token-phone-1-aaaaaaaaaaaa', 'android');
select app.register_push_token('fcm-token-phone-1-aaaaaaaaaaaa', 'android');
select pg_temp.expect((select count(*) from app.push_tokens) = 1, 'push: registers own token once');
select pg_temp.expect_error($$select app.register_push_token('short')$$, 'push: junk token');
select pg_temp.expect_error($$insert into app.push_tokens (token, user_id) values ('fcm-token-forged-bbbbbbbbbbbb', '00000000-0000-0000-0000-00000000000b')$$, 'push: inserting a token for someone else');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
select pg_temp.expect((select count(*) from app.push_tokens) = 0, 'push: cannot see others'' tokens');
select app.register_push_token('fcm-token-phone-1-aaaaaaaaaaaa', 'android');
select pg_temp.expect((select user_id from app.push_tokens) = auth.uid(), 'push: token moves to whoever signs in');
delete from app.push_tokens where token = 'fcm-token-phone-1-aaaaaaaaaaaa';
select pg_temp.expect((select count(*) from app.push_tokens) = 0, 'push: log-out removes own token');
reset role;
select set_config('request.jwt.claims', '', false);
set role anon;
select pg_temp.expect_error($$select app.register_push_token('fcm-token-anon-cccccccccccccccc')$$, 'anon registering a token');
reset role;

-- Streak: share steps with a friend each day. Only friends get it; writes only via share_steps().
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect(app.share_steps(array['00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000c']::uuid[], 6240, current_date) = 1, 'share: only friends get it');
select pg_temp.expect(app.share_steps(array['00000000-0000-0000-0000-00000000000b']::uuid[], 7000, current_date) = 1, 'share: again the same day');
select pg_temp.expect((select count(*) from app.step_shares) = 1 and (select steps from app.step_shares) = 7000, 'share: same day updates the number');
select pg_temp.expect((select streak from app.share_streak(current_date)) = 1 and (select shared_today from app.share_streak(current_date)), 'streak: 1 after sharing today');
select pg_temp.expect_error($$select app.share_steps(array['00000000-0000-0000-0000-00000000000b']::uuid[], 100, current_date - 5)$$, 'share: an old day');
select pg_temp.expect_error($$insert into app.step_shares (from_user, to_user, day, steps) values (auth.uid(), '00000000-0000-0000-0000-00000000000b', current_date - 3, 1)$$, 'share: writing the table directly');
reset role;
insert into app.step_shares (from_user, to_user, day, steps) values
  ('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', current_date - 1, 5000),
  ('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', current_date - 2, 5000),
  ('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', current_date - 4, 5000);
update app.step_shares set shared_at = now() - (current_date - day) * interval '1 day' where day < current_date;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select streak from app.share_streak(current_date)) = 3, 'streak: counts days in a row, stops at a gap');
select pg_temp.expect((select streak from app.share_streak(current_date + 1)) = 3 and not (select shared_today from app.share_streak(current_date + 1)), 'streak: kept until the day ends');
select pg_temp.expect((select streak from app.share_streak(current_date + 2)) = 0, 'streak: a missed day resets it');
select pg_temp.expect((select streak from app.friend_streaks(current_date) where friend_id = '00000000-0000-0000-0000-00000000000b') = 3, 'friend streak: counts days in a row with that friend');
select pg_temp.expect((select shared_today from app.friend_streaks(current_date) where friend_id = '00000000-0000-0000-0000-00000000000b'), 'friend streak: shared with them today');
select pg_temp.expect((select streak from app.friend_streaks(current_date + 1)) = 3 and not (select shared_today from app.friend_streaks(current_date + 1)), 'friend streak: kept until the day ends');
select pg_temp.expect((select streak from app.friend_streaks(current_date + 2)) = 0, 'friend streak: a missed day resets it');
select pg_temp.expect((select count(*) from app.friend_streaks(current_date) where friend_id = '00000000-0000-0000-0000-00000000000c') = 0, 'friend streak: none with a non-friend');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
select pg_temp.expect((select from_name from app.step_share_inbox(now() - interval '1 hour')) = 'Riya' and (select steps from app.step_share_inbox(now() - interval '1 hour')) = 7000, 'share: friend gets it in the inbox');
select pg_temp.expect((select count(*) from app.step_share_inbox(now() - interval '1 hour')) = 1, 'share: inbox only has new ones');
select pg_temp.expect((select streak from app.share_streak(current_date)) = 0, 'streak: receiving doesn''t count');
select pg_temp.expect((select count(*) from app.friend_streaks(current_date)) = 0, 'friend streak: receiving doesn''t count');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect((select count(*) from app.step_shares) = 0, 'share: others can''t read it');
reset role;

-- StepQ score: +1 per day the goal (as it was that day) was reached.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select coalesce(sum(score), 0) from app.stepq_scores(array[auth.uid()])) = 0, 'score: 0 before reaching a goal');
insert into app.daily_steps (user_id, day, steps) values (auth.uid(), current_date - 1, 9000);
select pg_temp.expect((select score from app.stepq_scores(array[auth.uid()])) = 1, 'score: +1 for a day at goal');
-- Tomorrow's goal is 12,000 (set above), so 10,000 tomorrow doesn't count.
insert into app.daily_steps (user_id, day, steps) values (auth.uid(), current_date + 1, 10000);
select pg_temp.expect((select goal from app.daily_steps where user_id = auth.uid() and day = current_date + 1) = 12000, 'score: day keeps the goal it started with');
select pg_temp.expect((select score from app.stepq_scores(array[auth.uid()])) = 1, 'score: below that day''s goal');
update app.daily_steps set steps = 8500 where user_id = auth.uid() and day = current_date;
select pg_temp.expect((select score from app.stepq_scores(array[auth.uid()])) = 2, 'score: today counts once reached');
select pg_temp.expect_error($$update app.daily_steps set goal = 1000 where user_id = auth.uid() and day = current_date - 1$$, 'score: changing a day''s goal');
update app.daily_steps set steps = 4000 where user_id = auth.uid() and day = current_date - 1;
select pg_temp.expect((select goal from app.daily_steps where user_id = auth.uid() and day = current_date - 1) = 8000, 'score: re-saving a day keeps its goal');
update app.daily_steps set steps = 9000 where user_id = auth.uid() and day = current_date - 1;
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
select pg_temp.expect((select score from app.stepq_scores(array['00000000-0000-0000-0000-00000000000a']::uuid[])) = 2, 'score: friends see it');
reset role;

-- These community checks are about the feed itself, so plan limits are lifted here; the
-- Pro section below puts the defaults back (Free can't post, new posts need approval).
update app.plan_features set free_value = null where key = 'community_posts';
update app.plan_features set free_value = 0 where key = 'post_approval';

-- Community: everyone signed in sees posts with the author's public fields; you can only
-- post, like and repost as yourself; achievements take steps from your own daily_steps row.
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select set_config('test.post', app.create_post('Morning walk done!')::text, false);
select set_config('test.win', app.create_post('Goal!', null, current_date)::text, false);
select pg_temp.expect((select steps from app.posts where id = current_setting('test.win')::uuid) = (select steps from app.daily_steps where user_id = auth.uid() and day = current_date), 'community: achievement uses my real steps');
select pg_temp.expect_error($$select app.create_post('   ')$$, 'community: empty post');
select pg_temp.expect_error($$select app.create_post(repeat('x', 501))$$, 'community: post over 500 characters');
select pg_temp.expect_error($$select app.create_post('x', null, current_date - 5)$$, 'community: sharing an old day');
select pg_temp.expect_error($$insert into app.posts (author, body) values (auth.uid(), 'direct')$$, 'community: writing posts directly');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect((select count(*) from app.community_feed()) = 2, 'community: a stranger sees the feed');
select pg_temp.expect((select author_name from app.community_feed() limit 1) = 'Riya', 'community: with the author''s name');
select pg_temp.expect((select count(*) from app.community_feed('friends')) = 0, 'community: friends feed leaves out strangers');
insert into app.post_likes (post_id, user_id) values (current_setting('test.post')::uuid, auth.uid());
select pg_temp.expect_error($$insert into app.post_likes (post_id, user_id) values (current_setting('test.post')::uuid, '00000000-0000-0000-0000-00000000000b')$$, 'community: liking as someone else');
select set_config('test.reply', app.create_post('Nice one', current_setting('test.post')::uuid)::text, false);
select app.create_post('Thanks!', current_setting('test.reply')::uuid);
select pg_temp.expect_error($$select app.create_post('x', current_setting('test.post')::uuid, current_date)$$, 'community: achievement as a reply');
with d as (delete from app.posts where id = current_setting('test.post')::uuid returning 1)
select pg_temp.expect((select count(*) from d) = 0, 'community: deleting someone else''s post');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
insert into app.post_reposts (post_id, user_id) values (current_setting('test.post')::uuid, auth.uid());
select pg_temp.expect((select count(*) from app.community_feed('friends')) = 3, 'community: friends feed has Riya''s posts and my repost');
select pg_temp.expect((select reposted_by from app.community_feed() limit 1) = 'Arjun', 'community: repost shows who reposted');
select pg_temp.expect((select count(*) from app.post_likes) = 0, 'community: can''t see who else liked');
select pg_temp.expect((select like_count from app.post_thread(current_setting('test.post')::uuid) where depth = 0) = 1, 'community: like count');
select pg_temp.expect((select reply_count from app.post_thread(current_setting('test.post')::uuid) where depth = 0) = 1, 'community: reply count');
select pg_temp.expect((select count(*) from app.post_thread(current_setting('test.reply')::uuid)) = 3, 'community: thread has parent, post and reply');
select pg_temp.expect((select parent_username from app.post_thread(current_setting('test.reply')::uuid) where depth = 0) = 'riya.walks', 'community: reply shows who it answers');
reset role;
-- Three reports hide a post from everyone but its author.
insert into auth.users values ('00000000-0000-0000-0000-00000000000e', 'meera@test');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000e');
insert into app.profiles (id, display_name, username) values (auth.uid(), 'Meera', 'meera');
select set_config('test.spam', app.create_post('Buy followers')::text, false);
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
insert into app.post_reports (post_id, reporter, reason) values (current_setting('test.spam')::uuid, auth.uid(), 'spam');
select pg_temp.expect_error($$insert into app.post_reports (post_id, reporter) values (current_setting('test.spam')::uuid, '00000000-0000-0000-0000-00000000000b')$$, 'community: reporting as someone else');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
insert into app.post_reports (post_id, reporter) values (current_setting('test.spam')::uuid, auth.uid());
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
insert into app.post_reports (post_id, reporter) values (current_setting('test.spam')::uuid, auth.uid());
select pg_temp.expect(not exists (select 1 from app.community_feed() where id = current_setting('test.spam')::uuid), 'community: reported post hidden');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000e');
select pg_temp.expect(exists (select 1 from app.community_feed() where id = current_setting('test.spam')::uuid), 'community: author still sees it');
delete from app.posts where id = current_setting('test.spam')::uuid;
select pg_temp.expect(not exists (select 1 from app.posts where id = current_setting('test.spam')::uuid), 'community: deleting own post');
reset role;
insert into stepq.admins values ('00000000-0000-0000-0000-0000000000ad', 'admin@test') on conflict do nothing;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
select pg_temp.expect((select count(*) from app.post_likes) = 1, 'admin: reads all likes');
select pg_temp.expect((select count(*) from app.post_reports) = 0, 'admin: reports went with the deleted post');
reset role;

-- Compliance: 13+ for new accounts; 13 to 17 need a parent's consent before doing anything.
insert into auth.users values ('00000000-0000-0000-0000-00000000000f', 'teen@test') on conflict do nothing;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000f');
select pg_temp.expect_error($$insert into app.profiles (id, display_name, username, birthdate) values (auth.uid(), 'Kid', 'kid', current_date - interval '12 years')$$, 'compliance: under-13 sign-up');
insert into app.profiles (id, display_name, username, birthdate) values (auth.uid(), 'Teen', 'teen', current_date - interval '15 years');
select pg_temp.expect(app.my_parental_consent() = 'none', 'parental: teen needs a parent');
select pg_temp.expect_error($$insert into app.daily_steps (user_id, day, steps) values (auth.uid(), current_date, 100)$$, 'parental: teen saving steps before consent');
select pg_temp.expect_error($$select app.create_post('hi')$$, 'parental: teen posting before consent');
select pg_temp.expect_error($$select app.record_consent('parental', 'x')$$, 'parental: teen consenting for their parent');
select pg_temp.expect_error($$insert into app.parental_consents (child_id, parent_name, parent_email, token_hash, status) values (auth.uid(), 'Me', 'me@test', 'x', 'granted')$$, 'parental: teen writing their own consent');
reset role;
-- What the stepq.in server does when the parent says yes.
insert into app.parental_consents (child_id, parent_name, parent_email, token_hash, status) values ('00000000-0000-0000-0000-00000000000f', 'Parent', 'parent@test', 'hash', 'granted');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000f');
select pg_temp.expect(app.my_parental_consent() = 'granted', 'parental: status visible to the teen');
insert into app.daily_steps (user_id, day, steps) values (auth.uid(), current_date, 100);
select pg_temp.expect((select count(*) from app.daily_steps where user_id = auth.uid()) = 1, 'parental: teen can walk once a parent agrees');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
select pg_temp.expect((select count(*) from app.parental_consents) = 0, 'parental: others cannot read it');
select pg_temp.expect(app.my_parental_consent() = 'not_needed', 'parental: adults need nothing');
reset role;

select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select app.record_consent('terms_privacy', '2026-11-24');
select pg_temp.expect((select count(*) from app.consents where withdrawn_at is null) = 1, 'consent: recorded and readable by me');
select pg_temp.expect_error($$insert into app.consents (user_id, purpose, version) values (auth.uid(), 'health', 'x')$$, 'consent: writing the log directly');
select pg_temp.expect_error($$select app.record_consent('ads', 'x')$$, 'consent: unknown purpose');
select app.record_consent('community', 'v1');
select app.record_consent('community', 'v1', false);
select pg_temp.expect((select withdrawn_at is not null from app.consents where purpose = 'community'), 'consent: withdrawn');
select pg_temp.expect((app.export_my_data() -> 'profile' ->> 'username') = 'riya.walks', 'export: my profile');
select pg_temp.expect(jsonb_array_length(app.export_my_data() -> 'posts') >= 1, 'export: my posts');
select pg_temp.expect(jsonb_array_length(app.export_my_data() -> 'consents') = 2, 'export: my consents');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
select pg_temp.expect((select count(*) from app.consents) = 0, 'consent: others cannot read mine');
select pg_temp.expect(jsonb_array_length(app.export_my_data() -> 'posts') = 0, 'export: only my own rows');
reset role;

select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
select pg_temp.expect(exists (select 1 from app.community_feed() where id = current_setting('test.post')::uuid), 'block: post visible before blocking');
insert into app.user_blocks (blocker, blocked) values (auth.uid(), '00000000-0000-0000-0000-00000000000a');
select pg_temp.expect(not exists (select 1 from app.community_feed() where id = current_setting('test.post')::uuid), 'block: blocked author hidden from me');
select pg_temp.expect((select count(*) from app.my_blocks()) = 1, 'block: listed in my blocks');
select pg_temp.expect_error($$insert into app.user_blocks (blocker, blocked) values ('00000000-0000-0000-0000-00000000000b', auth.uid())$$, 'block: blocking on someone else''s behalf');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select count(*) from app.user_blocks) = 0, 'block: the blocked person cannot see the block');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
delete from app.user_blocks where blocked = '00000000-0000-0000-0000-00000000000a';
select pg_temp.expect(exists (select 1 from app.community_feed() where id = current_setting('test.post')::uuid), 'block: unblocking shows posts again');
reset role;

select pg_temp.as_user('00000000-0000-0000-0000-00000000000b');
delete from app.posts where id = current_setting('test.post')::uuid;
select pg_temp.expect(exists (select 1 from app.posts where id = current_setting('test.post')::uuid), 'moderation: others cannot delete my post');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
-- Removes Riya's achievement post; test.post stays for the Pro checks further down.
delete from app.posts where id = current_setting('test.win')::uuid;
select pg_temp.expect(not exists (select 1 from app.posts where id = current_setting('test.win')::uuid), 'moderation: admin removes a post');
reset role;

-- StepQ Pro: plans, limits from app.plan_features, admin-only changes.
insert into auth.users values
  ('00000000-0000-0000-0000-0000000000f1', 'free@test'),
  ('00000000-0000-0000-0000-0000000000f2', 'pal@test');
update app.plan_features set free_value = 0 where key = 'community_posts';
update app.plan_features set free_value = 1 where key = 'post_approval';
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f2');
insert into app.profiles (id, display_name, username) values (auth.uid(), 'Pal', 'pal');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
insert into app.profiles (id, display_name, username) values (auth.uid(), 'Freya', 'freya');
insert into app.friendships (requester, addressee) values (auth.uid(), '00000000-0000-0000-0000-0000000000f2');
select pg_temp.expect((app.my_plan() ->> 'plan') = 'free', 'pro: new members are free');
select pg_temp.expect((select (f ->> 'mine')::int from jsonb_array_elements(app.my_plan() -> 'features') f where f ->> 'key' = 'community_posts') = 0, 'pro: my plan lists my limits');
select pg_temp.expect((select count(*) from app.plan_features) >= 13, 'pro: members read the feature list');
select pg_temp.expect_error($$update app.plan_features set free_value = 99 where key = 'community_posts'$$, 'pro: member editing a limit');
select pg_temp.expect_error($$select app.admin_set_feature('community_posts', 99, null)$$, 'pro: member calling the admin limit editor');
select pg_temp.expect_error($$select app.admin_set_member(auth.uid(), 'pro')$$, 'pro: member making themselves pro');
select pg_temp.expect_error($$update app.profiles set qrun_host = true where id = auth.uid()$$, 'pro: member making themselves a QRun host');
select pg_temp.expect_error($$update app.profiles set theme = 'gold' where id = auth.uid()$$, 'pro: free member changing theme');
select pg_temp.expect_error($$update app.profiles set photo_path = auth.uid()::text || '/me.jpg' where id = auth.uid()$$, 'pro: free member setting a photo');
select pg_temp.expect_error($$select app.request_qrun_host()$$, 'pro: free member asking to host');
select pg_temp.expect_error($$select app.create_post('one')$$, 'pro: free member starting a post');
select pg_temp.expect_error($$select app.create_post('', null, current_date)$$, 'pro: free member sharing steps as a post');
select pg_temp.expect(app.create_post('a reply is fine', current_setting('test.post')::uuid) is not null, 'pro: free member replies');
select pg_temp.expect((select status from app.posts where body = 'a reply is fine') = 'approved', 'pro: replies don''t wait for approval');
select pg_temp.expect(((app.my_plan() -> 'usage') ->> 'community_posts')::int = 0, 'pro: replies don''t count as posts');
reset role;
update app.friendships set status = 'accepted' where requester = '00000000-0000-0000-0000-0000000000f1';
-- Freya shared with Pal on each of the 3 days before yesterday, then missed yesterday.
insert into app.step_shares (from_user, to_user, day, steps)
  select '00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-0000000000f2', current_date - d, 5000 from generate_series(2, 4) d;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
select pg_temp.expect((select streak from app.lost_streaks(current_date)) = 3, 'restore: lost streak listed with its length');
select pg_temp.expect(coalesce((select streak from app.friend_streaks(current_date)), 0) = 0, 'restore: streak is broken before restoring');
select pg_temp.expect(app.restore_streak('00000000-0000-0000-0000-0000000000f2', current_date) = 1, 'restore: one of two left');
select pg_temp.expect((select streak from app.friend_streaks(current_date)) = 4, 'restore: streak is back, with the restored day');
select pg_temp.expect((select count(*) from app.lost_streaks(current_date)) = 0, 'restore: nothing left to restore');
select pg_temp.expect_error($$select app.restore_streak('00000000-0000-0000-0000-0000000000f2', current_date)$$, 'restore: same streak twice');
select pg_temp.expect_error($$insert into app.streak_restores (user_id, friend_id, day) values (auth.uid(), '00000000-0000-0000-0000-0000000000f2', current_date - 5)$$, 'restore: writing the table directly');
update app.profiles set bff_id = null where id = auth.uid();
select pg_temp.expect_error($$update app.profiles set bff_id = '00000000-0000-0000-0000-0000000000f2' where id = auth.uid()$$, 'bff: free member pinning');
select pg_temp.expect((select count(*) from app.step_history(5000)) <= 365, 'insights: free history is capped at a year');
reset role;
-- Free gets 2 restores a month: with 2 used, the next one is refused.
delete from app.streak_restores where user_id = '00000000-0000-0000-0000-0000000000f1';
insert into app.streak_restores (user_id, friend_id, day) values
  ('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-0000000000f2', current_date - 30),
  ('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-0000000000f2', current_date - 31);
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
select pg_temp.expect_error($$select app.restore_streak('00000000-0000-0000-0000-0000000000f2', current_date)$$, 'restore: over the monthly limit');
reset role;

-- The admin edits limits and makes Freya Pro; everything is logged.
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
select app.admin_set_feature('community_posts', 5, null);
select pg_temp.expect((select free_value from app.plan_features where key = 'community_posts') = 5, 'admin: changes a limit');
select pg_temp.expect_error($$select app.admin_set_feature('change_theme', 2, 1)$$, 'admin: a switch is 0 or 1');
select pg_temp.expect_error($$select app.admin_set_feature('community_posts', -1, null)$$, 'admin: no negative limits');
select app.admin_set_member('00000000-0000-0000-0000-0000000000f1', 'pro');
select pg_temp.expect((select count(*) from app.admin_log) = 2, 'admin: changes are logged');
select pg_temp.expect(app.admin_post_qrun('StepQ Sunday walk', 'Juhu Beach', now() + interval '3 days') is not null, 'admin: posts a QRun from the dashboard');
select pg_temp.expect((select official from app.qruns where title = 'StepQ Sunday walk'), 'admin: dashboard QRuns are official');
select pg_temp.expect_error($$select app.admin_post_qrun('Old', 'X', now() - interval '1 day')$$, 'admin: QRun in the past');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
select pg_temp.expect((app.my_plan() ->> 'is_pro')::boolean, 'pro: Freya is pro');
select set_config('test.live', app.create_post('pro posts go live')::text, false);
select pg_temp.expect((select status from app.posts where id = current_setting('test.live')::uuid) = 'approved', 'approval: off for Pro by default');
reset role;
-- An admin can still switch approval on for Pro; the rest checks that path.
update app.plan_features set pro_value = 1 where key = 'post_approval';
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
select set_config('test.pending', app.create_post('four')::text, false);
select app.create_post('five, unlimited on pro');
select pg_temp.expect((select status from app.posts where id = current_setting('test.pending')::uuid) = 'pending', 'approval: a Pro post waits for an admin');
select pg_temp.expect((select count(*) from app.my_pending_posts()) = 2, 'approval: I see my waiting posts');
select pg_temp.expect((select count(*) from app.community_feed('mine') where body = 'four') = 1, 'approval: my waiting post shows in my feed');
select pg_temp.expect_error($$update app.posts set status = 'approved' where author = auth.uid()$$, 'approval: approving my own post');
select pg_temp.expect_error($$select app.admin_review_post(current_setting('test.pending')::uuid, true)$$, 'approval: member calling the admin review');
update app.profiles set theme = 'gold', photo_path = auth.uid()::text || '/me.jpg', bff_id = '00000000-0000-0000-0000-0000000000f2' where id = auth.uid();
select pg_temp.expect((select theme from app.profiles where id = auth.uid()) = 'gold', 'pro: changes theme');
select pg_temp.expect_error($$update app.profiles set photo_path = '00000000-0000-0000-0000-0000000000f2/x.jpg' where id = auth.uid()$$, 'pro: photo path in someone else''s folder');
select pg_temp.expect_error($$update app.profiles set bff_id = '00000000-0000-0000-0000-00000000000c' where id = auth.uid()$$, 'pro: pinning a non-friend');
select pg_temp.expect_error($$insert into app.qruns (created_by, title, place_name, starts_at) values (auth.uid(), 'Mine', 'X', now() + interval '1 day')$$, 'pro: posting a QRun before an admin approves');
select app.request_qrun_host();
select pg_temp.expect((select qrun_host_requested_at is not null from app.profiles where id = auth.uid()), 'pro: asks to host QRuns');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f2');
select pg_temp.expect((select count(*) from app.community_feed() where body = 'four') = 0, 'approval: others don''t see a waiting post');
select pg_temp.expect((select count(*) from app.posts where body = 'four') = 0, 'approval: nor read it directly');
select pg_temp.expect_error($$insert into app.post_likes (post_id, user_id) values (current_setting('test.pending')::uuid, auth.uid())$$, 'approval: liking a waiting post');
select pg_temp.expect_error($$select app.create_post('reply', current_setting('test.pending')::uuid)$$, 'approval: replying to a waiting post');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
select pg_temp.expect((select count(*) from app.posts where status = 'pending') = 2, 'approval: admins see the queue');
select app.admin_review_post(current_setting('test.pending')::uuid, true);
select app.admin_review_post((select id from app.posts where body = 'five, unlimited on pro'), false);
select pg_temp.expect_error($$select app.admin_review_post(current_setting('test.pending')::uuid, true)$$, 'approval: reviewing twice');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f2');
select pg_temp.expect((select count(*) from app.community_feed() where body = 'four') = 1, 'approval: approved post is in the feed');
select pg_temp.expect((select count(*) from app.community_feed() where body like 'five%') = 0, 'approval: rejected post stays hidden');
select pg_temp.expect((select pro from app.people_badges(array['00000000-0000-0000-0000-0000000000f1'::uuid])), 'badge: friends see the Pro badge');
select pg_temp.expect((select photo_path from app.people_badges(array['00000000-0000-0000-0000-0000000000f1'::uuid])) is not null, 'badge: friends see the photo');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-00000000000a');
select pg_temp.expect((select photo_path from app.people_badges(array['00000000-0000-0000-0000-0000000000f1'::uuid])) is null, 'badge: strangers don''t see the photo');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
select app.admin_set_member('00000000-0000-0000-0000-0000000000f1', 'pro', true);
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
insert into app.qruns (created_by, title, place_name, starts_at) values (auth.uid(), 'Freya''s walk', 'Bandra Fort', now() + interval '1 day');
select pg_temp.expect(not (select official from app.qruns where title = 'Freya''s walk'), 'pro: approved host posts a QRun');
reset role;
insert into app.challenges (title, starts_at, ends_at, pro_only) values ('Pro 100K', now() - interval '1 day', now() + interval '20 days', true);
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f2');
select pg_temp.expect_error($$insert into app.challenge_entries (challenge_id, user_id) select id, auth.uid() from app.challenges where title = 'Pro 100K'$$, 'challenge: free member joining a Pro-only challenge');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
insert into app.challenge_entries (challenge_id, user_id) select id, auth.uid() from app.challenges where title = 'Pro 100K';
select pg_temp.expect(app.reward_eligible(auth.uid(), 'monthly'), 'reward: pro is eligible monthly');
reset role;
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f2');
select pg_temp.expect(not app.reward_eligible(auth.uid(), 'monthly') and app.reward_eligible(auth.uid(), 'weekly'), 'reward: free is weekly only');
reset role;
-- Back to Free: theme, photo, BFF and hosting go; data stays.
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
select app.admin_set_member('00000000-0000-0000-0000-0000000000f1', 'free');
reset role;
select pg_temp.expect((select theme = 'leaf' and photo_path is null and bff_id is null and not qrun_host and pro_since is null from app.profiles where id = '00000000-0000-0000-0000-0000000000f1'), 'admin: downgrade resets Pro-only settings');
select set_config('request.jwt.claims', '', false);
set role anon;
select pg_temp.expect_error($$select app.my_plan()$$, 'anon reading a plan');
select pg_temp.expect_error($$select * from app.plan_features$$, 'anon reading the feature list');
reset role;

-- Launch audit: contact matching is rate limited; the export covers Pro data.
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
do $$ begin for i in 1..10 loop perform * from app.match_contacts(array['00']); end loop; end $$;
select pg_temp.expect_error($$select * from app.match_contacts(array['00'])$$, 'contacts: 11th lookup in a day');
select pg_temp.expect_error($$select * from app.contact_lookups$$, 'contacts: lookups log is private');
select pg_temp.expect((select app.export_my_data() ? 'plan' and app.export_my_data() ? 'streak_restores'
                         and (app.export_my_data() ->> 'contact_lookups')::int = 10), 'export: includes Pro data and lookups');
reset role;

-- iOS: the community word filter and App Store subscriptions.
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f2');
select pg_temp.expect_error($$select app.create_post('you are a CHUTIYA')$$, 'filter: blocked word in a post');
select pg_temp.expect_error($$select app.create_post('what the fuck, 10k steps')$$, 'filter: blocked word in English');
select pg_temp.expect(app.text_is_allowed('Classic walk past the abc mall, basic class, Scunthorpe'), 'filter: matches whole words only');
select pg_temp.expect(app.text_is_allowed('Morning walk in the park, 8k steps'), 'filter: normal text passes');
select pg_temp.expect((select count(*) from app.blocked_terms) = 0, 'filter: members cannot read the word list');
select pg_temp.expect_error($$insert into app.blocked_terms (term) values ('walk')$$, 'filter: members cannot add words');
select pg_temp.expect_error($$insert into app.store_subscriptions (original_transaction_id, user_id, product_id, environment, expires_at)
  values ('t1', auth.uid(), 'in.stepq.app.pro.monthly', 'Sandbox', now() + interval '30 days')$$, 'store: members cannot write subscriptions');
select pg_temp.expect_error($$select app.apply_store_subscription(auth.uid(), 't1', 'in.stepq.app.pro.monthly', 'Sandbox', now() + interval '30 days')$$,
  'store: members cannot grant themselves Pro');
select pg_temp.expect_error($$update app.profiles set pro_source = 'app_store' where id = auth.uid()$$, 'store: members cannot set pro_source');
reset role;

set role service_role;
select pg_temp.expect(app.apply_store_subscription('00000000-0000-0000-0000-0000000000f2', 'ios-t1', 'in.stepq.app.pro.monthly',
  'Sandbox', now() + interval '30 days') = 'pro', 'store: verified purchase makes the member Pro');
select pg_temp.expect_error($$select app.apply_store_subscription('00000000-0000-0000-0000-0000000000f1', 'ios-t1', 'in.stepq.app.pro.monthly',
  'Sandbox', now() + interval '30 days')$$, 'store: one subscription cannot serve two accounts');
-- An older notification arriving late does not shorten the subscription.
select pg_temp.expect(app.apply_store_subscription('00000000-0000-0000-0000-0000000000f2', 'ios-t1', 'in.stepq.app.pro.monthly',
  'Sandbox', now() - interval '1 day') = 'pro', 'store: late notification keeps Pro');
reset role;

select pg_temp.as_user('00000000-0000-0000-0000-0000000000f2');
select pg_temp.expect((select count(*) from app.store_subscriptions) = 1, 'store: member reads their own subscription');
select pg_temp.expect((select pro_source from app.profiles where id = auth.uid()) = 'app_store', 'store: Pro marked as from the App Store');
select pg_temp.as_user('00000000-0000-0000-0000-0000000000f1');
select pg_temp.expect((select count(*) from app.store_subscriptions) = 0, 'store: others cannot read it');
reset role;

set role service_role;
select pg_temp.expect(app.apply_store_subscription('00000000-0000-0000-0000-0000000000f2', 'ios-t1', 'in.stepq.app.pro.monthly',
  'Sandbox', now() + interval '30 days', now()) = 'free', 'store: refund or revoke ends Pro');
reset role;
-- Pro an admin gave stays when an App Store subscription lapses.
select pg_temp.as_user('00000000-0000-0000-0000-0000000000ad');
select app.admin_set_member('00000000-0000-0000-0000-0000000000f2', 'pro');
select pg_temp.expect((select count(*) from app.blocked_terms) > 10, 'filter: admins read the word list');
insert into app.blocked_terms (term) values ('testbadword');
delete from app.blocked_terms where term = 'testbadword';
reset role;
select pg_temp.expect((select pro_source from app.profiles where id = '00000000-0000-0000-0000-0000000000f2') = 'admin', 'store: admin Pro marked as admin');
set role service_role;
select pg_temp.expect(app.apply_store_subscription('00000000-0000-0000-0000-0000000000f2', 'ios-t1', 'in.stepq.app.pro.monthly',
  'Sandbox', now() - interval '1 day', now()) = 'pro', 'store: lapsed App Store subscription keeps admin Pro');
reset role;

-- Anonymous (no sign-in) gets nothing at all.
select set_config('request.jwt.claims', '', false);
set role anon;
select pg_temp.expect_error('select * from app.profiles', 'anon reading profiles');
select pg_temp.expect_error($$select app.username_available('x')$$, 'anon checking usernames');
select pg_temp.expect_error($$select * from app.search_people('ri')$$, 'anon searching people');
select pg_temp.expect_error($$select * from app.match_contacts(array['x'])$$, 'anon matching contacts');
select pg_temp.expect_error($$select app.share_steps(array[]::uuid[], 1, current_date)$$, 'anon sharing steps');
select pg_temp.expect_error($$select * from app.stepq_scores(array[]::uuid[])$$, 'anon reading scores');
select pg_temp.expect_error($$select * from app.community_feed()$$, 'anon reading the community');
select pg_temp.expect_error($$select * from app.posts$$, 'anon reading posts');
select pg_temp.expect_error($$select app.create_post('hi')$$, 'anon posting');
select pg_temp.expect_error($$select app.export_my_data()$$, 'anon exporting data');
select pg_temp.expect_error($$select app.record_consent('terms_privacy', 'x')$$, 'anon recording consent');
select pg_temp.expect_error($$select * from app.user_blocks$$, 'anon reading blocks');
select pg_temp.expect_error($$select * from app.match_contacts(array['00'])$$, 'anon matching contacts after audit');
select pg_temp.expect_error($$select * from app.store_subscriptions$$, 'anon reading subscriptions');
select pg_temp.expect_error($$select app.text_is_allowed('x')$$, 'anon using the word filter');
reset role;

\echo 'All RLS checks passed.'
