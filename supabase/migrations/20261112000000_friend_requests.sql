-- A13: friend requests waiting for me, with the sender's public fields.
-- profiles_read only shows people the caller already shares a friendship or group with, so a
-- pending request would otherwise arrive without a name. This returns exactly the senders of
-- requests addressed to the caller, nothing else.
create or replace function app.friend_requests()
returns table (id uuid, display_name text, username text, avatar jsonb, created_at timestamptz)
language sql stable security definer set search_path = ''
as $$
  select p.id, p.display_name, p.username, p.avatar, f.created_at
  from app.friendships f
  join app.profiles p on p.id = f.requester
  where f.addressee = auth.uid() and f.status = 'pending'
  order by f.created_at desc
$$;

revoke all on function app.friend_requests() from public, anon;
grant execute on function app.friend_requests() to authenticated;
