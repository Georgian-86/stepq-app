-- Push notifications: the app registers this phone's FCM token for the signed-in user.
--
-- app.register_push_token(token, platform) stores the token against auth.uid(). A token belongs
-- to one phone, so when someone else signs in on that phone the row moves to them; plain RLS
-- can't do that (the old row is not theirs to update), hence security definer. It only ever
-- writes the caller's own id, and reads nothing back.
--
-- Sending happens on stepq.in's server (its own database connection), never from the app.
-- Additive and re-runnable; changes no existing data.

create or replace function app.register_push_token(p_token text, p_platform text default 'android')
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  if p_token is null or length(p_token) not between 20 and 4096 then
    raise exception 'Not a push token.' using errcode = '22023';
  end if;
  insert into app.push_tokens (token, user_id, platform, updated_at)
  values (p_token, auth.uid(), coalesce(p_platform, 'android'), now())
  on conflict (token) do update
    set user_id = excluded.user_id, platform = excluded.platform, updated_at = now();
end
$$;

revoke all on function app.register_push_token(text, text) from public, anon;
grant execute on function app.register_push_token(text, text) to authenticated;

create index if not exists push_tokens_user_idx on app.push_tokens (user_id);
