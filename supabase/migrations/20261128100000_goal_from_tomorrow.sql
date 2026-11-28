-- Nov 28: a daily goal change always starts the next day, and Postgres enforces it.
--
-- The app already saved a change as next_daily_goal / next_goal_from (Nov 18), and each day
-- keeps the goal it started with in daily_steps.goal (Nov 20). Two gaps were left: the app's
-- API key can still write daily_goal itself, and next_goal_from could be set to today. Either
-- way, if today's steps hadn't been saved yet, today would be judged on the new (lower) goal.
--
-- Now:
--   * app.goal_schedule keeps every goal with the day it starts. A day's goal is the newest
--     entry on or before that day, so applying a change early can't reach back into today.
--     Only the functions below write it.
--   * A change starts the day after the phone's today, and never before the day after the
--     last day already saved. A made-up phone date can't pull it earlier than the day after
--     today in UTC-12 (see app.goal_change_floor).
--   * app.set_daily_goal(goal, today) is the app's way to change the goal. A direct write to
--     daily_goal or next_goal_* from the app (older app versions) is turned into the same
--     scheduled change by a trigger, so it starts tomorrow too.
--   * daily_steps.goal is stamped from the schedule.
--
-- Additive and safe to re-run. It reads existing profiles once to fill the new table and
-- doesn't change any existing row.

create table if not exists app.goal_schedule (
  user_id uuid not null references app.profiles (id) on delete cascade,
  effective_from date not null,
  goal integer not null check (goal between 1000 and 100000),
  created_at timestamptz not null default now(),
  primary key (user_id, effective_from)
);
alter table app.goal_schedule enable row level security;
revoke all on app.goal_schedule from public, anon, authenticated;
grant select on app.goal_schedule to authenticated;
drop policy if exists goal_schedule_read on app.goal_schedule;
create policy goal_schedule_read on app.goal_schedule for select to authenticated
  using (user_id = auth.uid());

-- Everyone's goal as it is now, from the start of time, plus any change already waiting.
insert into app.goal_schedule (user_id, effective_from, goal)
  select p.id, date '2000-01-01', p.daily_goal from app.profiles p
  on conflict do nothing;
insert into app.goal_schedule (user_id, effective_from, goal)
  select p.id, p.next_goal_from, p.next_daily_goal from app.profiles p
  where p.next_daily_goal is not null and p.next_goal_from is not null
  on conflict do nothing;

-- A new profile's first goal (set in onboarding) counts from the start.
create or replace function app.seed_goal_schedule() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  insert into app.goal_schedule (user_id, effective_from, goal)
  values (new.id, date '2000-01-01', new.daily_goal)
  on conflict do nothing;
  return new;
end $$;

drop trigger if exists profiles_seed_goal on app.profiles;
create trigger profiles_seed_goal after insert on app.profiles
  for each row execute function app.seed_goal_schedule();

-- The goal [p_user] had on [p_day].
create or replace function app.goal_on(p_user uuid, p_day date) returns integer
language sql stable security definer set search_path = ''
as $$
  select coalesce(
    (select g.goal from app.goal_schedule g
      where g.user_id = p_user and g.effective_from <= p_day
      order by g.effective_from desc limit 1),
    (select p.daily_goal from app.profiles p where p.id = p_user))
$$;

-- The first day a change made now can start on: never before the day after the last saved
-- day. [p_today] is the phone's date and only counts when it is within a day of UTC; it then
-- gives exactly tomorrow on the phone, and can't pull a change earlier than the day after
-- today in UTC-12, the earliest calendar on Earth. Without a phone date, the day after today
-- in UTC.
create or replace function app.goal_change_floor(p_user uuid, p_today date) returns date
language sql stable security definer set search_path = ''
as $$
  with utc as (select (now() at time zone 'utc') as t)
  select greatest(
    (select max(d.day) + 1 from app.daily_steps d where d.user_id = p_user),
    case when p_today between utc.t::date - 1 and utc.t::date + 1
         then greatest(p_today, (utc.t - interval '12 hours')::date) + 1
         else utc.t::date + 1 end)
  from utc
$$;

revoke all on function app.goal_on(uuid, date), app.goal_change_floor(uuid, date)
  from public, anon, authenticated;

-- Schedules my goal change (null [p_goal]: keep today's goal and drop any waiting change) and
-- returns what profiles should show: today's goal and the change waiting, if any. It doesn't
-- write app.profiles, so the profiles trigger below can use it too.
create or replace function app.plan_my_goal(p_goal integer, p_today date, p_change boolean default true)
returns table (daily_goal integer, next_daily_goal integer, next_goal_from date)
language plpgsql security definer set search_path = ''
as $$
declare
  me uuid := auth.uid();
  f date;
begin
  if me is null then
    raise exception 'Log in first.' using errcode = '42501';
  end if;
  if p_goal is not null and p_goal not between 1000 and 100000 then
    raise exception 'Pick a goal between 1,000 and 100,000 steps.' using errcode = '23514';
  end if;
  f := app.goal_change_floor(me, p_today);
  if p_change then
    delete from app.goal_schedule g where g.user_id = me and g.effective_from >= f;
    if p_goal is not null and p_goal is distinct from app.goal_on(me, f - 1) then
      insert into app.goal_schedule (user_id, effective_from, goal) values (me, f, p_goal);
    end if;
  end if;
  return query
    select app.goal_on(me, f - 1),
           (select g.goal from app.goal_schedule g where g.user_id = me and g.effective_from >= f
             order by g.effective_from limit 1),
           (select g.effective_from from app.goal_schedule g where g.user_id = me and g.effective_from >= f
             order by g.effective_from limit 1);
end $$;

-- The app calls this to change the daily goal. [p_today] is the phone's date. Picking today's
-- goal again cancels a waiting change. Returns today's goal and the change waiting, if any.
create or replace function app.set_daily_goal(p_goal integer, p_today date)
returns table (daily_goal integer, next_daily_goal integer, next_goal_from date)
language plpgsql security definer set search_path = ''
as $$
declare r record;
begin
  if p_goal is null then
    raise exception 'Pick a goal between 1,000 and 100,000 steps.' using errcode = '23514';
  end if;
  select * into r from app.plan_my_goal(p_goal, p_today);
  update app.profiles p
     set daily_goal = r.daily_goal, next_daily_goal = r.next_daily_goal, next_goal_from = r.next_goal_from
   where p.id = auth.uid();
  return query select r.daily_goal, r.next_daily_goal, r.next_goal_from;
end $$;

-- The app calls this on open: moves a change that has started into daily_goal. Changes nothing
-- in the schedule.
create or replace function app.sync_my_goal(p_today date)
returns table (daily_goal integer, next_daily_goal integer, next_goal_from date)
language plpgsql security definer set search_path = ''
as $$
declare r record;
begin
  select * into r from app.plan_my_goal(null, p_today, false);
  update app.profiles p
     set daily_goal = r.daily_goal, next_daily_goal = r.next_daily_goal, next_goal_from = r.next_goal_from
   where p.id = auth.uid()
     and (p.daily_goal, p.next_daily_goal, p.next_goal_from)
         is distinct from (r.daily_goal, r.next_daily_goal, r.next_goal_from);
  return query select r.daily_goal, r.next_daily_goal, r.next_goal_from;
end $$;

revoke all on function app.plan_my_goal(integer, date, boolean), app.set_daily_goal(integer, date),
  app.sync_my_goal(date) from public, anon;
grant execute on function app.plan_my_goal(integer, date, boolean), app.set_daily_goal(integer, date),
  app.sync_my_goal(date) to authenticated;

-- Direct writes to the goal columns from the app (older versions) go through the schedule.
-- Writes from the functions above run as their owner, and the dashboard as postgres or
-- service_role, so only the app's own role is checked here.
create or replace function app.guard_goal_columns() returns trigger
language plpgsql set search_path = ''
as $$
declare r record;
begin
  if current_user <> 'authenticated' then
    return new;
  end if;
  if new.daily_goal is not distinct from old.daily_goal
     and new.next_daily_goal is not distinct from old.next_daily_goal
     and new.next_goal_from is not distinct from old.next_goal_from then
    return new;
  end if;

  if new.daily_goal is distinct from old.daily_goal and new.daily_goal = old.next_daily_goal then
    -- An older app moving a change that has started into daily_goal: nothing new to schedule.
    select * into r from app.plan_my_goal(null, null, false);
  elsif new.next_daily_goal is distinct from old.next_daily_goal
        or new.next_goal_from is distinct from old.next_goal_from then
    -- An older app saving tomorrow's goal (or cancelling it). It sends the phone's tomorrow.
    select * into r from app.plan_my_goal(
      coalesce(new.next_daily_goal, old.daily_goal), new.next_goal_from - 1);
  else
    -- daily_goal written directly: it becomes a change from tomorrow.
    select * into r from app.plan_my_goal(new.daily_goal, null);
  end if;

  new.daily_goal := r.daily_goal;
  new.next_daily_goal := r.next_daily_goal;
  new.next_goal_from := r.next_goal_from;
  return new;
end $$;

drop trigger if exists profiles_guard_goal on app.profiles;
create trigger profiles_guard_goal before update on app.profiles
  for each row execute function app.guard_goal_columns();

-- Each day is judged on the goal it had, from the schedule. Kept once set.
create or replace function app.stamp_day_goal() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' and old.goal is not null then
    new.goal := old.goal;
  else
    new.goal := app.goal_on(new.user_id, new.day);
  end if;
  return new;
end $$;
