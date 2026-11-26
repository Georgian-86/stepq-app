-- Launch audit fixes (DPDP Act 2023 s.8(5), s.11; DPDP Rules 2025 rule 6).
-- Additive and re-runnable. Changes no existing rows. Run after 20261124000000_privacy_compliance.sql
-- and 20261126000000_pro_tier.sql.
--
-- 1. Contact matching is rate limited (10 lookups a day, each up to 2,000 hashes), so nobody can
--    use it to test long lists of email addresses for StepQ accounts.
-- 2. "Download my data" now also covers StepQ Pro: plan, streak restores, community post usage,
--    admin plan changes about you, your profile photo's path and contact lookups.

do $$
begin
  if to_regclass('app.consents') is null or to_regclass('app.streak_restores') is null then
    raise exception 'Run 20261124000000_privacy_compliance.sql and 20261126000000_pro_tier.sql first.';
  end if;
end $$;

-- ---------------------------------------------------------------- 1. Contact lookups
create table if not exists app.contact_lookups (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  at timestamptz not null default now(),
  hashes integer not null
);
create index if not exists contact_lookups_user_idx on app.contact_lookups (user_id, at desc);
alter table app.contact_lookups enable row level security;
alter table app.contact_lookups force row level security;
revoke all on app.contact_lookups from public, anon, authenticated;

create or replace function app.match_contacts(email_hashes text[])
returns table (id uuid, display_name text, username text, avatar jsonb)
language plpgsql volatile security definer set search_path = ''
as $$
declare me uuid := auth.uid();
begin
  if me is null then
    return;
  end if;
  if cardinality(email_hashes) > 2000 then
    raise exception 'Too many contacts at once.' using errcode = '22023';
  end if;
  if (select count(*) from app.contact_lookups l where l.user_id = me and l.at > now() - interval '1 day') >= 10 then
    raise exception 'You''ve checked your contacts a lot today. Try again tomorrow.'
      using errcode = 'P0001', hint = 'rate_limit:contacts';
  end if;
  insert into app.contact_lookups (user_id, hashes) values (me, coalesce(cardinality(email_hashes), 0));
  -- Keep only a month of lookups.
  delete from app.contact_lookups l where l.user_id = me and l.at < now() - interval '30 days';

  return query
  select p.id, p.display_name, p.username, p.avatar
  from auth.users u
  join app.profiles p on p.id = u.id
  where u.id <> me
    and u.email is not null
    and encode(sha256(convert_to(lower(trim(u.email)), 'UTF8')), 'hex') = any (email_hashes)
    and not app.is_blocked(me, p.id)
  order by p.display_name
  limit 50;
end $$;
revoke all on function app.match_contacts(text[]) from public, anon;
grant execute on function app.match_contacts(text[]) to authenticated;

-- ---------------------------------------------------------------- 2. Download my data
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
    'streak_restores', coalesce((select jsonb_agg(to_jsonb(r) order by r.created_at) from app.streak_restores r where r.user_id = me), '[]'),
    'plan_usage', coalesce((select jsonb_agg(to_jsonb(e) order by e.at) from app.usage_events e where e.user_id = me), '[]'),
    'plan', app.my_plan(),
    'plan_changes_by_admins', coalesce((select jsonb_agg(jsonb_build_object('at', l.at, 'action', l.action, 'detail', l.detail) order by l.at)
                                        from app.admin_log l where l.target = me::text), '[]'),
    'profile_photo', (select case when p.photo_path is null then null else
                        jsonb_build_object('path', p.photo_path, 'note', 'The photo file itself is in the app: Profile, then your photo.') end
                      from app.profiles p where p.id = me),
    'contact_lookups', (select count(*) from app.contact_lookups c where c.user_id = me),
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
