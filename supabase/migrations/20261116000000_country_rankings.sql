-- A22/A23: weekly and monthly rankings of everyone in your country, not only friends.
--
-- Adds app.profiles.country (ISO 3166 code, set by the app from the phone's SIM or region) and
-- app.country_ranking(), which ranks everyone in the caller's country by total steps between two
-- days. A stranger's profile and daily rows stay hidden by RLS; this returns only what a
-- leaderboard shows: display name, avatar, the period total and the rank. Additive and
-- re-runnable; changes no existing data.

alter table app.profiles add column if not exists country text;
do $$ begin
  alter table app.profiles add constraint profiles_country_check check (country ~ '^[A-Z]{2}$');
exception when duplicate_object then null;
end $$;
create index if not exists profiles_country_idx on app.profiles (country);

grant insert (country), update (country) on app.profiles to authenticated;

create or replace function app.country_ranking(from_day date, to_day date, max_rows integer default 50)
returns table (
  rank bigint,
  id uuid,
  display_name text,
  avatar jsonb,
  steps bigint,
  is_me boolean,
  ranked bigint
)
language sql stable security definer set search_path = ''
as $$
  with me as (
    select p.id, p.country from app.profiles p where p.id = auth.uid()
  ),
  totals as (
    select p.id, p.display_name, p.avatar, coalesce(sum(d.steps), 0)::bigint as steps
    from app.profiles p
    join me on (p.country = me.country or p.id = me.id)
    left join app.daily_steps d
      on d.user_id = p.id and d.day between from_day and to_day
    where to_day >= from_day and to_day - from_day <= 40
    group by p.id, p.display_name, p.avatar
  ),
  ranked as (
    select rank() over (order by t.steps desc) as rank, t.*, count(*) over () as ranked
    from totals t
    where t.steps > 0 or t.id = (select id from me)
  )
  select r.rank, r.id, r.display_name, r.avatar, r.steps, r.id = (select id from me), r.ranked
  from ranked r
  where r.rank <= least(greatest(max_rows, 1), 100) or r.id = (select id from me)
  order by r.rank, r.display_name
$$;

revoke all on function app.country_ranking(date, date, integer) from public, anon;
grant execute on function app.country_ranking(date, date, integer) to authenticated;
