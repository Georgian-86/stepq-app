-- The app saves steps with an upsert (PostgREST `on_conflict=user_id,day`), whose
-- DO UPDATE sets every column it sent, including the key. Allow updating the key columns;
-- the steps_update policy still pins user_id to the signed-in user.
grant update (user_id, day) on app.daily_steps to authenticated;
