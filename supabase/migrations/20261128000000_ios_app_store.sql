-- The iOS app, for App Store review:
--
-- 1. A word filter for community posts (App Review Guideline 1.2: "a method for filtering
--    objectionable material from being posted"). Terms live in app.blocked_terms, which only
--    admins can change. A post or reply containing one is refused with hint 'content_filter'.
-- 2. StepQ Pro bought in the App Store (Guideline 3.1.1). The appstore-verify Edge Function
--    checks Apple's signed transaction and calls app.apply_store_subscription() with the
--    service role. Members can read their own rows; nobody can write them from the app.
--    Pro from the App Store ends when the subscription ends; Pro an admin gave is left alone.
--
-- Additive and re-runnable. No member data is changed.

-- ---------------------------------------------------------------------------------------
-- 1. Word filter
-- ---------------------------------------------------------------------------------------
create table if not exists app.blocked_terms (
  term text primary key check (term = lower(btrim(term)) and char_length(term) between 2 and 60),
  added_at timestamptz not null default now(),
  added_by uuid
);
alter table app.blocked_terms enable row level security;
revoke all on app.blocked_terms from public, anon, authenticated;

-- A starting list: slurs, sexual terms and common abuse in English and Hinglish. Admins add or
-- remove terms in the dashboard (stepq.is_admin()). Whole words only, so "class" never matches
-- "ass". Seeded once; existing rows are kept.
insert into app.blocked_terms (term) values
  ('fuck'), ('fucking'), ('fucker'), ('motherfucker'), ('shit'), ('bullshit'), ('bitch'), ('bastard'),
  ('cunt'), ('dick'), ('pussy'), ('asshole'), ('slut'), ('whore'), ('porn'), ('nude'), ('nudes'),
  ('rape'), ('rapist'), ('nigger'), ('nigga'), ('faggot'), ('fag'), ('retard'), ('tranny'),
  ('kill yourself'), ('kys'),
  ('chutiya'), ('chutiye'), ('madarchod'), ('behenchod'), ('bhenchod'), ('bsdk'), ('bhosdike'),
  ('bhosadi'), ('gandu'), ('randi'), ('harami'), ('lund'), ('lauda'), ('loda'), ('mc'), ('bc')
on conflict (term) do nothing;

-- True when [body] contains a blocked term as a whole word (case-insensitive).
create or replace function app.text_is_allowed(body text) returns boolean
language sql stable security definer set search_path = ''
as $$
  select not exists (
    select 1 from app.blocked_terms t
    where lower(coalesce(body, '')) ~ ('\m' || regexp_replace(t.term, '([.*+?^${}()|\[\]\\])', '\\\1', 'g') || '\M')
  )
$$;
revoke all on function app.text_is_allowed(text) from public, anon;
grant execute on function app.text_is_allowed(text) to authenticated;

create or replace function app.check_post_text() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if not app.text_is_allowed(new.body) then
    raise exception 'That has words StepQ doesn''t allow in the community. Please change it and try again.'
      using errcode = '22023', hint = 'content_filter';
  end if;
  return new;
end $$;

drop trigger if exists posts_check_text on app.posts;
create trigger posts_check_text before insert or update of body on app.posts
  for each row execute function app.check_post_text();

-- ---------------------------------------------------------------------------------------
-- 2. App Store subscriptions
-- ---------------------------------------------------------------------------------------
-- Where a member's Pro came from: 'admin' (the dashboard) or 'app_store'. Null for members
-- made Pro before this migration, which counts as admin.
alter table app.profiles add column if not exists pro_source text
  check (pro_source in ('admin', 'app_store'));

create table if not exists app.store_subscriptions (
  -- Apple's originalTransactionId: the same for every renewal of one subscription.
  original_transaction_id text primary key,
  user_id uuid not null references app.profiles (id) on delete cascade,
  product_id text not null,
  environment text not null check (environment in ('Production', 'Sandbox', 'Xcode', 'LocalTesting')),
  expires_at timestamptz,
  revoked_at timestamptz,
  auto_renew boolean,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);
create index if not exists store_subscriptions_user_idx on app.store_subscriptions (user_id);
alter table app.store_subscriptions enable row level security;
revoke all on app.store_subscriptions from public, anon, authenticated;
grant select on app.store_subscriptions to authenticated;

drop policy if exists store_subscriptions_read on app.store_subscriptions;
create policy store_subscriptions_read on app.store_subscriptions for select to authenticated
  using (user_id = auth.uid());

do $$
begin
  if to_regprocedure('stepq.is_admin()') is not null then
    drop policy if exists admins_read on app.store_subscriptions;
    create policy admins_read on app.store_subscriptions for select to authenticated using (stepq.is_admin());
    drop policy if exists admins_read on app.blocked_terms;
    create policy admins_read on app.blocked_terms for select to authenticated using (stepq.is_admin());
    drop policy if exists admins_write on app.blocked_terms;
    create policy admins_write on app.blocked_terms for all to authenticated
      using (stepq.is_admin()) with check (stepq.is_admin());
    grant select, insert, delete on app.blocked_terms to authenticated;
  end if;
end $$;

-- Records a verified App Store transaction for p_user and sets their plan from all of their
-- subscriptions. Only the appstore-verify Edge Function calls it, with the service role, after
-- checking Apple's signature. Returns the plan now in force.
--
-- One subscription belongs to one StepQ account: a transaction already linked to someone else
-- is refused, so a shared Apple ID can't hand Pro to a second account.
create or replace function app.apply_store_subscription(
  p_user uuid,
  p_original_transaction_id text,
  p_product_id text,
  p_environment text,
  p_expires_at timestamptz,
  p_revoked_at timestamptz default null,
  p_auto_renew boolean default null
) returns text
language plpgsql volatile security definer set search_path = ''
as $$
declare
  owner_id uuid;
  active boolean;
  p app.profiles%rowtype;
begin
  -- Tells app.mark_admin_pro_source() that this plan change comes from the App Store.
  perform set_config('stepq.store_sync', 'on', true);
  select * into p from app.profiles where id = p_user;
  if not found then
    raise exception 'Finish setting up your profile first.' using errcode = 'P0002';
  end if;
  select user_id into owner_id from app.store_subscriptions where original_transaction_id = p_original_transaction_id;
  if owner_id is not null and owner_id <> p_user then
    raise exception 'This App Store subscription belongs to another StepQ account.' using errcode = '42501';
  end if;

  insert into app.store_subscriptions as s
    (original_transaction_id, user_id, product_id, environment, expires_at, revoked_at, auto_renew)
  values (p_original_transaction_id, p_user, p_product_id, p_environment, p_expires_at, p_revoked_at, p_auto_renew)
  on conflict (original_transaction_id) do update
    set product_id = excluded.product_id,
        environment = excluded.environment,
        -- Notifications can arrive out of order: never move the end date backwards unless revoked.
        expires_at = case when excluded.revoked_at is not null then excluded.expires_at
                          else greatest(s.expires_at, excluded.expires_at) end,
        revoked_at = excluded.revoked_at,
        auto_renew = coalesce(excluded.auto_renew, s.auto_renew),
        updated_at = now();

  select exists (
    select 1 from app.store_subscriptions
    where user_id = p_user and revoked_at is null and expires_at > now()
  ) into active;

  if active and p.plan <> 'pro' then
    update app.profiles set plan = 'pro', pro_since = coalesce(pro_since, now()), pro_source = 'app_store'
    where id = p_user;
  elsif not active and p.plan = 'pro' and p.pro_source = 'app_store' then
    update app.profiles set plan = 'free', pro_since = null, pro_source = null where id = p_user;
    -- Drop what Free doesn't include, as app.admin_set_member does.
    update app.profiles
    set theme = case when app.has_feature(id, 'change_theme') then theme else 'leaf' end,
        photo_path = case when app.has_feature(id, 'upload_photo') then photo_path end,
        bff_id = case when app.has_feature(id, 'pin_bff') then bff_id end,
        qrun_host = qrun_host and app.has_feature(id, 'plan_qrun')
    where id = p_user;
  end if;

  perform set_config('stepq.store_sync', 'off', true);
  return (select plan from app.profiles where id = p_user);
end $$;
revoke all on function app.apply_store_subscription(uuid, text, text, text, timestamptz, timestamptz, boolean)
  from public, anon, authenticated;
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'service_role') then
    grant execute on function app.apply_store_subscription(uuid, text, text, text, timestamptz, timestamptz, boolean) to service_role;
    grant usage on schema app to service_role;
  end if;
end $$;

-- An admin making someone Pro (or Free) in the dashboard marks it as theirs, so a lapsed App
-- Store subscription never takes away Pro an admin gave.
create or replace function app.mark_admin_pro_source() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if new.plan is distinct from old.plan and coalesce(current_setting('stepq.store_sync', true), '') <> 'on' then
    new.pro_source := case when new.plan = 'pro' then 'admin' end;
  end if;
  return new;
end $$;
drop trigger if exists profiles_pro_source on app.profiles;
create trigger profiles_pro_source before update of plan on app.profiles
  for each row execute function app.mark_admin_pro_source();
