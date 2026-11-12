-- A18/A21: the QRun list and event planner.
--
-- QRuns and their participants are readable by every signed-in user, but profiles are not
-- (a stranger's profile is hidden by RLS), so the card couldn't say who posted it. This
-- returns each QRun with only its host's display name and avatar, how many are going, and
-- whether the caller has joined. Nothing else about the host or the people going is exposed.
--
-- Lists QRuns that haven't started yet (or started in the last 3 hours), plus up to 60 days of
-- past QRuns the caller hosted or joined, for the planner's Past tab.
create or replace function app.qrun_feed()
returns table (
  id uuid,
  title text,
  place_name text,
  lat double precision,
  lng double precision,
  starts_at timestamptz,
  created_at timestamptz,
  created_by uuid,
  host_name text,
  host_avatar jsonb,
  going bigint,
  joined boolean
)
language sql stable security definer set search_path = ''
as $$
  select q.id, q.title, q.place_name, q.lat, q.lng, q.starts_at, q.created_at, q.created_by,
         p.display_name, p.avatar,
         (select count(*) from app.qrun_participants x where x.qrun_id = q.id),
         exists (select 1 from app.qrun_participants x where x.qrun_id = q.id and x.user_id = auth.uid())
  from app.qruns q
  join app.profiles p on p.id = q.created_by
  where auth.uid() is not null
    and (
      q.starts_at > now() - interval '3 hours'
      or (q.starts_at > now() - interval '60 days'
          and (q.created_by = auth.uid()
               or exists (select 1 from app.qrun_participants x where x.qrun_id = q.id and x.user_id = auth.uid())))
    )
  order by q.starts_at
  limit 200
$$;

revoke all on function app.qrun_feed() from public, anon;
grant execute on function app.qrun_feed() to authenticated;
