-- Keeps both rules for community posts. 20261124000000_privacy_compliance.sql hides posts and
-- replies from people you blocked (or who blocked you); 20261127100000_pro_tier_round2.sql
-- hides posts still waiting for approval. Round 2 redefined app.post_cards without the block
-- filter, so blocking stopped working. This puts it back. Additive and re-runnable.
-- Run after both of those files.

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
            and (r.author = auth.uid() or (select count(*) from app.post_reports x where x.post_id = r.id) < 3)
            and not app.is_blocked(auth.uid(), r.author)),
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
    and not app.is_blocked(auth.uid(), p.author)
$f$;
revoke all on function app.post_cards(uuid[]) from public, anon, authenticated;
