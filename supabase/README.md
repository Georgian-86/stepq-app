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

## StepQ Pro (`20261126000000_pro_tier.sql`)

Free vs Pro lives in `app.plan_features`: one row per feature, a Free value and a Pro value
(0/1 for switches, a number for limits, empty for unlimited). Admins change them in the admin
panel ("Pro & limits"), which calls `app.admin_set_feature`; the app reads them with
`app.my_plan()`. Every limit is enforced in Postgres (triggers and security-definer functions),
so an old or modified app can't get around it. Errors carry `hint = 'plan_limit:<key>'` so the
app can show the right sheet.

Admins also make people Pro (`app.admin_set_member`), approve Pro members to host QRuns
(`qrun_host`), post official QRuns (`app.admin_post_qrun`) and mark challenges Pro-only. Every
admin change goes to `app.admin_log`.

The migration also creates the private `avatars` storage bucket (1 MB, JPEG/PNG/WebP). A photo
is readable only by its owner and by people who can see their profile (friends and group mates).
There are no payments: members become Pro when an admin sets them.

### Round 2 (`20261127100000_pro_tier_round2.sql`)

Free members can't start Community posts (they like and reply), and new posts from anyone whose
plan has `post_approval` (off for Pro by default; Free can't start posts anyway) wait for an admin: only the author sees them
until an admin approves them in the admin panel (`app.admin_review_post`). Replies are never held.
Free members' Insights history is one year.

### Upgrade page

Free members see a full-screen "Upgrade to StepQ Pro" page from every limit sheet and from
Streaks when restores run out. It also pops up on its own when the app opens, at most once
every `upgrade_popup_days` days (admin panel, Free column; 0 turns it off), never on the first
day and never once they've asked for Pro. The perks it lists come from `app.plan_features`.
