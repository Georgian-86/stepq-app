-- Community: a Threads-style feed in the Friends tab, in place of Groups.
--
-- Anyone signed in can post a thought or an achievement (today's steps, read from their own
-- app.daily_steps row, so the number can't be made up), like, reply (replies are posts with a
-- parent, so a reply can have replies of its own), repost and report.
--
-- Who sees what:
--   * Posts are readable by every signed-in user. Profiles of strangers stay hidden by RLS, so
--     the feed functions return only the author's display name, username and avatar.
--   * Likes, reposts and reports are readable by the person who made them (and admins);
--     everyone else only sees counts, through the feed functions.
--   * Writing a post goes only through app.create_post(), which checks the length, the
--     rate limit and fills in achievement numbers. Likes and reposts are plain inserts that
--     RLS limits to yourself. You can delete your own posts, likes and reposts.
--   * A post that 3 different people report is hidden from everyone but its author.
--   * anon gets nothing.
--
-- Additive and re-runnable. No existing data is changed.

create table if not exists app.posts (
  id uuid primary key default gen_random_uuid(),
  author uuid not null references app.profiles (id) on delete cascade,
  parent_id uuid references app.posts (id) on delete cascade,
  body text not null default '',
  kind text not null default 'thought' check (kind in ('thought', 'achievement')),
  -- Achievements only: the author's steps and goal for `day`, copied from app.daily_steps.
  day date,
  steps integer check (steps between 0 and 200000),
  goal integer check (goal between 1000 and 100000),
  created_at timestamptz not null default now(),
  check (char_length(body) <= 500),
  check (kind = 'achievement' or char_length(btrim(body)) >= 1),
  check (kind = 'thought' or (parent_id is null and day is not null and steps is not null))
);
create index if not exists posts_feed_idx on app.posts (created_at desc) where parent_id is null;
create index if not exists posts_parent_idx on app.posts (parent_id, created_at);
create index if not exists posts_author_idx on app.posts (author, created_at desc);

create table if not exists app.post_likes (
  post_id uuid not null references app.posts (id) on delete cascade,
  user_id uuid not null references app.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);
create index if not exists post_likes_user_idx on app.post_likes (user_id);

create table if not exists app.post_reposts (
  post_id uuid not null references app.posts (id) on delete cascade,
  user_id uuid not null references app.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);
create index if not exists post_reposts_feed_idx on app.post_reposts (created_at desc);

create table if not exists app.post_reports (
  id bigint generated always as identity primary key,
  post_id uuid not null references app.posts (id) on delete cascade,
  reporter uuid not null references app.profiles (id) on delete cascade,
  reason text not null default '' check (char_length(reason) <= 200),
  created_at timestamptz not null default now(),
  unique (post_id, reporter)
);

alter table app.posts enable row level security;
alter table app.post_likes enable row level security;
alter table app.post_reposts enable row level security;
alter table app.post_reports enable row level security;

revoke all on app.posts, app.post_likes, app.post_reposts, app.post_reports from public, anon, authenticated;
grant select, delete on app.posts to authenticated;
grant select, delete on app.post_likes to authenticated;
grant insert (post_id, user_id) on app.post_likes to authenticated;
grant select, delete on app.post_reposts to authenticated;
grant insert (post_id, user_id) on app.post_reposts to authenticated;
grant select on app.post_reports to authenticated;
grant insert (post_id, reporter, reason) on app.post_reports to authenticated;

drop policy if exists posts_read on app.posts;
create policy posts_read on app.posts for select to authenticated using (auth.uid() is not null);
drop policy if exists posts_delete on app.posts;
create policy posts_delete on app.posts for delete to authenticated using (author = auth.uid());

drop policy if exists likes_read on app.post_likes;
create policy likes_read on app.post_likes for select to authenticated using (user_id = auth.uid());
drop policy if exists likes_insert on app.post_likes;
create policy likes_insert on app.post_likes for insert to authenticated with check (user_id = auth.uid());
drop policy if exists likes_delete on app.post_likes;
create policy likes_delete on app.post_likes for delete to authenticated using (user_id = auth.uid());

drop policy if exists reposts_read on app.post_reposts;
create policy reposts_read on app.post_reposts for select to authenticated using (user_id = auth.uid());
drop policy if exists reposts_insert on app.post_reposts;
create policy reposts_insert on app.post_reposts for insert to authenticated with check (user_id = auth.uid());
drop policy if exists reposts_delete on app.post_reposts;
create policy reposts_delete on app.post_reposts for delete to authenticated using (user_id = auth.uid());

drop policy if exists reports_read on app.post_reports;
create policy reports_read on app.post_reports for select to authenticated using (reporter = auth.uid());
drop policy if exists reports_insert on app.post_reports;
create policy reports_insert on app.post_reports for insert to authenticated with check (reporter = auth.uid());

-- The stepq.in admin dashboard reads every row (read only), like the other app tables.
do $$
declare t text;
begin
  if to_regprocedure('stepq.is_admin()') is not null then
    foreach t in array array['posts', 'post_likes', 'post_reposts', 'post_reports'] loop
      execute format('drop policy if exists admins_read on app.%I', t);
      execute format('create policy admins_read on app.%I for select to authenticated using (stepq.is_admin())', t);
    end loop;
  end if;
end $$;

-- ---------------------------------------------------------------------------------------
-- Cards: what the app shows for each post. Internal; the feed functions below call it.
-- ---------------------------------------------------------------------------------------
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
as $$
  select p.id, p.author, a.display_name, a.username, a.avatar, p.parent_id, pa.username,
         p.body, p.kind, p.day, p.steps, p.goal, p.created_at,
         (select count(*) from app.post_likes l where l.post_id = p.id),
         (select count(*) from app.posts r where r.parent_id = p.id
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
    and (p.author = auth.uid() or (select count(*) from app.post_reports x where x.post_id = p.id) < 3)
$$;
revoke all on function app.post_cards(uuid[]) from public, anon, authenticated;

-- The feed, newest first: top-level posts plus reposts (shown as "Riya reposted").
--   p_scope: 'everyone', 'friends' (me and my friends) or 'mine'.
--   p_before: for the next page, the entry_at of the last entry already shown.
create or replace function app.community_feed(p_scope text default 'everyone', p_before timestamptz default null, p_limit integer default 30)
returns table (
  entry_at timestamptz,
  reposted_by text,
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
language plpgsql stable security definer set search_path = ''
as $$
declare me uuid := auth.uid();
begin
  if me is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  return query
  with entries as (
    select p.id as post_id, p.created_at as at, null::uuid as reposter
    from app.posts p
    where p.parent_id is null
      and (p_before is null or p.created_at < p_before)
      and case coalesce(p_scope, 'everyone')
            when 'mine' then p.author = me
            when 'friends' then p.author = me or app.are_friends(me, p.author)
            else true
          end
    union all
    select s.post_id, s.created_at, s.user_id
    from app.post_reposts s
    join app.posts p on p.id = s.post_id
    where s.user_id <> p.author
      and (p_before is null or s.created_at < p_before)
      and case coalesce(p_scope, 'everyone')
            when 'mine' then s.user_id = me
            when 'friends' then s.user_id = me or app.are_friends(me, s.user_id)
            else true
          end
  ), page as (
    select * from entries order by at desc limit least(greatest(coalesce(p_limit, 30), 1), 50)
  )
  select e.at, rp.display_name, c.*
  from page e
  join app.post_cards(array(select post_id from page)) c on c.id = e.post_id
  left join app.profiles rp on rp.id = e.reposter
  order by e.at desc;
end $$;

-- One post's page: the posts it replies to (depth < 0, oldest first), the post (depth 0)
-- and its replies (depth 1, oldest first).
create or replace function app.post_thread(p_post uuid)
returns table (
  depth integer,
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
language plpgsql stable security definer set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  return query
  with recursive up as (
    select p.id, p.parent_id, 0 as d from app.posts p where p.id = p_post
    union all
    select p.id, p.parent_id, up.d - 1 from app.posts p join up on p.id = up.parent_id where up.d > -20
  ), rows as (
    select up.id, up.d from up
    union all
    select r.id, 1 from app.posts r where r.parent_id = p_post
  )
  select rows.d, c.*
  from rows
  join app.post_cards(array(select rows.id from rows)) c on c.id = rows.id
  order by rows.d, c.created_at;
end $$;

-- Posts a thought, a reply (p_parent) or an achievement (p_share_day: shares my steps for
-- that day from app.daily_steps). Returns the new post's id.
create or replace function app.create_post(p_body text, p_parent uuid default null, p_share_day date default null)
returns uuid
language plpgsql volatile security definer set search_path = ''
as $$
declare
  me uuid := auth.uid();
  b text := btrim(coalesce(p_body, ''));
  s integer;
  g integer;
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
  return new_id;
end $$;

revoke all on function app.community_feed(text, timestamptz, integer), app.post_thread(uuid),
  app.create_post(text, uuid, date) from public, anon;
grant execute on function app.community_feed(text, timestamptz, integer), app.post_thread(uuid),
  app.create_post(text, uuid, date) to authenticated;
