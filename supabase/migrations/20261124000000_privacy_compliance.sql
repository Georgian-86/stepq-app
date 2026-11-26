-- Launch compliance (India DPDP Act 2023 and DPDP Rules 2025, IT Rules 2021, Google Play):
--
-- 1. Age gate and parental consent: new profiles must be 13 or older. DPDP treats anyone under
--    18 as a child, whose data needs verifiable parental consent (s.9, DPDP Rules rule 10). For
--    13 to 17 year olds a parent answers on stepq.in (the website writes app.parental_consents
--    with its own connection); until a parent grants consent, the account can't write anything
--    but its profile. Existing rows are not checked or changed.
-- 2. app.consents: a log of what each person agreed to (terms and privacy notice, by version)
--    and when, and when they withdrew it. Readable by its owner and admins; written only
--    through app.record_consent().
-- 3. app.export_my_data(): everything StepQ holds about the caller, as one JSON document, for the
--    DPDP right to access (and GDPR portability). Only the caller's own rows.
-- 4. app.user_blocks: block someone in the community. Their posts disappear from your feed and
--    threads, and yours from theirs. Only the blocker can see or remove a block.
-- 5. Moderation: admins (stepq.admins) can remove any post, so a grievance or a lawful order can
--    be acted on within the IT Rules' deadlines.
-- 6. app.deleted_accounts: IT Rules 2021 rule 3(1)(h) asks an intermediary to keep a user's
--    registration details for 180 days after they cancel their registration. delete_my_account()
--    now copies only the id, email, name and username here before deleting everything else.
--    Admins can read it; nobody else. Rows older than 180 days are for the owner to purge
--    (see /mnt/project-files/supabase-to-run/ retention SQL); nothing here deletes data by itself.
--
-- Additive and re-runnable. No existing data is changed.

-- ---------------------------------------------------------------------------------------
-- 1. 13+ for new accounts
-- ---------------------------------------------------------------------------------------
create or replace function app.check_min_age() returns trigger
language plpgsql set search_path = ''
as $$
begin
  if new.birthdate is not null and new.birthdate > (current_date - interval '13 years')::date then
    raise exception 'You need to be 13 or older to use StepQ.' using errcode = '22023';
  end if;
  return new;
end
$$;
drop trigger if exists profiles_min_age on app.profiles;
create trigger profiles_min_age before insert on app.profiles
  for each row execute function app.check_min_age();

-- ---------------------------------------------------------------------------------------
-- 2. Consent log
-- ---------------------------------------------------------------------------------------
create table if not exists app.consents (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  purpose text not null check (purpose in ('terms_privacy', 'parental', 'community', 'contacts', 'location', 'health')),
  version text not null check (char_length(version) between 1 and 40),
  granted_at timestamptz not null default now(),
  withdrawn_at timestamptz
);
create index if not exists consents_user_idx on app.consents (user_id, purpose, granted_at desc);
alter table app.consents enable row level security;
revoke all on app.consents from public, anon, authenticated;
grant select on app.consents to authenticated;

drop policy if exists consents_read on app.consents;
create policy consents_read on app.consents for select to authenticated using (user_id = auth.uid());

-- Records that the caller agreed to `p_purpose` at `p_version` (p_granted) or withdraws every
-- open consent for that purpose (not p_granted).
create or replace function app.record_consent(p_purpose text, p_version text, p_granted boolean default true)
returns void
language plpgsql volatile security definer set search_path = ''
as $$
declare me uuid := auth.uid();
begin
  if me is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  if p_purpose = 'parental' then
    raise exception 'A parent gives this consent on stepq.in.' using errcode = '42501';
  end if;
  if p_granted then
    insert into app.consents (user_id, purpose, version) values (me, p_purpose, p_version);
  else
    update app.consents set withdrawn_at = now()
    where user_id = me and purpose = p_purpose and withdrawn_at is null;
  end if;
end
$$;
revoke all on function app.record_consent(text, text, boolean) from public, anon;
grant execute on function app.record_consent(text, text, boolean) to authenticated;

-- ---------------------------------------------------------------------------------------
-- 4. Blocks (created before the export below, which includes them)
-- ---------------------------------------------------------------------------------------
create table if not exists app.user_blocks (
  blocker uuid not null references app.profiles (id) on delete cascade,
  blocked uuid not null references app.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker, blocked),
  check (blocker <> blocked)
);
create index if not exists user_blocks_blocked_idx on app.user_blocks (blocked);
alter table app.user_blocks enable row level security;
revoke all on app.user_blocks from public, anon, authenticated;
grant select, delete on app.user_blocks to authenticated;
grant insert (blocker, blocked) on app.user_blocks to authenticated;

drop policy if exists blocks_read on app.user_blocks;
create policy blocks_read on app.user_blocks for select to authenticated using (blocker = auth.uid());
drop policy if exists blocks_insert on app.user_blocks;
create policy blocks_insert on app.user_blocks for insert to authenticated with check (blocker = auth.uid());
drop policy if exists blocks_delete on app.user_blocks;
create policy blocks_delete on app.user_blocks for delete to authenticated using (blocker = auth.uid());

-- True when either of a and b has blocked the other.
create or replace function app.is_blocked(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from app.user_blocks
    where (blocker = a and blocked = b) or (blocker = b and blocked = a)
  )
$$;
revoke all on function app.is_blocked(uuid, uuid) from public, anon, authenticated;

-- People I've blocked, for the "Blocked accounts" list.
create or replace function app.my_blocks()
returns table (user_id uuid, display_name text, username text, avatar jsonb, blocked_at timestamptz)
language sql stable security definer set search_path = ''
as $$
  select b.blocked, p.display_name, p.username, p.avatar, b.created_at
  from app.user_blocks b join app.profiles p on p.id = b.blocked
  where b.blocker = auth.uid()
  order by b.created_at desc
$$;
revoke all on function app.my_blocks() from public, anon;
grant execute on function app.my_blocks() to authenticated;

-- post_cards (community feed migration) again, now leaving out posts by anyone I've blocked or
-- who has blocked me. Every feed and thread function reads posts through it.
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
            and not app.is_blocked(auth.uid(), r.author)
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
    and not app.is_blocked(auth.uid(), p.author)
    and (p.author = auth.uid() or (select count(*) from app.post_reports x where x.post_id = p.id) < 3)
$$;
revoke all on function app.post_cards(uuid[]) from public, anon, authenticated;

-- ---------------------------------------------------------------------------------------
-- 3. Export my data
-- ---------------------------------------------------------------------------------------
create or replace function app.export_my_data()
returns jsonb
language plpgsql stable security definer set search_path = ''
as $$
declare me uuid := auth.uid();
begin
  if me is null then
    raise exception 'Sign in first.' using errcode = '42501';
  end if;
  return jsonb_build_object(
    'exported_at', now(),
    'account', (select jsonb_build_object('id', u.id, 'email', u.email, 'created_at', u.created_at,
                                          'last_sign_in_at', u.last_sign_in_at)
                from auth.users u where u.id = me),
    'profile', (select to_jsonb(p) from app.profiles p where p.id = me),
    'daily_steps', coalesce((select jsonb_agg(to_jsonb(d) order by d.day) from app.daily_steps d where d.user_id = me), '[]'),
    'friendships', coalesce((select jsonb_agg(to_jsonb(f)) from app.friendships f where me in (f.requester, f.addressee)), '[]'),
    'water_reminders', coalesce((select jsonb_agg(to_jsonb(w)) from app.water_reminders w where me in (w.from_user, w.to_user)), '[]'),
    'groups_created', coalesce((select jsonb_agg(to_jsonb(g)) from app.groups g where g.created_by = me), '[]'),
    'group_memberships', coalesce((select jsonb_agg(to_jsonb(m)) from app.group_members m where m.user_id = me), '[]'),
    'qruns_created', coalesce((select jsonb_agg(to_jsonb(q)) from app.qruns q where q.created_by = me), '[]'),
    'qruns_joined', coalesce((select jsonb_agg(to_jsonb(q)) from app.qrun_participants q where q.user_id = me), '[]'),
    'challenge_entries', coalesce((select jsonb_agg(to_jsonb(c)) from app.challenge_entries c where c.user_id = me), '[]'),
    'push_devices', (select count(*) from app.push_tokens t where t.user_id = me),
    'pro_waitlist', (select to_jsonb(w) from app.pro_waitlist w where w.user_id = me),
    'step_shares', coalesce((select jsonb_agg(to_jsonb(s)) from app.step_shares s where me in (s.from_user, s.to_user)), '[]'),
    'posts', coalesce((select jsonb_agg(to_jsonb(p) order by p.created_at) from app.posts p where p.author = me), '[]'),
    'post_likes', coalesce((select jsonb_agg(to_jsonb(l)) from app.post_likes l where l.user_id = me), '[]'),
    'post_reposts', coalesce((select jsonb_agg(to_jsonb(r)) from app.post_reposts r where r.user_id = me), '[]'),
    'post_reports', coalesce((select jsonb_agg(to_jsonb(r)) from app.post_reports r where r.reporter = me), '[]'),
    'blocks', coalesce((select jsonb_agg(to_jsonb(b)) from app.user_blocks b where b.blocker = me), '[]'),
    'consents', coalesce((select jsonb_agg(to_jsonb(c) order by c.granted_at) from app.consents c where c.user_id = me), '[]'),
    'parental_consent', case when to_regclass('app.parental_consents') is null then null else
      (select jsonb_build_object('parent_name', c.parent_name, 'parent_email', c.parent_email, 'status', c.status,
                                 'requested_at', c.requested_at, 'responded_at', c.responded_at)
       from app.parental_consents c where c.child_id = me) end
  );
end
$$;
revoke all on function app.export_my_data() from public, anon;
grant execute on function app.export_my_data() to authenticated;

-- ---------------------------------------------------------------------------------------
-- 5. Admin read access to the new tables, and admins can remove posts
-- ---------------------------------------------------------------------------------------
do $$
declare t text;
begin
  if to_regprocedure('stepq.is_admin()') is not null then
    foreach t in array array['consents', 'user_blocks'] loop
      execute format('drop policy if exists admins_read on app.%I', t);
      execute format('create policy admins_read on app.%I for select to authenticated using (stepq.is_admin())', t);
    end loop;
    drop policy if exists admins_delete on app.posts;
    create policy admins_delete on app.posts for delete to authenticated using (stepq.is_admin());
  end if;
end $$;

-- ---------------------------------------------------------------------------------------
-- 6. Registration details kept 180 days after an account is deleted (IT Rules 3(1)(h))
-- ---------------------------------------------------------------------------------------
create table if not exists app.deleted_accounts (
  user_id uuid primary key,
  email text,
  display_name text,
  username text,
  signed_up_at timestamptz,
  deleted_at timestamptz not null default now()
);
create index if not exists deleted_accounts_deleted_at on app.deleted_accounts (deleted_at);
alter table app.deleted_accounts enable row level security;
revoke all on app.deleted_accounts from public, anon, authenticated;
grant select on app.deleted_accounts to authenticated;
do $$
begin
  if to_regprocedure('stepq.is_admin()') is not null then
    drop policy if exists admins_read on app.deleted_accounts;
    create policy admins_read on app.deleted_accounts for select to authenticated using (stepq.is_admin());
  end if;
end $$;

-- delete_my_account() as before (groups handed on, everything else cascades), now archiving the
-- registration details first.
create or replace function app.delete_my_account()
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  me uuid := auth.uid();
  g record;
  heir uuid;
begin
  if me is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;

  insert into app.deleted_accounts (user_id, email, display_name, username, signed_up_at)
  select u.id, u.email, p.display_name, p.username, u.created_at
  from auth.users u left join app.profiles p on p.id = u.id
  where u.id = me
  on conflict (user_id) do update set deleted_at = now();

  for g in select id from app.groups where created_by = me loop
    select m.user_id into heir
    from app.group_members m
    where m.group_id = g.id and m.user_id <> me
    order by m.joined_at, m.user_id
    limit 1;
    if heir is not null then
      update app.groups set created_by = heir where id = g.id;
    end if;
  end loop;

  delete from app.profiles where id = me;
  delete from auth.users where id = me;
end;
$$;

revoke all on function app.delete_my_account() from public, anon;
grant execute on function app.delete_my_account() to authenticated;

-- ---------------------------------------------------------------------------------------
-- 7. Verifiable parental consent for 13 to 17 year olds (DPDP Act s.9(1), DPDP Rules rule 10)
-- ---------------------------------------------------------------------------------------
-- One row per teen. The stepq.in server creates it when the app asks (after checking the
-- teen's session with Supabase Auth), emails the parent a one-time link (only its SHA-256 is
-- kept here), and records the parent's answer and declaration. The teen can read their own
-- row's status; nobody signed in can write it.
create table if not exists app.parental_consents (
  child_id uuid primary key references app.profiles (id) on delete cascade,
  parent_name text not null,
  parent_email text not null,
  token_hash text not null unique,
  status text not null default 'pending' check (status in ('pending', 'granted', 'declined', 'withdrawn')),
  request_count integer not null default 1,
  requested_at timestamptz not null default now(),
  responded_at timestamptz,
  responder_name text,
  relationship text check (relationship in ('parent', 'guardian')),
  responder_birth_year integer,
  responder_ip_hash text,
  notice_version text
);
alter table app.parental_consents enable row level security;
revoke all on app.parental_consents from public, anon, authenticated;
grant select (child_id, parent_name, parent_email, status, requested_at, responded_at)
  on app.parental_consents to authenticated;
drop policy if exists parental_read on app.parental_consents;
create policy parental_read on app.parental_consents for select to authenticated using (child_id = auth.uid());
do $$
begin
  if to_regprocedure('stepq.is_admin()') is not null then
    drop policy if exists admins_read on app.parental_consents;
    create policy admins_read on app.parental_consents for select to authenticated using (stepq.is_admin());
  end if;
end $$;

-- True for a signed-in user under 18 whose parent hasn't granted consent (yet).
create or replace function app.needs_parental_consent(u uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from app.profiles p
    where p.id = u
      and p.birthdate > (current_date - interval '18 years')::date
      and not exists (select 1 from app.parental_consents c where c.child_id = u and c.status = 'granted')
  )
$$;
revoke all on function app.needs_parental_consent(uuid) from public, anon, authenticated;

-- What the app shows: 'not_needed' (18+ or no profile yet), or the consent row's status
-- ('none' when no parent has been asked).
create or replace function app.my_parental_consent()
returns text
language sql stable security definer set search_path = ''
as $$
  select case
    when not exists (select 1 from app.profiles p where p.id = auth.uid()
                     and p.birthdate > (current_date - interval '18 years')::date) then 'not_needed'
    else coalesce((select c.status from app.parental_consents c where c.child_id = auth.uid()), 'none')
  end
$$;
revoke all on function app.my_parental_consent() from public, anon;
grant execute on function app.my_parental_consent() to authenticated;

-- Blocks every write a teen's account makes (steps, friends, groups, QRuns, challenges, posts,
-- push tokens…) until a parent says yes. Server-side writes (no auth.uid()) are unaffected.
create or replace function app.require_parental_consent() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if auth.uid() is not null and app.needs_parental_consent(auth.uid()) then
    raise exception 'A parent needs to approve your StepQ account first.' using errcode = '42501';
  end if;
  return new;
end
$$;
revoke all on function app.require_parental_consent() from public, anon, authenticated;

do $$
declare t text;
begin
  foreach t in array array['daily_steps', 'friendships', 'water_reminders', 'groups', 'group_members',
    'qruns', 'qrun_participants', 'challenge_entries', 'push_tokens', 'pro_waitlist', 'step_shares',
    'posts', 'post_likes', 'post_reposts', 'post_reports', 'user_blocks']
  loop
    if to_regclass('app.' || t) is not null then
      execute format('drop trigger if exists parental_consent_gate on app.%I', t);
      execute format('create trigger parental_consent_gate before insert on app.%I
                        for each row execute function app.require_parental_consent()', t);
    end if;
  end loop;
end $$;
