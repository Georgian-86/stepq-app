# StepQ Pro: Free vs Pro tiers

Status: built in the Pro tier PR. Figma: section "4 · StepQ Pro" on the App page
(https://www.figma.com/design/ALlGHGf0uFKj8bqXaoZnK1/?node-id=113-1033).

## Goal

Two plans, Free and Pro, with every difference adjustable from the admin panel. Pro must feel
clearly premium without leaving the StepQ look.

## What changes per plan (seeded defaults, all editable)

| Feature key | Free | Pro | Where it shows |
|---|---|---|---|
| upload_photo | no | yes | Pro page → Profile photo; avatars everywhere |
| change_theme | no | yes | Pro page → Make it yours (Leaf, Ocean, Grape, Sunset, Gold) |
| plan_qrun | no | yes (once an admin approves) | QRun "Post" button |
| streak_restores | 2 a month | 8 a month | Streaks → Lost last night |
| reward_weekly | yes | yes | Challenges period card |
| reward_monthly | no | yes | Challenges period card |
| reward_yearly | no | yes | config only (no yearly board yet) |
| all_challenges | no | yes | Pro-only challenges |
| analysis_days | 7 days | unlimited | Insights |
| water_reminders | 3 a day | unlimited | Friends water banner |
| profile_badge | no | yes | PRO badge next to names |
| pin_bff | no (top streak shown) | yes, one BFF | Streaks BFF card |
| community_posts | 3 a month | unlimited | Community composer (likes and replies always free) |

Streaks, StepQ score, QRun fest participation and Piko chat are the same on both plans.

## QRuns

Only admins post QRuns from the admin panel, plus Pro members an admin has approved
(`profiles.qrun_host`). A Pro member can ask to host from the app; the request shows in the
admin panel. Admin-posted QRuns are marked `official` and hosted by a "StepQ" profile.

## The premium look ("Kadam Gold")

- Night-green cards (Night 1–3) with a gold gradient (Gold 1–3) and a thin gold border, used
  only for Pro moments: the Pro profile hero, membership card, BFF card, Insights chart.
- A gold PRO pill with a crown next to Pro members' names, and a gold ring around their avatar
  or photo, so Pro is visible to everyone else too.
- Champagne rows (cream-to-gold) for "upgrade" or Pro entry points.
- Themes recolour the whole app; text colours and warnings stay fixed for contrast.
- Free members see what they're missing at the moment it matters (a limit sheet with "3/3"),
  not as nagging banners.

## Enforcement

Postgres is the source of truth (`supabase/migrations/20261126000000_pro_tier.sql`): triggers on
profiles, qruns, water_reminders and challenge_entries, and security-definer functions for
posts, restores and history. Errors carry `hint = 'plan_limit:<key>'`; the app maps that to the
same limit sheet. Months and days roll over in India time. Deleting a post does not give a
post back.

Known gap: `app.step_history` caps Insights, but a member can still read their own raw
`daily_steps` rows (they're their own data).

## Not in this change

Payments (members become Pro when an admin sets them; Free members can ask from the app),
a yearly leaderboard, and Piko chat.
