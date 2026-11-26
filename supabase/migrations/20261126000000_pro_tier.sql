-- StepQ Pro: Free and Pro plans, with every limit editable from the stepq.in admin dashboard.
--
-- What changes for members:
--   * app.plan_features holds each feature's Free and Pro value (a switch: 0 or 1, or a limit:
--     a number, empty = unlimited). Admins edit it from the dashboard (app.admin_set_feature).
--     The app reads it to show what each plan gets; Postgres enforces it:
--       - profile photo, theme and BFF pin: a trigger on app.profiles;
--       - photos: storage policies on the `avatars` bucket;
--       - community posts a month: app.create_post (replies and likes are never limited);
--       - water reminders a day: the existing trigger, now reading the limit from here;
--       - streak restores a month: app.restore_streak;
--       - Pro-only challenges: a trigger on app.challenge_entries;
--       - step history in Insights: app.step_history.
--   * QRuns: only StepQ admins (from the dashboard, app.admin_post_qrun) and Pro members an admin
--     has made a QRun host (app.admin_set_member) can post one. Pro members can ask to host
--     (app.request_qrun_host); the request shows up in the dashboard.
--   * Nobody can make themselves Pro: `plan` has no write grant, and only app.admin_set_member
--     changes it. Every admin change is written to app.admin_log.
--
-- Profile photos are only shown to people who can already see the profile (me, friends and
-- group mates, app.can_see); strangers in the community feed still see the avatar.
--
-- Additive and re-runnable. It adds tables, columns (with defaults), functions and policies and
-- seeds the feature list once (existing rows are left as they are). No member data is changed.

-- ---------------------------------------------------------------------------------------
-- What each plan gets
-- ---------------------------------------------------------------------------------------
create table if not exists app.plan_features (
  key text primary key check (key ~ '^[a-z_]{2,40}$'),
  label text not null,
  -- switch: 0 = no, 1 = yes. limit: how many, null = unlimited.
  kind text not null check (kind in ('switch', 'limit')),
  free_value integer check (free_value >= 0),
  pro_value integer check (pro_value >= 0),
  -- For limits: what the number counts, e.g. 'a month'.
  unit text,
  sort integer not null default 100,
  updated_at timestamptz not null default now(),
  updated_by uuid,
  check (kind = 'limit' or (free_value in (0, 1) and pro_value in (0, 1)))
);

insert into app.plan_features (key, label, kind, free_value, pro_value, unit, sort) values
  ('upload_photo',      'Upload a profile photo',                 'switch', 0, 1,    null,          10),
  ('change_theme',      'Change the app theme',                   'switch', 0, 1,    null,          20),
  ('plan_qrun',         'Plan a QRun (once an admin approves)',   'switch', 0, 1,    null,          30),
  ('streak_restores',   'Streak restores',                        'limit',  2, 8,    'a month',     40),
  ('reward_weekly',     'Weekly reward eligible',                 'switch', 1, 1,    null,          50),
  ('reward_monthly',    'Monthly reward eligible',                'switch', 0, 1,    null,          60),
  ('reward_yearly',     'Yearly reward eligible',                 'switch', 0, 1,    null,          70),
  ('all_challenges',    'Join every challenge (incl. Pro-only)',  'switch', 0, 1,    null,          80),
  ('analysis_days',     'Step history in Insights',               'limit',  7, null, 'days',        90),
  ('water_reminders',   'Water reminders',                        'limit',  3, null, 'a day',      100),
  ('profile_badge',     'Pro badge on profile',                   'switch', 0, 1,    null,         110),
  ('pin_bff',           'Pin one BFF',                            'switch', 0, 1,    null,         120),
  ('community_posts',   'Community posts',                        'limit',  3, null, 'a month',    130)
on conflict (key) do nothing;

alter table app.plan_features enable row level security;
alter table app.plan_features force row level security;
grant select on app.plan_features to authenticated;
drop policy if exists plan_features_read on app.plan_features;
create policy plan_features_read on app.plan_features for select to authenticated using (true);

-- ---------------------------------------------------------------------------------------
-- New columns
-- ---------------------------------------------------------------------------------------
alter table app.profiles add column if not exists theme text not null default 'leaf';
do $$ begin
  alter table app.profiles add constraint profiles_theme_check
    check (theme in ('leaf', 'ocean', 'grape', 'sunset', 'gold'));
exception when duplicate_object then null; end $$;
-- Path of the photo in the `avatars` bucket: "<my id>/<file>".
alter table app.profiles add column if not exists photo_path text;
do $$ begin
  alter table app.profiles add constraint profiles_photo_path_check
    check (photo_path ~ '^[0-9a-f-]{36}/[A-Za-z0-9._-]{1,80}$');
exception when duplicate_object then null; end $$;
alter table app.profiles add column if not exists bff_id uuid references app.profiles (id) on delete set null;
alter table app.profiles add column if not exists qrun_host boolean not null default false;
alter table app.profiles add column if not exists qrun_host_requested_at timestamptz;
alter table app.profiles add column if not exists pro_since timestamptz;
-- Members set their own theme, photo and BFF (the trigger below checks the plan). plan,
-- pro_since and qrun_host stay without a grant: only admins change them.
grant update (theme, photo_path, bff_id) on app.profiles to authenticated;

-- QRuns posted by StepQ from the dashboard.
alter table app.qruns add column if not exists official boolean not null default false;
-- Challenges only Pro members (or whoever has all_challenges) can join.
alter table app.challenges add column if not exists pro_only boolean not null default false;

-- Days a member restored a streak with a friend (counts as a share that day).
create table if not exists app.streak_restores (
  user_id uuid not null references app.profiles (id) on delete cascade,
  friend_id uuid not null references app.profiles (id) on delete cascade,
  day date not null,
  created_at timestamptz not null default now(),
  primary key (user_id, friend_id, day)
);
create index if not exists streak_restores_month_idx on app.streak_restores (user_id, created_at);

-- Counts that must not go back down when something is deleted (a deleted post still used one
-- of this month's posts).
create table if not exists app.usage_events (
  id bigint generated always as identity primary key,
  user_id uuid not null references app.profiles (id) on delete cascade,
  kind text not null check (kind in ('community_post')),
  at timestamptz not null default now()
);
create index if not exists usage_events_idx on app.usage_events (user_id, kind, at);

-- Who changed what from the dashboard.
create table if not exists app.admin_log (
  id bigint generated always as identity primary key,
  at timestamptz not null default now(),
  admin_id uuid,
  action text not null,
  target text,
  detail jsonb not null default '{}'::jsonb
);

alter table app.streak_restores enable row level security;
alter table app.streak_restores force row level security;
alter table app.usage_events enable row level security;
alter table app.usage_events force row level security;
alter table app.admin_log enable row level security;
alter table app.admin_log force row level security;
grant select on app.streak_restores, app.usage_events, app.admin_log to authenticated;
drop policy if exists streak_restores_read on app.streak_restores;
create policy streak_restores_read on app.streak_restores for select to authenticated using (user_id = auth.uid());
drop policy if exists usage_events_read on app.usage_events;
create policy usage_events_read on app.usage_events for select to authenticated using (user_id = auth.uid());
-- admin_log: admins only (below).

do $$
declare t text;
begin
  if to_regprocedure('stepq.is_admin()') is not null then
    foreach t in array array['plan_features', 'streak_restores', 'usage_events', 'admin_log'] loop
      execute format('drop policy if exists admins_read on app.%I', t);
      execute format('create policy admins_read on app.%I for select to authenticated using (stepq.is_admin())', t);
    end loop;
  end if;
end $$;

-- ---------------------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------------------
-- India time: days and months roll over at midnight IST.
create or replace function app.ist_today() returns date
language sql stable set search_path = ''
as $$ select (now() at time zone 'Asia/Kolkata')::date $$;

create or replace function app.ist_month_start() returns timestamptz
language sql stable set search_path = ''
as $$ select date_trunc('month', now() at time zone 'Asia/Kolkata') at time zone 'Asia/Kolkata' $$;

-- A member's value for a feature: their plan's number (null = unlimited). 0 for an unknown key.
create or replace function app.feature_value(u uuid, k text) returns integer
language plpgsql stable security definer set search_path = ''
as $$
declare
  f app.plan_features%rowtype;
  pro boolean;
begin
  select * into f from app.plan_features where key = k;
  if not found then
    return 0;
  end if;
  select p.plan = 'pro' into pro from app.profiles p where p.id = u;
  return case when coalesce(pro, false) then f.pro_value else f.free_value end;
end $$;

-- Does the member have a switch on (or a limit above 0 / unlimited)?
create or replace function app.has_feature(u uuid, k text) returns boolean
language sql stable security definer set search_path = ''
as $$ select coalesce(app.feature_value(u, k) <> 0, true) $$;

revoke all on function app.ist_today(), app.ist_month_start(), app.feature_value(uuid, text),
  app.has_feature(uuid, text) from public, anon;
grant execute on function app.ist_today(), app.ist_month_start(), app.feature_value(uuid, text),
  app.has_feature(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------------------
-- My plan: what I get, and what I've used this day / month. The app reads this on start.
-- ---------------------------------------------------------------------------------------
create or replace function app.my_plan() returns jsonb
language sql stable security definer set search_path = ''
as $$
  select jsonb_build_object(
    'plan', p.plan,
    'is_pro', p.plan = 'pro',
    'pro_since', p.pro_since,
    'qrun_host', p.qrun_host,
    'qrun_host_requested_at', p.qrun_host_requested_at,
    'theme', p.theme,
    'photo_path', p.photo_path,
    'bff_id', p.bff_id,
    'features', coalesce((
      select jsonb_agg(jsonb_build_object(
        'key', f.key, 'label', f.label, 'kind', f.kind, 'unit', f.unit,
        'free', f.free_value, 'pro', f.pro_value,
        'mine', case when p.plan = 'pro' then f.pro_value else f.free_value end
      ) order by f.sort, f.key)
      from app.plan_features f
    ), '[]'::jsonb),
    'usage', jsonb_build_object(
      'community_posts', (select count(*) from app.usage_events e
                          where e.user_id = p.id and e.kind = 'community_post' and e.at >= app.ist_month_start()),
      'streak_restores', (select count(*) from app.streak_restores r
                          where r.user_id = p.id and r.created_at >= app.ist_month_start()),
      'water_reminders', (select count(*) from app.water_reminders w
                          where w.from_user = p.id
                            and (w.sent_at at time zone 'Asia/Kolkata')::date = app.ist_today())
    ),
    'month_resets_at', (app.ist_month_start() at time zone 'Asia/Kolkata' + interval '1 month') at time zone 'Asia/Kolkata'
  )
  from app.profiles p
  where p.id = auth.uid()
$$;

-- Pro badge and photo for people shown on screen (feed, leaderboards, friends). The badge is
-- public; the photo only for people I can already see (me, friends, group mates).
create or replace function app.people_badges(ids uuid[])
returns table (id uuid, pro boolean, photo_path text)
language sql stable security definer set search_path = ''
as $$
  select p.id,
         p.plan = 'pro' and app.has_feature(p.id, 'profile_badge'),
         case when p.photo_path is not null and app.has_feature(p.id, 'upload_photo') and app.can_see(p.id)
              then p.photo_path end
  from app.profiles p
  where auth.uid() is not null and p.id = any (ids[1:500])
$$;

revoke all on function app.my_plan(), app.people_badges(uuid[]) from public, anon;
grant execute on function app.my_plan(), app.people_badges(uuid[]) to authenticated;

-- ---------------------------------------------------------------------------------------
-- Theme, photo and BFF: members set them, the plan decides if they may.
-- ---------------------------------------------------------------------------------------
create or replace function app.check_pro_fields() returns trigger
language plpgsql security definer set search_path = ''
as $$
declare
  fresh boolean := tg_op = 'INSERT';
begin
  -- The service role and admins' dashboard functions aren't checked.
  if auth.uid() is null or app.i_am_admin() then
    return new;
  end if;
  if new.theme <> 'leaf' and (fresh or new.theme is distinct from old.theme)
     and not app.has_feature(new.id, 'change_theme') then
    raise exception 'Themes are a StepQ Pro feature.' using errcode = '42501', hint = 'plan_limit:change_theme';
  end if;
  if new.photo_path is not null and (fresh or new.photo_path is distinct from old.photo_path) then
    if not app.has_feature(new.id, 'upload_photo') then
      raise exception 'Profile photos are a StepQ Pro feature.' using errcode = '42501', hint = 'plan_limit:upload_photo';
    end if;
    if split_part(new.photo_path, '/', 1) <> new.id::text then
      raise exception 'That photo isn''t yours.' using errcode = '42501';
    end if;
  end if;
  if new.bff_id is not null and (fresh or new.bff_id is distinct from old.bff_id) then
    if not app.has_feature(new.id, 'pin_bff') then
      raise exception 'Pinning a BFF is a StepQ Pro feature.' using errcode = '42501', hint = 'plan_limit:pin_bff';
    end if;
    if new.bff_id = new.id or not app.are_friends(new.id, new.bff_id) then
      raise exception 'You can only pin a friend.' using errcode = '42501';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists profiles_check_pro_fields on app.profiles;
create trigger profiles_check_pro_fields before insert or update on app.profiles
  for each row execute function app.check_pro_fields();

-- ---------------------------------------------------------------------------------------
-- QRuns: StepQ and approved Pro hosts only
-- ---------------------------------------------------------------------------------------
create or replace function app.check_qrun_host() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if auth.uid() is null or app.i_am_admin() then
    return new;
  end if;
  if not app.has_feature(auth.uid(), 'plan_qrun')
     or not exists (select 1 from app.profiles p where p.id = auth.uid() and p.qrun_host) then
    raise exception 'QRuns are posted by StepQ and by Pro members StepQ has made hosts.'
      using errcode = '42501', hint = 'plan_limit:plan_qrun';
  end if;
  new.official := false;
  return new;
end $$;

drop trigger if exists qruns_check_host on app.qruns;
create trigger qruns_check_host before insert on app.qruns
  for each row execute function app.check_qrun_host();

-- A Pro member asks to host QRuns. Admins see the request in the dashboard.
create or replace function app.request_qrun_host() returns void
language plpgsql volatile security definer set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  if not app.has_feature(auth.uid(), 'plan_qrun') then
    raise exception 'Hosting QRuns is for StepQ Pro members.' using errcode = '42501', hint = 'plan_limit:plan_qrun';
  end if;
  update app.profiles set qrun_host_requested_at = coalesce(qrun_host_requested_at, now())
  where id = auth.uid() and not qrun_host;
end $$;

revoke all on function app.request_qrun_host() from public, anon;
grant execute on function app.request_qrun_host() to authenticated;

-- ---------------------------------------------------------------------------------------
-- Water reminders a day, from the plan (was a fixed 5 on Free)
-- ---------------------------------------------------------------------------------------
create or replace function app.check_water_reminder() returns trigger
language plpgsql security definer set search_path = ''
as $$
declare
  sent_today integer;
  lim integer;
begin
  if not app.are_friends(new.from_user, new.to_user) then
    raise exception 'You can only remind friends.' using errcode = '42501';
  end if;
  lim := app.feature_value(new.from_user, 'water_reminders');
  if lim is not null then
    select count(*) into sent_today from app.water_reminders
    where from_user = new.from_user
      and (sent_at at time zone 'Asia/Kolkata')::date = app.ist_today();
    if sent_today >= lim then
      raise exception '%', format('Your plan sends %s water reminders a day. StepQ Pro sends as many as you like.', lim)
        using errcode = 'P0001', hint = 'plan_limit:water_reminders';
    end if;
  end if;
  new.sent_at := now();
  return new;
end $$;

-- ---------------------------------------------------------------------------------------
-- Pro-only challenges
-- ---------------------------------------------------------------------------------------
create or replace function app.check_challenge_entry() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if exists (select 1 from app.challenges c where c.id = new.challenge_id and c.pro_only)
     and not app.has_feature(new.user_id, 'all_challenges') then
    raise exception 'This challenge is for StepQ Pro members.' using errcode = '42501', hint = 'plan_limit:all_challenges';
  end if;
  return new;
end $$;

drop trigger if exists challenge_entries_check_plan on app.challenge_entries;
create trigger challenge_entries_check_plan before insert on app.challenge_entries
  for each row execute function app.check_challenge_entry();

-- Is a member eligible for this period's reward ('weekly', 'monthly' or 'yearly')? For the
-- dashboard and the app's ranking cards.
create or replace function app.reward_eligible(u uuid, period text) returns boolean
language sql stable security definer set search_path = ''
as $$ select period in ('weekly', 'monthly', 'yearly') and app.has_feature(u, 'reward_' || period) $$;
revoke all on function app.reward_eligible(uuid, text) from public, anon;
grant execute on function app.reward_eligible(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------------------
-- Insights: my step history, as far back as my plan allows
-- ---------------------------------------------------------------------------------------
create or replace function app.step_history(p_days integer)
returns table (day date, steps integer, goal integer, distance_m integer)
language sql stable security definer set search_path = ''
as $$
  select d.day, d.steps, d.goal, d.distance_m
  from app.daily_steps d
  where auth.uid() is not null
    and d.user_id = auth.uid()
    and d.day > app.ist_today() - least(
      greatest(coalesce(p_days, 7), 1),
      coalesce(app.feature_value(auth.uid(), 'analysis_days'), 3660),
      3660)
    and d.day <= app.ist_today() + 1
  order by d.day
$$;
revoke all on function app.step_history(integer) from public, anon;
grant execute on function app.step_history(integer) to authenticated;

-- ---------------------------------------------------------------------------------------
-- Community: Free members start a few posts a month; replies and likes are unlimited
-- ---------------------------------------------------------------------------------------
create or replace function app.create_post(p_body text, p_parent uuid default null, p_share_day date default null)
returns uuid
language plpgsql volatile security definer set search_path = ''
as $$
declare
  me uuid := auth.uid();
  b text := btrim(coalesce(p_body, ''));
  s integer;
  g integer;
  lim integer;
  new_id uuid;
begin
  if me is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  if not exists (select 1 from app.profiles where id = me) then
    raise exception 'Finish setting up your profile first.' using errcode = '42501';
  end if;
  if char_length(b) > 500 then
    raise exception 'Keep it under 500 characters.' using errcode = '22023';
  end if;
  if p_share_day is null and b = '' then
    raise exception 'Write something first.' using errcode = '22023';
  end if;
  if p_parent is not null and not exists (select 1 from app.posts where id = p_parent) then
    raise exception 'That post was deleted.' using errcode = 'P0002';
  end if;
  if (select count(*) from app.posts where author = me and created_at > now() - interval '1 hour') >= 30 then
    raise exception 'You''re posting a lot. Take a breather and try again in a bit.' using errcode = '54000';
  end if;

  -- New posts (not replies) count towards the plan's monthly limit.
  if p_parent is null then
    lim := app.feature_value(me, 'community_posts');
    if lim is not null and (
      select count(*) from app.usage_events
      where user_id = me and kind = 'community_post' and at >= app.ist_month_start()
    ) >= lim then
      raise exception '%', case when lim = 0
          then 'Starting posts is for StepQ Pro members. You can still like and reply.'
          else format('You''ve used your %s posts this month. You can still like and reply, or go Pro to post as much as you like.', lim)
        end
        using errcode = 'P0001', hint = 'plan_limit:community_posts';
    end if;
  end if;

  if p_share_day is not null then
    if p_parent is not null then
      raise exception 'Steps can only be shared in a new post.' using errcode = '22023';
    end if;
    if p_share_day not between current_date - 1 and current_date + 1 then
      raise exception 'Only today''s steps can be shared.' using errcode = '22023';
    end if;
    select d.steps into s from app.daily_steps d where d.user_id = me and d.day = p_share_day;
    select pr.daily_goal into g from app.profiles pr where pr.id = me;
    insert into app.posts (author, body, kind, day, steps, goal)
    values (me, b, 'achievement', p_share_day, coalesce(s, 0), g)
    returning id into new_id;
  else
    insert into app.posts (author, parent_id, body) values (me, p_parent, b) returning id into new_id;
  end if;
  if p_parent is null then
    insert into app.usage_events (user_id, kind) values (me, 'community_post');
  end if;
  return new_id;
end $$;

revoke all on function app.create_post(text, uuid, date) from public, anon;
grant execute on function app.create_post(text, uuid, date) to authenticated;

-- ---------------------------------------------------------------------------------------
-- Streak restores: missed sharing with a friend yesterday? Restore the day (limit a month).
-- ---------------------------------------------------------------------------------------
-- Per-friend streaks (from 20261125000000_friend_streaks), now counting restored days.
create or replace function app.friend_streaks(p_today date)
returns table (friend_id uuid, streak integer, shared_today boolean)
language sql stable security definer set search_path = ''
as $$
  with days as (
    select s.to_user as friend, s.day, true as shared
    from app.step_shares s
    where s.from_user = auth.uid() and s.day <= p_today and s.day > p_today - 400
    union all
    select r.friend_id, r.day, false
    from app.streak_restores r
    where r.user_id = auth.uid() and r.day <= p_today and r.day > p_today - 400
  ), daily as (
    select friend, day, bool_or(shared) as shared from days group by friend, day
  ), per_friend as (
    select friend,
           bool_or(day = p_today and shared) as shared_today,
           case when bool_or(day = p_today) then p_today else p_today - 1 end as start
    from daily
    group by friend
  ), numbered as (
    select d.friend, f.start - d.day as back,
           row_number() over (partition by d.friend order by d.day desc) - 1 as rn
    from daily d
    join per_friend f on f.friend = d.friend
    where d.day <= f.start
  )
  select f.friend,
         (select count(*)::integer from numbered n where n.friend = f.friend and n.back = n.rn),
         f.shared_today
  from per_friend f
  where auth.uid() is not null and app.are_friends(auth.uid(), f.friend)
$$;

-- Streaks I lost yesterday and can still restore: nothing with that friend yesterday, but a
-- streak up to the day before. `streak` is how long it was.
create or replace function app.lost_streaks(p_today date)
returns table (friend_id uuid, streak integer)
language sql stable security definer set search_path = ''
as $$
  with days as (
    select s.to_user as friend, s.day from app.step_shares s
    where s.from_user = auth.uid() and s.day between p_today - 400 and p_today
    union
    select r.friend_id, r.day from app.streak_restores r
    where r.user_id = auth.uid() and r.day between p_today - 400 and p_today
  ), lost as (
    select distinct d.friend from days d
    where d.day = p_today - 2
      and not exists (select 1 from days e where e.friend = d.friend and e.day = p_today - 1)
  ), numbered as (
    select d.friend, (p_today - 2) - d.day as back,
           row_number() over (partition by d.friend order by d.day desc) - 1 as rn
    from days d join lost l on l.friend = d.friend
    where d.day <= p_today - 2
  )
  select l.friend, (select count(*)::integer from numbered n where n.friend = l.friend and n.back = n.rn)
  from lost l
  where auth.uid() is not null
    and p_today between app.ist_today() - 1 and app.ist_today() + 1
    and app.are_friends(auth.uid(), l.friend)
$$;

-- Restores yesterday's streak with a friend. Returns the restores left this month (null =
-- unlimited).
create or replace function app.restore_streak(p_friend uuid, p_today date)
returns integer
language plpgsql volatile security definer set search_path = ''
as $$
declare
  me uuid := auth.uid();
  lim integer;
  used integer;
begin
  if me is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  if not exists (select 1 from app.lost_streaks(p_today) l where l.friend_id = p_friend) then
    raise exception 'There''s no streak with them to restore.' using errcode = '22023';
  end if;
  -- One at a time, so two taps can't both squeeze under the limit.
  perform pg_advisory_xact_lock(hashtext('streak_restore:' || me::text));
  lim := app.feature_value(me, 'streak_restores');
  select count(*) into used from app.streak_restores
  where user_id = me and created_at >= app.ist_month_start();
  if lim is not null and used >= lim then
    raise exception '%', case when lim = 0
        then 'Streak restores are a StepQ Pro feature.'
        else format('You''ve used your %s streak restores this month.', lim)
      end
      using errcode = 'P0001', hint = 'plan_limit:streak_restores';
  end if;
  insert into app.streak_restores (user_id, friend_id, day) values (me, p_friend, p_today - 1)
  on conflict do nothing;
  return case when lim is null then null else greatest(lim - used - 1, 0) end;
end $$;

revoke all on function app.friend_streaks(date), app.lost_streaks(date), app.restore_streak(uuid, date)
  from public, anon;
grant execute on function app.friend_streaks(date), app.lost_streaks(date), app.restore_streak(uuid, date)
  to authenticated;

-- ---------------------------------------------------------------------------------------
-- Admin dashboard (stepq.in/admin). Each checks the caller is in stepq.admins.
-- ---------------------------------------------------------------------------------------
create or replace function app.admin_check() returns uuid
language plpgsql stable security definer set search_path = ''
as $$
begin
  if not app.i_am_admin() then
    raise exception 'Admins only.' using errcode = '42501';
  end if;
  return auth.uid();
end $$;

-- Change a feature's Free and Pro values. Switches take 0 or 1; limits a number, or null for
-- unlimited.
create or replace function app.admin_set_feature(p_key text, p_free integer, p_pro integer)
returns void
language plpgsql volatile security definer set search_path = ''
as $$
declare
  me uuid := app.admin_check();
  f app.plan_features%rowtype;
begin
  select * into f from app.plan_features where key = p_key;
  if not found then
    raise exception 'No feature called %.', p_key using errcode = '22023';
  end if;
  if f.kind = 'switch' and (p_free is null or p_pro is null or p_free not in (0, 1) or p_pro not in (0, 1)) then
    raise exception 'A switch is on or off.' using errcode = '22023';
  end if;
  if coalesce(p_free, 0) < 0 or coalesce(p_pro, 0) < 0 or coalesce(p_free, 0) > 100000 or coalesce(p_pro, 0) > 100000 then
    raise exception 'Use a number from 0 to 100000, or leave it empty for unlimited.' using errcode = '22023';
  end if;
  update app.plan_features
  set free_value = p_free, pro_value = p_pro, updated_at = now(), updated_by = me
  where key = p_key;
  insert into app.admin_log (admin_id, action, target, detail)
  values (me, 'set_feature', p_key, jsonb_build_object(
    'from', jsonb_build_object('free', f.free_value, 'pro', f.pro_value),
    'to', jsonb_build_object('free', p_free, 'pro', p_pro)));
end $$;

-- Make a member Free or Pro, and (Pro only) a QRun host. Going back to Free takes away what
-- the Free plan doesn't include (theme, photo, BFF, hosting); nothing is deleted.
create or replace function app.admin_set_member(p_user uuid, p_plan text, p_qrun_host boolean default null)
returns void
language plpgsql volatile security definer set search_path = ''
as $$
declare
  me uuid := app.admin_check();
  old_row app.profiles%rowtype;
begin
  if p_plan not in ('free', 'pro') then
    raise exception 'Plan is free or pro.' using errcode = '22023';
  end if;
  select * into old_row from app.profiles where id = p_user;
  if not found then
    raise exception 'No member with that id.' using errcode = 'P0002';
  end if;
  update app.profiles
  set plan = p_plan,
      pro_since = case when p_plan = 'pro' then coalesce(pro_since, now()) end,
      qrun_host = coalesce(p_qrun_host, qrun_host),
      qrun_host_requested_at = case when coalesce(p_qrun_host, qrun_host) then null else qrun_host_requested_at end
  where id = p_user;
  -- Drop what the member's plan no longer includes.
  update app.profiles
  set theme = case when app.has_feature(id, 'change_theme') then theme else 'leaf' end,
      photo_path = case when app.has_feature(id, 'upload_photo') then photo_path end,
      bff_id = case when app.has_feature(id, 'pin_bff') then bff_id end,
      qrun_host = qrun_host and app.has_feature(id, 'plan_qrun')
  where id = p_user;
  insert into app.admin_log (admin_id, action, target, detail)
  values (me, 'set_member', p_user::text, jsonb_build_object(
    'from', jsonb_build_object('plan', old_row.plan, 'qrun_host', old_row.qrun_host),
    'to', (select jsonb_build_object('plan', p.plan, 'qrun_host', p.qrun_host) from app.profiles p where p.id = p_user)));
end $$;

-- Post a QRun as StepQ. The admin's own account gets a member profile named "StepQ" the first
-- time, because every QRun has a host profile.
create or replace function app.admin_post_qrun(
  p_title text, p_place text, p_starts_at timestamptz,
  p_lat double precision default null, p_lng double precision default null, p_price_inr integer default 0
) returns uuid
language plpgsql volatile security definer set search_path = ''
as $$
declare
  me uuid := app.admin_check();
  handle text := 'stepq';
  new_id uuid;
begin
  if p_starts_at is null or p_starts_at <= now() then
    raise exception 'Pick a time in the future.' using errcode = '22023';
  end if;
  if char_length(btrim(coalesce(p_title, ''))) not between 1 and 60 then
    raise exception 'Give it a title (up to 60 characters).' using errcode = '22023';
  end if;
  if char_length(btrim(coalesce(p_place, ''))) not between 1 and 120 then
    raise exception 'Say where it starts (up to 120 characters).' using errcode = '22023';
  end if;
  if not exists (select 1 from app.profiles where id = me) then
    if not app.username_available(handle) then
      handle := 'stepq.' || substr(replace(me::text, '-', ''), 1, 6);
    end if;
    insert into app.profiles (id, display_name, username) values (me, 'StepQ', handle);
  end if;
  insert into app.qruns (created_by, title, place_name, lat, lng, starts_at, price_inr, official)
  values (me, btrim(p_title), btrim(p_place), p_lat, p_lng, p_starts_at, coalesce(p_price_inr, 0), true)
  returning id into new_id;
  insert into app.admin_log (admin_id, action, target, detail)
  values (me, 'post_qrun', new_id::text, jsonb_build_object('title', btrim(p_title), 'starts_at', p_starts_at));
  return new_id;
end $$;

-- Mark a challenge Pro-only (or open it to everyone).
create or replace function app.admin_set_challenge_pro_only(p_challenge uuid, p_pro_only boolean)
returns void
language plpgsql volatile security definer set search_path = ''
as $$
declare
  me uuid := app.admin_check();
begin
  update app.challenges set pro_only = coalesce(p_pro_only, false) where id = p_challenge;
  if not found then
    raise exception 'No challenge with that id.' using errcode = 'P0002';
  end if;
  insert into app.admin_log (admin_id, action, target, detail)
  values (me, 'set_challenge_pro_only', p_challenge::text, jsonb_build_object('pro_only', p_pro_only));
end $$;

revoke all on function app.admin_check(), app.admin_set_feature(text, integer, integer),
  app.admin_set_member(uuid, text, boolean),
  app.admin_post_qrun(text, text, timestamptz, double precision, double precision, integer),
  app.admin_set_challenge_pro_only(uuid, boolean) from public, anon;
grant execute on function app.admin_check(), app.admin_set_feature(text, integer, integer),
  app.admin_set_member(uuid, text, boolean),
  app.admin_post_qrun(text, text, timestamptz, double precision, double precision, integer),
  app.admin_set_challenge_pro_only(uuid, boolean) to authenticated;

-- ---------------------------------------------------------------------------------------
-- Profile photos: private `avatars` bucket, "<user id>/<file>". Only on Supabase (skipped on a
-- plain Postgres without the storage schema).
-- ---------------------------------------------------------------------------------------
create or replace function app.photo_visible(object_name text) returns boolean
language plpgsql stable security definer set search_path = ''
as $$
declare owner uuid;
begin
  begin
    owner := split_part(object_name, '/', 1)::uuid;
  exception when others then
    return false;
  end;
  return auth.uid() is not null and app.can_see(owner);
end $$;
revoke all on function app.photo_visible(text) from public, anon;
grant execute on function app.photo_visible(text) to authenticated;

do $$
begin
  if to_regclass('storage.objects') is null or to_regclass('storage.buckets') is null then
    return;
  end if;
  insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
  values ('avatars', 'avatars', false, 1048576, array['image/jpeg', 'image/png', 'image/webp'])
  on conflict (id) do nothing;

  drop policy if exists stepq_avatars_read on storage.objects;
  create policy stepq_avatars_read on storage.objects for select to authenticated
    using (bucket_id = 'avatars' and app.photo_visible(name));
  drop policy if exists stepq_avatars_insert on storage.objects;
  create policy stepq_avatars_insert on storage.objects for insert to authenticated
    with check (bucket_id = 'avatars' and split_part(name, '/', 1) = auth.uid()::text
                and app.has_feature(auth.uid(), 'upload_photo'));
  drop policy if exists stepq_avatars_update on storage.objects;
  create policy stepq_avatars_update on storage.objects for update to authenticated
    using (bucket_id = 'avatars' and split_part(name, '/', 1) = auth.uid()::text)
    with check (bucket_id = 'avatars' and split_part(name, '/', 1) = auth.uid()::text
                and app.has_feature(auth.uid(), 'upload_photo'));
  drop policy if exists stepq_avatars_delete on storage.objects;
  create policy stepq_avatars_delete on storage.objects for delete to authenticated
    using (bucket_id = 'avatars' and split_part(name, '/', 1) = auth.uid()::text);
end $$;
