-- StepQ app schema: everything the Android app reads and writes.
--
-- Lives in its own `app` schema so the website's private `stepq` schema stays out of the
-- public API. The app uses only the anon key; every table has row-level security, and the
-- `authenticated` role gets only the grants each table needs. `anon` gets nothing.
--
-- Additive and idempotent: safe to run more than once. It creates tables and policies only;
-- it reads and changes no existing data.

create schema if not exists app;
revoke all on schema app from public, anon;
grant usage on schema app to authenticated;

-- ---------------------------------------------------------------------------------------
-- Profiles (A05–A09, A26)
-- ---------------------------------------------------------------------------------------
create table if not exists app.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (length(trim(display_name)) between 1 and 40),
  username text not null check (username ~ '^[a-z0-9._]{3,20}$'),
  gender text check (gender in ('female', 'male', 'non_binary', 'prefer_not')),
  birthdate date check (birthdate between date '1900-01-01' and current_date),
  avatar jsonb not null default '{}'::jsonb,
  daily_goal integer not null default 8000 check (daily_goal between 1000 and 100000),
  plan text not null default 'free' check (plan in ('free', 'pro')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists profiles_username_key on app.profiles (lower(username));

-- ---------------------------------------------------------------------------------------
-- Daily steps from Health Connect (A10–A12)
-- ---------------------------------------------------------------------------------------
create table if not exists app.daily_steps (
  user_id uuid not null references app.profiles (id) on delete cascade,
  day date not null,
  steps integer not null default 0 check (steps between 0 and 200000),
  distance_m integer not null default 0 check (distance_m >= 0),
  active_minutes integer not null default 0 check (active_minutes between 0 and 1440),
  calories integer not null default 0 check (calories >= 0),
  updated_at timestamptz not null default now(),
  primary key (user_id, day),
  check (day <= current_date + 1)
);

-- ---------------------------------------------------------------------------------------
-- Friends (A13) and water reminders (A14)
-- ---------------------------------------------------------------------------------------
create table if not exists app.friendships (
  requester uuid not null references app.profiles (id) on delete cascade,
  addressee uuid not null references app.profiles (id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  primary key (requester, addressee),
  check (requester <> addressee)
);
create index if not exists friendships_addressee_idx on app.friendships (addressee);

create table if not exists app.water_reminders (
  id bigint generated always as identity primary key,
  from_user uuid not null references app.profiles (id) on delete cascade,
  to_user uuid not null references app.profiles (id) on delete cascade,
  sent_at timestamptz not null default now(),
  check (from_user <> to_user)
);
create index if not exists water_reminders_from_idx on app.water_reminders (from_user, sent_at);

-- ---------------------------------------------------------------------------------------
-- Groups (A15–A17)
-- ---------------------------------------------------------------------------------------
create table if not exists app.groups (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) between 1 and 40),
  created_by uuid not null references app.profiles (id) on delete cascade,
  invite_code text not null unique default substr(replace(gen_random_uuid()::text, '-', ''), 1, 12),
  created_at timestamptz not null default now()
);

create table if not exists app.group_members (
  group_id uuid not null references app.groups (id) on delete cascade,
  user_id uuid not null references app.profiles (id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);
create index if not exists group_members_user_idx on app.group_members (user_id);

-- ---------------------------------------------------------------------------------------
-- QRuns: walks anyone can post and join (A18–A21)
-- ---------------------------------------------------------------------------------------
create table if not exists app.qruns (
  id uuid primary key default gen_random_uuid(),
  created_by uuid not null references app.profiles (id) on delete cascade,
  title text not null check (length(trim(title)) between 1 and 60),
  place_name text not null check (length(trim(place_name)) between 1 and 120),
  lat double precision check (lat between -90 and 90),
  lng double precision check (lng between -180 and 180),
  starts_at timestamptz not null,
  created_at timestamptz not null default now()
);
create index if not exists qruns_starts_at_idx on app.qruns (starts_at);

create table if not exists app.qrun_participants (
  qrun_id uuid not null references app.qruns (id) on delete cascade,
  user_id uuid not null references app.profiles (id) on delete cascade,
  -- A20: joining needs the "I understand and want to join" box ticked.
  accepted_disclaimer_at timestamptz not null default now(),
  primary key (qrun_id, user_id)
);

-- ---------------------------------------------------------------------------------------
-- Challenges (A22–A25). Written by admins only; users join them.
-- ---------------------------------------------------------------------------------------
create table if not exists app.challenges (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  partner_name text,
  description text,
  reward text,
  target_steps integer check (target_steps > 0),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  created_at timestamptz not null default now(),
  check (ends_at > starts_at)
);

create table if not exists app.challenge_entries (
  challenge_id uuid not null references app.challenges (id) on delete cascade,
  user_id uuid not null references app.profiles (id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (challenge_id, user_id)
);

-- ---------------------------------------------------------------------------------------
-- Push tokens (FCM) and the Pro waitlist (A27)
-- ---------------------------------------------------------------------------------------
create table if not exists app.push_tokens (
  token text primary key,
  user_id uuid not null references app.profiles (id) on delete cascade,
  platform text not null default 'android' check (platform in ('android', 'ios')),
  updated_at timestamptz not null default now()
);

create table if not exists app.pro_waitlist (
  user_id uuid primary key references app.profiles (id) on delete cascade,
  joined_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------------------
-- Helpers. Security definer so policies can ask about other rows without recursing into
-- their own RLS; each answers only a yes/no about the caller.
-- ---------------------------------------------------------------------------------------
create or replace function app.are_friends(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from app.friendships
    where status = 'accepted'
      and ((requester = a and addressee = b) or (requester = b and addressee = a))
  )
$$;

create or replace function app.is_group_member(g uuid, u uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (select 1 from app.group_members where group_id = g and user_id = u)
$$;

create or replace function app.shares_group(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from app.group_members x join app.group_members y using (group_id)
    where x.user_id = a and y.user_id = b
  )
$$;

-- Whose name, avatar and steps the caller may see: themselves, friends, and group mates.
create or replace function app.can_see(target uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select target = auth.uid()
      or app.are_friends(auth.uid(), target)
      or app.shares_group(auth.uid(), target)
$$;

-- A06: is this username free? Answers only yes/no, so it cannot list users.
create or replace function app.username_available(name text) returns boolean
language sql stable security definer set search_path = ''
as $$
  select name ~ '^[a-z0-9._]{3,20}$'
     and not exists (select 1 from app.profiles where lower(username) = lower(name))
$$;

-- A13: find one person by exact username to send a friend request. Public fields only.
create or replace function app.find_by_username(name text)
returns table (id uuid, display_name text, username text, avatar jsonb)
language sql stable security definer set search_path = ''
as $$
  select p.id, p.display_name, p.username, p.avatar
  from app.profiles p
  where auth.uid() is not null and lower(p.username) = lower(name)
$$;

revoke all on function app.are_friends(uuid, uuid), app.is_group_member(uuid, uuid),
  app.shares_group(uuid, uuid), app.can_see(uuid), app.username_available(text),
  app.find_by_username(text) from public, anon;
grant execute on function app.are_friends(uuid, uuid), app.is_group_member(uuid, uuid),
  app.shares_group(uuid, uuid), app.can_see(uuid), app.username_available(text),
  app.find_by_username(text) to authenticated;

-- A14: free plan sends up to 5 water reminders a day (IST), and only to friends.
create or replace function app.check_water_reminder() returns trigger
language plpgsql security definer set search_path = ''
as $$
declare
  sent_today integer;
  my_plan text;
begin
  if not app.are_friends(new.from_user, new.to_user) then
    raise exception 'You can only remind friends.' using errcode = '42501';
  end if;
  select plan into my_plan from app.profiles where id = new.from_user;
  select count(*) into sent_today from app.water_reminders
  where from_user = new.from_user
    and (sent_at at time zone 'Asia/Kolkata')::date = (now() at time zone 'Asia/Kolkata')::date;
  if coalesce(my_plan, 'free') = 'free' and sent_today >= 5 then
    raise exception 'Free plan sends 5 water reminders a day.' using errcode = 'P0001';
  end if;
  new.sent_at := now();
  return new;
end
$$;
drop trigger if exists water_reminder_limit on app.water_reminders;
create trigger water_reminder_limit before insert on app.water_reminders
  for each row execute function app.check_water_reminder();

create or replace function app.touch_updated_at() returns trigger
language plpgsql set search_path = ''
as $$ begin new.updated_at := now(); return new; end $$;
drop trigger if exists profiles_touch on app.profiles;
create trigger profiles_touch before update on app.profiles
  for each row execute function app.touch_updated_at();
drop trigger if exists daily_steps_touch on app.daily_steps;
create trigger daily_steps_touch before update on app.daily_steps
  for each row execute function app.touch_updated_at();

-- A07: birthdate is set once and can't be changed afterwards.
create or replace function app.lock_birthdate() returns trigger
language plpgsql set search_path = ''
as $$
begin
  if old.birthdate is not null and new.birthdate is distinct from old.birthdate then
    raise exception 'Birthdate can''t be changed.' using errcode = '42501';
  end if;
  return new;
end
$$;
drop trigger if exists profiles_lock_birthdate on app.profiles;
create trigger profiles_lock_birthdate before update on app.profiles
  for each row execute function app.lock_birthdate();

-- ---------------------------------------------------------------------------------------
-- Grants. Columns a user must not set themselves (plan, ids, timestamps) are left out.
-- ---------------------------------------------------------------------------------------
revoke all on all tables in schema app from public, anon, authenticated;

grant select on app.profiles to authenticated;
grant insert (id, display_name, username, gender, birthdate, avatar, daily_goal)
  on app.profiles to authenticated;
grant update (display_name, username, gender, birthdate, avatar, daily_goal)
  on app.profiles to authenticated;

grant select, delete on app.daily_steps to authenticated;
grant insert (user_id, day, steps, distance_m, active_minutes, calories),
  update (steps, distance_m, active_minutes, calories) on app.daily_steps to authenticated;

grant select, delete on app.friendships to authenticated;
grant insert (requester, addressee), update (status) on app.friendships to authenticated;

grant select on app.water_reminders to authenticated;
grant insert (from_user, to_user) on app.water_reminders to authenticated;

grant select, delete on app.groups to authenticated;
grant insert (name, created_by), update (name) on app.groups to authenticated;
grant select, delete on app.group_members to authenticated;
grant insert (group_id, user_id) on app.group_members to authenticated;

grant select, delete on app.qruns to authenticated;
grant insert (created_by, title, place_name, lat, lng, starts_at),
  update (title, place_name, lat, lng, starts_at) on app.qruns to authenticated;
grant select, delete on app.qrun_participants to authenticated;
grant insert (qrun_id, user_id) on app.qrun_participants to authenticated;

grant select on app.challenges to authenticated;
grant select, delete on app.challenge_entries to authenticated;
grant insert (challenge_id, user_id) on app.challenge_entries to authenticated;

grant select, delete on app.push_tokens to authenticated;
grant insert (token, user_id, platform), update (user_id, platform) on app.push_tokens to authenticated;

grant select, insert (user_id), delete on app.pro_waitlist to authenticated;

-- ---------------------------------------------------------------------------------------
-- Row-level security
-- ---------------------------------------------------------------------------------------
do $$
declare t text;
begin
  foreach t in array array['profiles', 'daily_steps', 'friendships', 'water_reminders',
    'groups', 'group_members', 'qruns', 'qrun_participants', 'challenges',
    'challenge_entries', 'push_tokens', 'pro_waitlist']
  loop
    execute format('alter table app.%I enable row level security', t);
    execute format('alter table app.%I force row level security', t);
  end loop;
end $$;

-- Policies are dropped and recreated so re-running this file applies edits.
do $$
declare r record;
begin
  for r in select tablename, policyname from pg_policies where schemaname = 'app' loop
    execute format('drop policy %I on app.%I', r.policyname, r.tablename);
  end loop;
end $$;

create policy profiles_read on app.profiles for select to authenticated
  using (app.can_see(id));
create policy profiles_insert on app.profiles for insert to authenticated
  with check (id = auth.uid());
create policy profiles_update on app.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

create policy steps_read on app.daily_steps for select to authenticated
  using (app.can_see(user_id));
create policy steps_insert on app.daily_steps for insert to authenticated
  with check (user_id = auth.uid());
create policy steps_update on app.daily_steps for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy steps_delete on app.daily_steps for delete to authenticated
  using (user_id = auth.uid());

create policy friendships_read on app.friendships for select to authenticated
  using (auth.uid() in (requester, addressee));
create policy friendships_insert on app.friendships for insert to authenticated
  with check (requester = auth.uid());
-- Only the person asked can accept.
create policy friendships_accept on app.friendships for update to authenticated
  using (addressee = auth.uid()) with check (addressee = auth.uid() and status = 'accepted');
create policy friendships_delete on app.friendships for delete to authenticated
  using (auth.uid() in (requester, addressee));

create policy water_read on app.water_reminders for select to authenticated
  using (auth.uid() in (from_user, to_user));
create policy water_insert on app.water_reminders for insert to authenticated
  with check (from_user = auth.uid());

create policy groups_read on app.groups for select to authenticated
  using (created_by = auth.uid() or app.is_group_member(id, auth.uid()));
create policy groups_insert on app.groups for insert to authenticated
  with check (created_by = auth.uid());
create policy groups_update on app.groups for update to authenticated
  using (created_by = auth.uid()) with check (created_by = auth.uid());
create policy groups_delete on app.groups for delete to authenticated
  using (created_by = auth.uid());

create policy members_read on app.group_members for select to authenticated
  using (app.is_group_member(group_id, auth.uid()));
-- The creator adds friends (A16); anyone else joins only themselves (via invite link).
create policy members_insert on app.group_members for insert to authenticated
  with check (
    user_id = auth.uid()
    or (exists (select 1 from app.groups g where g.id = group_id and g.created_by = auth.uid())
        and app.are_friends(auth.uid(), user_id))
  );
create policy members_delete on app.group_members for delete to authenticated
  using (
    user_id = auth.uid()
    or exists (select 1 from app.groups g where g.id = group_id and g.created_by = auth.uid())
  );

-- QRuns are public to signed-in users (A18 lists walks nearby).
create policy qruns_read on app.qruns for select to authenticated using (true);
create policy qruns_insert on app.qruns for insert to authenticated
  with check (created_by = auth.uid() and starts_at > now());
create policy qruns_update on app.qruns for update to authenticated
  using (created_by = auth.uid()) with check (created_by = auth.uid());
create policy qruns_delete on app.qruns for delete to authenticated
  using (created_by = auth.uid());

create policy qrun_people_read on app.qrun_participants for select to authenticated using (true);
create policy qrun_people_insert on app.qrun_participants for insert to authenticated
  with check (user_id = auth.uid());
create policy qrun_people_delete on app.qrun_participants for delete to authenticated
  using (user_id = auth.uid());

create policy challenges_read on app.challenges for select to authenticated using (true);

create policy entries_read on app.challenge_entries for select to authenticated
  using (user_id = auth.uid());
create policy entries_insert on app.challenge_entries for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (select 1 from app.challenges c where c.id = challenge_id and c.ends_at > now())
  );
create policy entries_delete on app.challenge_entries for delete to authenticated
  using (user_id = auth.uid());

create policy tokens_own on app.push_tokens for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy waitlist_own on app.pro_waitlist for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Admins (people in stepq.admins) can read every app table from the stepq.in dashboard.
do $$
declare t text;
begin
  if to_regprocedure('stepq.is_admin()') is null then
    return;
  end if;
  foreach t in array array['profiles', 'daily_steps', 'friendships', 'water_reminders',
    'groups', 'group_members', 'qruns', 'qrun_participants', 'challenges',
    'challenge_entries', 'push_tokens', 'pro_waitlist']
  loop
    execute format(
      'create policy admins_read on app.%I for select to authenticated using (stepq.is_admin())', t);
  end loop;
end $$;
