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
reset role;
insert into app.challenges (title, target_steps, starts_at, ends_at) values ('Walk 50k', 50000, now() - interval '1 day', now() + interval '6 days');
select pg_temp.as_user('00000000-0000-0000-0000-00000000000c');
insert into app.challenge_entries (challenge_id, user_id) select id, auth.uid() from app.challenges;
select pg_temp.expect((select joined from app.challenge_counts()) = 1, 'challenge count includes my entry');
select pg_temp.expect_error($$insert into app.challenge_entries (challenge_id, user_id) select id, '00000000-0000-0000-0000-00000000000a' from app.challenges$$, 'joining someone else to a challenge');
select pg_temp.expect_error($$insert into app.challenges (title, starts_at, ends_at) values ('x', now(), now() + interval '1 day')$$, 'creating a challenge');
reset role;

-- QRuns: anyone signed in sees the feed with the host's name, but can only post and join as themselves.
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
select pg_temp.expect((select count(*) from app.water_inbox(now() - interval '1 hour')) = 5, 'inbox: reminders sent to me');
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
select set_config('request.jwt.claims', '', false);
set role anon;
select pg_temp.expect_error($$select app.delete_my_account()$$, 'anon deleting an account');
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

-- Anonymous (no sign-in) gets nothing at all.
select set_config('request.jwt.claims', '', false);
set role anon;
select pg_temp.expect_error('select * from app.profiles', 'anon reading profiles');
select pg_temp.expect_error($$select app.username_available('x')$$, 'anon checking usernames');
select pg_temp.expect_error($$select * from app.search_people('ri')$$, 'anon searching people');
select pg_temp.expect_error($$select * from app.match_contacts(array['x'])$$, 'anon matching contacts');
reset role;

\echo 'All RLS checks passed.'
