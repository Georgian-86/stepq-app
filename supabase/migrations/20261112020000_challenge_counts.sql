-- A22/A24: how many people joined each open challenge. entries_read shows only the caller's
-- own entries, so the count comes from here; it returns numbers only, never who joined.
create or replace function app.challenge_counts()
returns table (challenge_id uuid, joined bigint)
language sql stable security definer set search_path = ''
as $$
  select e.challenge_id, count(*)
  from app.challenge_entries e
  join app.challenges c on c.id = e.challenge_id
  where auth.uid() is not null and c.ends_at > now() - interval '30 days'
  group by e.challenge_id
$$;

revoke all on function app.challenge_counts() from public, anon;
grant execute on function app.challenge_counts() to authenticated;
