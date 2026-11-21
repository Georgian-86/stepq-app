-- Four StepQ test accounts, for trying the app with friends, a group, steps and a QRun.
--
-- NOT a migration: run it once by hand in the Supabase SQL editor (it needs the service role,
-- which the SQL editor has). Re-running it is safe: it skips accounts that already exist.
-- To remove everything it made, run the block at the bottom.
--
-- Sign in with any of these and the password below:
--   test.riya@stepq.test    @test.riya    (female)
--   test.arjun@stepq.test   @test.arjun   (male)
--   test.meera@stepq.test   @test.meera   (female)
--   test.kabir@stepq.test   @test.kabir   (male)
-- To use a different password, change `pw` below before running.

do $$
declare
  pw constant text := 'StepQ-test-2026';
  people constant jsonb := '[
    {"id": "7e570000-0000-4000-8000-000000000001", "email": "test.riya@stepq.test",  "name": "Riya Test",  "username": "test.riya",  "gender": "female", "avatar": {"skin": 1, "hair": 0, "outfit": 1, "headband": 0, "style": 1}, "goal": 8000,  "base": 7200},
    {"id": "7e570000-0000-4000-8000-000000000002", "email": "test.arjun@stepq.test", "name": "Arjun Test", "username": "test.arjun", "gender": "male",   "avatar": {"skin": 3, "hair": 0, "outfit": 3, "headband": 1, "style": 0}, "goal": 10000, "base": 9800},
    {"id": "7e570000-0000-4000-8000-000000000003", "email": "test.meera@stepq.test", "name": "Meera Test", "username": "test.meera", "gender": "female", "avatar": {"skin": 2, "hair": 2, "outfit": 2, "headband": 2, "style": 2}, "goal": 6000,  "base": 5100},
    {"id": "7e570000-0000-4000-8000-000000000004", "email": "test.kabir@stepq.test", "name": "Kabir Test", "username": "test.kabir", "gender": "male",   "avatar": {"skin": 4, "hair": 1, "outfit": 0, "headband": 3, "style": 0}, "goal": 12000, "base": 11400}
  ]';
  p jsonb;
  uid uuid;
  d integer;
  g uuid;
begin
  for p in select * from jsonb_array_elements(people) loop
    uid := (p ->> 'id')::uuid;
    if exists (select 1 from auth.users where id = uid or email = p ->> 'email') then
      continue;
    end if;

    -- A confirmed email-and-password account, as if they'd signed up and clicked the link.
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, recovery_token, email_change_token_new, email_change
    ) values (
      '00000000-0000-0000-0000-000000000000', uid, 'authenticated', 'authenticated', p ->> 'email',
      extensions.crypt(pw, extensions.gen_salt('bf')), now(),
      '{"provider": "email", "providers": ["email"]}', jsonb_build_object('full_name', p ->> 'name'),
      now(), now(), '', '', '', ''
    );
    insert into auth.identities (id, user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at)
    values (
      gen_random_uuid(), uid, uid::text, 'email',
      jsonb_build_object('sub', uid::text, 'email', p ->> 'email', 'email_verified', true),
      now(), now(), now()
    );

    insert into app.profiles (id, display_name, username, gender, birthdate, avatar, daily_goal, country)
    values (uid, p ->> 'name', p ->> 'username', p ->> 'gender', '1998-06-15', p -> 'avatar', (p ->> 'goal')::int, 'IN');

    -- 35 days of steps, varying day to day, so streaks, the week chart and rankings have data.
    for d in 0..34 loop
      insert into app.daily_steps (user_id, day, steps, distance_m, active_minutes, calories)
      values (
        uid, current_date - d,
        greatest(800, (p ->> 'base')::int + ((d * 1371 + (p ->> 'goal')::int) % 5200) - 2600),
        greatest(600, ((p ->> 'base')::int + ((d * 1371 + (p ->> 'goal')::int) % 5200) - 2600) * 3 / 4),
        30 + (d * 7) % 50,
        180 + (d * 13) % 220
      )
      on conflict (user_id, day) do nothing;
    end loop;
  end loop;

  -- Riya, Arjun and Meera are friends; Kabir has sent Riya a request, so she can accept it.
  insert into app.friendships (requester, addressee, status) values
    ('7e570000-0000-4000-8000-000000000001', '7e570000-0000-4000-8000-000000000002', 'accepted'),
    ('7e570000-0000-4000-8000-000000000001', '7e570000-0000-4000-8000-000000000003', 'accepted'),
    ('7e570000-0000-4000-8000-000000000002', '7e570000-0000-4000-8000-000000000003', 'accepted'),
    ('7e570000-0000-4000-8000-000000000004', '7e570000-0000-4000-8000-000000000001', 'pending')
  on conflict do nothing;

  -- A group with all four.
  if not exists (select 1 from app.groups where created_by = '7e570000-0000-4000-8000-000000000001' and name = 'Test walkers') then
    insert into app.groups (name, created_by) values ('Test walkers', '7e570000-0000-4000-8000-000000000001') returning id into g;
    insert into app.group_members (group_id, user_id)
    select g, (x ->> 'id')::uuid from jsonb_array_elements(people) x;
  end if;

  -- A QRun Arjun is hosting this weekend, with Meera going.
  if not exists (select 1 from app.qruns where created_by = '7e570000-0000-4000-8000-000000000002') then
    insert into app.qruns (created_by, title, place_name, lat, lng, starts_at)
    values ('7e570000-0000-4000-8000-000000000002', 'Test morning walk', 'Carter Road Promenade, Bandra West, Mumbai',
            19.0656, 72.8226, date_trunc('day', now()) + interval '2 days 6 hours 30 minutes')
    returning id into g;
    insert into app.qrun_participants (qrun_id, user_id) values
      (g, '7e570000-0000-4000-8000-000000000002'), (g, '7e570000-0000-4000-8000-000000000003');
  end if;
end $$;

-- To make one of them your friend too, put your username in and run:
--   insert into app.friendships (requester, addressee, status)
--   select '7e570000-0000-4000-8000-000000000001', id, 'accepted' from app.profiles where username = 'your.username'
--   on conflict do nothing;

-- To remove all four test accounts and everything they made (profiles, steps, friendships,
-- groups and QRuns go with them):
--   delete from auth.users where id in (
--     '7e570000-0000-4000-8000-000000000001', '7e570000-0000-4000-8000-000000000002',
--     '7e570000-0000-4000-8000-000000000003', '7e570000-0000-4000-8000-000000000004');
