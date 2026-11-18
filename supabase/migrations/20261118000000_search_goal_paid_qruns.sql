-- Feedback round, Nov 18:
--   * Add friends: live search by name or username, people you may know, and contacts.
--   * Daily goal changes start the next day (next_daily_goal / next_goal_from).
--   * QRuns can have a price; only StepQ admins can post or set a paid one.
--
-- Every function below returns only the public fields find_by_username already returns
-- (id, display_name, username, avatar), to signed-in users only, with a small row limit.

-- ---------------------------------------------------------------------------------------
-- Daily goal from the next day
-- ---------------------------------------------------------------------------------------
alter table app.profiles add column if not exists next_daily_goal integer
  check (next_daily_goal between 1000 and 100000);
alter table app.profiles add column if not exists next_goal_from date;
grant update (next_daily_goal, next_goal_from) on app.profiles to authenticated;

-- ---------------------------------------------------------------------------------------
-- Add friends
-- ---------------------------------------------------------------------------------------
-- Search as you type: username or name starting with what was typed (any word of the name).
-- Needs at least 2 characters and returns at most 20 people, so it can't page through users.
create or replace function app.search_people(q text)
returns table (id uuid, display_name text, username text, avatar jsonb, mutual bigint)
language sql stable security definer set search_path = ''
as $$
  with term as (
    select replace(replace(replace(lower(trim(both from ltrim(trim(q), '@'))), '\', '\\'), '%', '\%'), '_', '\_') as t
  ), mine as (
    select case when f.requester = auth.uid() then f.addressee else f.requester end as friend
    from app.friendships f
    where f.status = 'accepted' and auth.uid() in (f.requester, f.addressee)
  )
  select p.id, p.display_name, p.username, p.avatar,
         (select count(*) from app.friendships f2
           where f2.status = 'accepted'
             and ((f2.requester = p.id and f2.addressee in (select friend from mine))
               or (f2.addressee = p.id and f2.requester in (select friend from mine)))) as mutual
  from app.profiles p, term
  where auth.uid() is not null
    and length(term.t) >= 2
    and p.id <> auth.uid()
    and (lower(p.username) like term.t || '%'
         or lower(p.display_name) like term.t || '%'
         or lower(p.display_name) like '% ' || term.t || '%')
  order by (lower(p.username) = replace(term.t, '\', '')) desc,
           (lower(p.username) like term.t || '%') desc,
           mutual desc, p.display_name
  limit 20
$$;

-- Friends of my friends I have no friendship row with yet, most mutual friends first.
create or replace function app.people_you_may_know()
returns table (id uuid, display_name text, username text, avatar jsonb, mutual bigint)
language sql stable security definer set search_path = ''
as $$
  with mine as (
    select case when f.requester = auth.uid() then f.addressee else f.requester end as friend
    from app.friendships f
    where f.status = 'accepted' and auth.uid() in (f.requester, f.addressee)
  ), theirs as (
    select case when f.requester = m.friend then f.addressee else f.requester end as person
    from mine m
    join app.friendships f on f.status = 'accepted' and m.friend in (f.requester, f.addressee)
  )
  select p.id, p.display_name, p.username, p.avatar, count(*) as mutual
  from theirs t
  join app.profiles p on p.id = t.person
  where auth.uid() is not null
    and t.person <> auth.uid()
    and not exists (
      select 1 from app.friendships x
      where (x.requester = auth.uid() and x.addressee = t.person)
         or (x.addressee = auth.uid() and x.requester = t.person)
    )
  group by p.id, p.display_name, p.username, p.avatar
  order by count(*) desc, p.display_name
  limit 20
$$;

-- Contacts on StepQ: the phone sends SHA-256 hashes of its contacts' email addresses
-- (lower-cased, trimmed), never the addresses, and gets back the people whose sign-in email
-- matches. At most 2,000 hashes a call and 50 people back.
create or replace function app.match_contacts(email_hashes text[])
returns table (id uuid, display_name text, username text, avatar jsonb)
language sql stable security definer set search_path = ''
as $$
  select p.id, p.display_name, p.username, p.avatar
  from auth.users u
  join app.profiles p on p.id = u.id
  where auth.uid() is not null
    and cardinality(email_hashes) <= 2000
    and u.id <> auth.uid()
    and u.email is not null
    and encode(sha256(convert_to(lower(trim(u.email)), 'UTF8')), 'hex') = any (email_hashes)
  order by p.display_name
  limit 50
$$;

revoke all on function app.search_people(text), app.people_you_may_know(),
  app.match_contacts(text[]) from public, anon;
grant execute on function app.search_people(text), app.people_you_may_know(),
  app.match_contacts(text[]) to authenticated;

-- ---------------------------------------------------------------------------------------
-- Paid QRuns
-- ---------------------------------------------------------------------------------------
alter table app.qruns add column if not exists price_inr integer not null default 0
  check (price_inr between 0 and 100000);
grant insert (price_inr), update (price_inr) on app.qruns to authenticated;

-- Is the caller a StepQ admin (the website's admin list, stepq.is_admin())? The app uses it
-- only to show the price field; the trigger below is what enforces it. False when the
-- website's admin schema isn't installed.
create or replace function app.i_am_admin() returns boolean
language plpgsql stable security definer set search_path = ''
as $$
declare ok boolean := false;
begin
  if auth.uid() is not null and to_regprocedure('stepq.is_admin()') is not null then
    execute 'select stepq.is_admin()' into ok;
  end if;
  return coalesce(ok, false);
end $$;
revoke all on function app.i_am_admin() from public, anon;
grant execute on function app.i_am_admin() to authenticated;

-- Members can only post free QRuns. A paid one (price above 0) needs a StepQ admin. The
-- service role has no auth.uid() and is not checked.
create or replace function app.check_qrun_price() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if new.price_inr > 0 and auth.uid() is not null and not app.i_am_admin() then
    raise exception 'Only StepQ can post paid QRuns.' using errcode = '42501';
  end if;
  return new;
end $$;

drop trigger if exists qruns_check_price on app.qruns;
create trigger qruns_check_price before insert or update of price_inr on app.qruns
  for each row execute function app.check_qrun_price();

-- qruns is readable by every signed-in user, so the app reads price_inr straight from the
-- table next to app.qrun_feed(); the feed's shape stays the same.
