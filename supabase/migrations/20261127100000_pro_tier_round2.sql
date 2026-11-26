-- StepQ Pro, round 2 (requested 25 Nov):
--   * Free members can't start Community posts (they can still like and reply).
--   * New posts from anyone whose plan has `post_approval` wait for an admin. Until approved,
--     only the author sees them. Replies are never held. Off for Pro by default (Pro posts go
--     live straight away); an admin can switch it on per plan from the admin panel.
--   * Insights history for Free: 1 year.
--   * A setting for how often the upgrade page pops up for Free members.
-- Additive and safe to re-run. Apply after 20261126000000_pro_tier.sql.

-- ---------------------------------------------------------------------------------------
-- Plan values (admins can still change them in the admin panel).
-- ---------------------------------------------------------------------------------------
update app.plan_features set free_value = 0, updated_at = now() where key = 'community_posts' and free_value is distinct from 0;
update app.plan_features set free_value = 365, updated_at = now() where key = 'analysis_days' and free_value is distinct from 365;
insert into app.plan_features (key, label, kind, free_value, pro_value, unit, sort) values
  ('post_approval', 'New Community posts wait for admin approval', 'switch', 1, 0, null, 135)
on conflict (key) do nothing;
-- How often the full-screen "Upgrade to StepQ Pro" page pops up for Free members, in days
-- (0 = never). The Pro value is unused. The app also opens the page from every limit.
insert into app.plan_features (key, label, kind, free_value, pro_value, unit, sort) values
  ('upgrade_popup_days', 'Upgrade page pops up every (0 = never)', 'limit', 3, 0, 'days', 140)
on conflict (key) do nothing;
-- Pro posts go live without approval (requested 25 Nov, after the first draft of this file).
update app.plan_features set pro_value = 0, updated_at = now() where key = 'post_approval' and pro_value = 1
  and updated_at < '2026-11-26';

-- ---------------------------------------------------------------------------------------
-- Post approval.
-- ---------------------------------------------------------------------------------------
alter table app.posts add column if not exists status text not null default 'approved';
alter table app.posts add column if not exists reviewed_at timestamptz;
alter table app.posts add column if not exists reviewed_by uuid;
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'posts_status_check' and conrelid = 'app.posts'::regclass) then
    alter table app.posts add constraint posts_status_check check (status in ('pending', 'approved', 'rejected'));
  end if;
end $$;
create index if not exists posts_pending_idx on app.posts (created_at) where status = 'pending';

-- Others only see approved posts; I always see my own.
drop policy if exists posts_read on app.posts;
create policy posts_read on app.posts for select to authenticated
  using (auth.uid() is not null and (status = 'approved' or author = auth.uid()));

-- The cards behind the feed and threads hide unapproved posts from everyone but the author.
create or replace function app.post_cards(p_ids uuid[])
returns table (
  id uuid,
  author_id uuid,
  author_name text,
  author_username text,
  author_avatar jsonb,
  parent_id uuid,
  parent_username text,
  body text,
  kind text,
  day date,
  steps integer,
  goal integer,
  created_at timestamptz,
  like_count bigint,
  reply_count bigint,
  repost_count bigint,
  liked boolean,
  reposted boolean
)
language sql stable security definer set search_path = ''
as $f$
  select p.id, p.author, a.display_name, a.username, a.avatar, p.parent_id, pa.username,
         p.body, p.kind, p.day, p.steps, p.goal, p.created_at,
         (select count(*) from app.post_likes l where l.post_id = p.id),
         (select count(*) from app.posts r where r.parent_id = p.id
            and (r.status = 'approved' or r.author = auth.uid())
            and (r.author = auth.uid() or (select count(*) from app.post_reports x where x.post_id = r.id) < 3)),
         (select count(*) from app.post_reposts s where s.post_id = p.id),
         exists (select 1 from app.post_likes l where l.post_id = p.id and l.user_id = auth.uid()),
         exists (select 1 from app.post_reposts s where s.post_id = p.id and s.user_id = auth.uid())
  from app.posts p
  join app.profiles a on a.id = p.author
  left join app.posts pp on pp.id = p.parent_id
  left join app.profiles pa on pa.id = pp.author
  where auth.uid() is not null
    and p.id = any (p_ids)
    and (p.status = 'approved' or p.author = auth.uid())
    and (p.author = auth.uid() or (select count(*) from app.post_reports x where x.post_id = p.id) < 3)
$f$;
revoke all on function app.post_cards(uuid[]) from public, anon, authenticated;

-- Nobody can like or repost a post others can't see yet.
create or replace function app.check_post_visible() returns trigger
language plpgsql security definer set search_path = ''
as $f$
begin
  if not exists (select 1 from app.posts p where p.id = new.post_id and (p.status = 'approved' or p.author = auth.uid())) then
    raise exception 'That post isn''t visible yet.' using errcode = '42501';
  end if;
  return new;
end $f$;
drop trigger if exists check_post_visible on app.post_likes;
create trigger check_post_visible before insert on app.post_likes for each row execute function app.check_post_visible();
drop trigger if exists check_post_visible on app.post_reposts;
create trigger check_post_visible before insert on app.post_reposts for each row execute function app.check_post_visible();

-- My posts still waiting for an admin (the app marks them "Waiting for approval").
create or replace function app.my_pending_posts() returns setof uuid
language sql stable security definer set search_path = ''
as $f$
  select id from app.posts where author = auth.uid() and status = 'pending'
$f$;
revoke all on function app.my_pending_posts() from public, anon;
grant execute on function app.my_pending_posts() to authenticated;

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
  -- New threads wait for an admin when the plan says so; replies never do.
  st text := 'approved';
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
  if p_parent is not null and not exists (
    select 1 from app.posts where id = p_parent and (status = 'approved' or author = me)
  ) then
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

  if p_parent is null and app.has_feature(me, 'post_approval') then
    st := 'pending';
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
    insert into app.posts (author, body, kind, day, steps, goal, status)
    values (me, b, 'achievement', p_share_day, coalesce(s, 0), g, st)
    returning id into new_id;
  else
    insert into app.posts (author, parent_id, body, status) values (me, p_parent, b, st) returning id into new_id;
  end if;
  if p_parent is null then
    insert into app.usage_events (user_id, kind) values (me, 'community_post');
  end if;
  return new_id;
end $$;

revoke all on function app.create_post(text, uuid, date) from public, anon;
grant execute on function app.create_post(text, uuid, date) to authenticated;

-- Admins approve or reject a waiting post. A rejected post stays visible to its author only.
create or replace function app.admin_review_post(p_post uuid, p_approve boolean)
returns void
language plpgsql volatile security definer set search_path = ''
as $f$
declare
  me uuid := app.admin_check();
begin
  update app.posts
     set status = case when p_approve then 'approved' else 'rejected' end,
         reviewed_at = now(), reviewed_by = me,
         -- Approved posts go to the top of the feed as if posted now.
         created_at = case when p_approve then now() else created_at end
   where id = p_post and status = 'pending';
  if not found then
    raise exception 'That post isn''t waiting for review.' using errcode = 'P0002';
  end if;
  insert into app.admin_log (admin_id, action, target, detail)
  values (me, case when p_approve then 'approve_post' else 'reject_post' end, p_post::text, '{}'::jsonb);
end $f$;
revoke all on function app.admin_review_post(uuid, boolean) from public, anon;
grant execute on function app.admin_review_post(uuid, boolean) to authenticated;
