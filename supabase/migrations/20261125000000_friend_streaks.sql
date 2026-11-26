-- Nov 24 rule change: streaks are per friend. There is no overall streak any more.
--   * A streak with a friend grows on each day I share my step count with that friend, and
--     resets after a day without a share to them. Each friend's streak runs on its own, so I
--     can have several going on the same day.
--   * app.share_streak() (the overall streak) stays for older app builds; new builds don't call it.
--
-- Additive and re-runnable; no data is changed.

-- My streak with each friend I've shared with: days in a row, counting back from today if I've
-- shared with them today, or from yesterday otherwise (so it doesn't drop to 0 every morning).
-- Only current friends are listed.
create or replace function app.friend_streaks(p_today date)
returns table (friend_id uuid, streak integer, shared_today boolean)
language sql
stable
security definer
set search_path = ''
as $$
  with days as (
    select distinct s.to_user as friend, s.day
    from app.step_shares s
    where s.from_user = auth.uid() and s.day <= p_today and s.day > p_today - 400
  ), per_friend as (
    select friend,
           bool_or(day = p_today) as shared_today,
           case when bool_or(day = p_today) then p_today else p_today - 1 end as start
    from days
    group by friend
  ), numbered as (
    select d.friend, f.start - d.day as back,
           row_number() over (partition by d.friend order by d.day desc) - 1 as rn
    from days d
    join per_friend f on f.friend = d.friend
    where d.day <= f.start
  )
  select f.friend,
         (select count(*)::integer from numbered n where n.friend = f.friend and n.back = n.rn),
         f.shared_today
  from per_friend f
  where auth.uid() is not null and app.are_friends(auth.uid(), f.friend)
$$;

revoke all on function app.friend_streaks(date) from public, anon;
grant execute on function app.friend_streaks(date) to authenticated;
