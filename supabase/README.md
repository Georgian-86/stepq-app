# Supabase: app schema

The app's tables live in their own `app` schema in the same Supabase project as stepq.in.
The website's `stepq` schema is untouched and stays out of the public API.

- `migrations/` — SQL to apply, in filename order. Additive and safe to re-run; no data changes.
- `tests/run.sh` — applies the migrations twice to a scratch local Postgres and runs
  `tests/rls_test.sql`, which signs in as different users and checks what row-level security
  allows (own data, friends, groups, strangers, admins, anonymous). CI runs it on every PR.

## Turning it on (owner, once)

Do these in order, in the Supabase dashboard for project `koagkmedvsplbgmltehk`:

1. **SQL editor** → paste `migrations/20261102000000_app_schema.sql` → Run.
   It only creates the `app` schema, its tables, functions and policies.
2. **Project Settings → Data API → Exposed schemas**: add `app`. Leave `stepq` out.
3. **Authentication → Sign In / Providers**: turn **on** "Allow new users to sign up".
   Admin access is unaffected: the dashboard only shows data to users in `stepq.admins`,
   and nobody can add themselves there.
4. **Authentication → Providers → Email**: keep "Confirm email" on. The app handles it:
   after sign-up it asks the user to confirm, and onboarding resumes on first log-in.
5. Put the anon key in the app's `local.properties` (see the main README).

Undo, if ever needed: remove `app` from Exposed schemas and turn sign-ups off again.
