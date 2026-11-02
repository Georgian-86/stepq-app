# StepQ Android: plan

Source of truth for the UI: Figma "StepQ · Kadam — App, Website & Admin", page **App**.

## Stack

| Concern | Choice | Why |
| --- | --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3 with StepQ tokens | Native Android, matches the Figma ramp 1:1 |
| Navigation | Navigation Compose | One activity, routes per screen |
| Auth | Supabase Auth (email + password, then Google and Apple) | Same project and users as stepq.in |
| Data | Supabase Postgres via PostgREST, new `app` schema, RLS on every table | The website's `stepq` schema stays private |
| Steps | Health Connect (steps, distance, active calories, exercise) | A10 "Let Piko count your steps" |
| Push | Firebase Cloud Messaging | Water reminders, QRun reminders, challenge news |
| Crashes | Firebase Crashlytics | |
| Build | AGP 9.5, Gradle 9.8, compile/target SDK 37, min SDK 28 | Current AndroidX and Compose need AGP 9.1+ and SDK 37 |
| Tests | JUnit, Robolectric + Roborazzi screenshots | Screens are checked against Figma in CI |

Application id: `in.stepq.app` (permanent once on Play). Code package: `com.stepq.app`.

## Screens

| # | Screen | Status |
| --- | --- | --- |
| A01 | Splash | done (PR 1) |
| A02 | Log in (email) | done (PR 1); Google/Apple buttons say "coming soon" |
| A03–A04 | Forgot password, reset link sent | done (PR 1) |
| A05–A07 | Sign up: account, name + username, about you (gender, birthdate) | PR 2 |
| A08 | Avatar builder (skin, hair, outfit, headband) | PR 2 |
| A09 | Daily step goal (5k/8k/10k/12k or custom) | PR 2 |
| A10 | Connect steps (Health Connect permissions) | PR 2 |
| A11, A11a, A11b | All set; first 10K progress and milestones; 10K unlocked | PR 3 |
| A12, A12b, A12c | Home: ring, streak, StepQ score, week bars, friends today; goal reached; evening | PR 3 |
| A13–A14 | Friends leaderboard, water reminder (5 a day on free) | PR 4 |
| A15–A17 | Groups, create group, group leaderboard | PR 4 |
| A18–A21 | QRun list, post a QRun, join disclaimer, event planner | PR 5 |
| A22–A25 | Challenges weekly/monthly, challenge detail, joined | PR 6 |
| A26–A27 | Profile and settings, StepQ Pro waitlist | PR 7 |

Tab shell (Home, Friends, Challenges, Profile) is in PR 1 with placeholders.

## Data model (proposed `app` schema)

All tables have RLS on. "Me" means `auth.uid()`.

| Table | Purpose | Who can read / write |
| --- | --- | --- |
| `profiles` | name, username (unique), gender, birthdate, avatar config, daily goal, plan (free/pro) | read: me and my friends/group members (public fields only); write: me |
| `daily_steps` | user, date, steps, distance, active minutes, calories (from Health Connect) | read: me, friends, shared group members; write: me |
| `friendships` | requester, addressee, status | read/write: either side |
| `water_reminders` | from, to, sent_at; 5 a day on free, enforced in Postgres | insert: me (to a friend); read: either side |
| `groups`, `group_members` | name, created_by, invite code; membership | read: members; write: creator / me joining |
| `qruns`, `qrun_participants` | where, when, pace note, created_by; who joined | read: signed-in users near by; write: creator / me joining |
| `challenges`, `challenge_entries` | partner, reward, target steps, window; who joined and progress | read: everyone signed in; challenges written by admins only |
| `push_tokens` | user, FCM token, platform | write/read: me |
| `pro_waitlist` | user, joined_at | insert/read: me |

Leaderboards (friends, groups, weekly/monthly challenges, StepQ score) are Postgres views or
`security invoker` functions, so RLS still applies.

Schema SQL will live in `supabase/migrations/` here. Applying it to the live project is a
separate, explicit step.

## Server-side pieces

- **Push sending**: a Supabase Edge Function (or a stepq.in API route) holds the Firebase service
  account and sends FCM when a water reminder or QRun is created. Never in the app.
- **Admin dashboard**: per the project rules, the new `app` tables get listed in stepq.in/admin
  with the same admin-only RLS (`admins_read` policy). That change goes in the stepq repo.

## Settings that need the owner

1. Supabase → Authentication: sign-ups are **off** today (admin-only login). The app needs them
   **on**. Admin data stays safe because the dashboard only shows rows to users in `stepq.admins`.
2. Supabase → Data API: add `app` to the exposed schemas (keep `stepq` out).
3. Google sign-in: an Android OAuth client (package + SHA-1) and a web client in Google Cloud,
   then enable the Google provider in Supabase. Apple sign-in: Services ID and key from Apple.
4. Firebase project with an Android app for `in.stepq.app`; `google-services.json` as a CI secret.
5. Health Connect: Play Console health-permissions declaration before release.
