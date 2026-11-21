-- Nov 20 rule changes:
--   * Streak (Snapchat style): it grows on each day the user shares their step count with at
--     least one friend, and resets after a day without a share.
--   * StepQ score: +1 for every day the user reaches their daily goal.
--
-- Days are the phone's calendar days, passed in by the app, within a day of the server's date.
-- Additive and re-runnable; existing data is only backfilled (daily_steps.goal).

-- ---------------------------------------------------------------------------------------
-- Sharing steps with friends
-- ---------------------------------------------------------------------------------------
create table if not exists app.step_shares (
  id bigint generated always as identity primary key,
  from_user uuid not null references app.profiles (id) on delete cascade,
  to_user uuid not null references app.profiles (id) on delete cascade,
  day date not null,
  steps integer not null check (steps between 0 and 200000),
  shared_at timestamptz not null default now(),
  check (from_user <> to_user),
  unique (from_user, to_user, day)
);
create index if not exists step_shares_to_idx on app.step_shares (to_user, shared_at);

alter table app.step_shares enable row level security;
-- Sender and receiver can read a share. Writing goes only through app.share_steps().
drop policy if exists step_shares_read on app.step_shares;
create policy step_shares_read on app.step_shares for select to authenticated
  using (auth.uid() in (from_user, to_user));
grant select on app.step_shares to authenticated;

do $$
begin
  if to_regprocedure('stepq.is_admin()') is not null then
    drop policy if exists admins_read on app.step_shares;
    create policy admins_read on app.step_shares for select to authenticated using (stepq.is_admin());
  end if;
end $$;

-- Shares today's step count with friends. Only accepted friends get it; anyone else in the
-- list is skipped. Sharing again the same day updates the number. Returns how many got it.
create or replace function app.share_steps(friends uuid[], steps integer, p_day date)
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare n integer;
begin
  if auth.uid() is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  if p_day is null or p_day not between current_date - 1 and current_date + 1 then
    raise exception 'That day can''t be shared.' using errcode = '22023';
  end if;
  if steps is null or steps not between 0 and 200000 then
    raise exception 'Not a step count.' using errcode = '22023';
  end if;
  insert into app.step_shares (from_user, to_user, day, steps)
  select auth.uid(), f, p_day, steps
  from unnest(friends[1:200]) as f
  where app.are_friends(auth.uid(), f)
  on conflict (from_user, to_user, day) do update set steps = excluded.steps, shared_at = now();
  get diagnostics n = row_count;
  return n;
end $$;

-- My streak: days in a row with a share, counting back from today if I've shared today, or
-- from yesterday otherwise (so it doesn't drop to 0 every morning before the first share).
create or replace function app.share_streak(p_today date)
returns table (streak integer, shared_today boolean)
language sql
stable
security definer
set search_path = ''
as $$
  with days as (
    select distinct s.day from app.step_shares s where s.from_user = auth.uid() and s.day <= p_today
  ), start as (
    select case when exists (select 1 from days where day = p_today) then p_today else p_today - 1 end as d
  ), numbered as (
    select day, (select d from start) - day as back, row_number() over (order by day desc) - 1 as rn
    from days where day <= (select d from start)
  )
  select coalesce((select count(*)::integer from numbered where back = rn), 0),
         exists (select 1 from days where day = p_today)
  where auth.uid() is not null
$$;

-- New shares to me since the last check, for the phone's notification.
create or replace function app.step_share_inbox(since timestamptz)
returns table (id bigint, from_name text, steps integer, shared_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  select s.id, p.display_name, s.steps, s.shared_at
  from app.step_shares s
  join app.profiles p on p.id = s.from_user
  where s.to_user = auth.uid()
    and s.shared_at > greatest(since, now() - interval '2 days')
  order by s.shared_at desc
  limit 20
$$;

revoke all on function app.share_steps(uuid[], integer, date), app.share_streak(date),
  app.step_share_inbox(timestamptz) from public, anon;
grant execute on function app.share_steps(uuid[], integer, date), app.share_streak(date),
  app.step_share_inbox(timestamptz) to authenticated;

-- ---------------------------------------------------------------------------------------
-- StepQ score: one point per day the goal was reached
-- ---------------------------------------------------------------------------------------
-- Each day keeps the goal it started with, so changing the goal later doesn't rewrite the
-- past. Set on the day's first save (taking a pending goal change into account) and kept.
alter table app.daily_steps add column if not exists goal integer check (goal between 1000 and 100000);

create or replace function app.stamp_day_goal() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' and old.goal is not null then
    new.goal := old.goal;
  else
    select case when p.next_daily_goal is not null and p.next_goal_from <= new.day
                then p.next_daily_goal else p.daily_goal end
      into new.goal
      from app.profiles p where p.id = new.user_id;
  end if;
  return new;
end $$;

drop trigger if exists daily_steps_stamp_goal on app.daily_steps;
create trigger daily_steps_stamp_goal before insert or update on app.daily_steps
  for each row execute function app.stamp_day_goal();

-- Days saved before this migration get today's goal.
update app.daily_steps d set goal = p.daily_goal
  from app.profiles p where p.id = d.user_id and d.goal is null;

-- StepQ scores for people the caller can see (themselves, friends, group mates); RLS on
-- daily_steps does the filtering, so others are simply left out.
create or replace function app.stepq_scores(ids uuid[])
returns table (user_id uuid, score integer)
language sql
stable
set search_path = ''
as $$
  select d.user_id, count(*) filter (where d.steps >= d.goal)::integer
  from app.daily_steps d
  where d.user_id = any (ids[1:200])
  group by d.user_id
$$;

revoke all on function app.stepq_scores(uuid[]) from public, anon;
grant execute on function app.stepq_scores(uuid[]) to authenticated;
