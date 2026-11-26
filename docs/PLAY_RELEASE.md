# Publishing StepQ on Google Play

The checklist for the first production release. Not legal advice.

## 1. Build the bundle
1. Create the upload key once (keep it and its passwords safe; losing it means a key reset with Google):
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. GitHub → Settings → Secrets → Actions: `STEPQ_KEYSTORE_BASE64` (`base64 -w0 upload.jks`),
   `STEPQ_KEYSTORE_PASSWORD`, `STEPQ_KEY_ALIAS`, `STEPQ_KEY_PASSWORD`, `SUPABASE_ANON_KEY`,
   `GOOGLE_SERVICES_JSON`; variables `SUPABASE_URL`, `GOOGLE_WEB_CLIENT_ID`.
3. Actions → **Play release** → Run workflow. Download the `.aab` and `mapping.txt`.
4. Play Console → create the app (`in.stepq.app`), turn on **Play App Signing**, upload the bundle to
   **Internal testing** first. Upload `mapping.txt` under App bundle explorer → Downloads.
5. Add the **Play App Signing SHA-1** and the upload key's SHA-1 to the Google Cloud Android OAuth client
   (see GOOGLE_SIGN_IN.md), or Google sign-in fails in the Play build.

## 2. Supabase
- Run every file in `supabase/migrations/` that isn't live yet, in order, ending with
  `20261127000000_launch_audit.sql`.
- Authentication → URL configuration: `stepq://auth-callback` in Redirect URLs. The app uses the PKCE
  flow, so email links must be opened on the phone that asked for them.
- Schedule the retention jobs (project files: `20261124010000_retention_jobs_REVIEW_FIRST.sql`) once reviewed.

## 3. App content (Play Console → Policy → App content)
| Item | Answer |
|---|---|
| Privacy policy | https://stepq.in/privacy |
| App access | Give a test email + password (Play reviewers must sign in). Use an adult account. |
| Ads | No ads |
| Content rating | Fill in the IARC questionnaire: user-generated content and user interaction = yes |
| Target audience | 13–15, 16–17 and 18+. Not under 13. Not in the Families programme. |
| Health apps | Fitness / activity tracking. Not a medical device. |
| Health Connect | Declare READ_STEPS, READ_DISTANCE, READ_ACTIVE_CALORIES_BURNED, READ_EXERCISE with the reason "show daily activity, goals and rankings" and link https://stepq.in/privacy#health-and-step-data |
| Account deletion | In app: Profile → Account → Delete account. Web: https://stepq.in/delete-account |
| Government app / financial features | No |

### Data safety form
All data is encrypted in transit. Users can request deletion. No data is sold or used for ads.

| Data type | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|
| Name, email, user IDs | Yes | No | Required | Account management, app functionality |
| Gender, date of birth | Yes | No | Required | App functionality (age check, rankings) |
| Photos (Pro profile photo) | Yes | No | Optional | App functionality |
| Fitness info (steps, distance, calories, exercise) | Yes | No | Required | App functionality |
| Approximate location | Yes (used on device, not stored) | No | Optional | App functionality (QRuns near you) |
| Contacts (email hashes, not stored) | Yes | No | Optional | App functionality (find friends) |
| Other user-generated content (posts, replies) | Yes | No | Optional | App functionality |
| App interactions (likes, friends, QRuns) | Yes | No | Required | App functionality |
| Crash logs, diagnostics | Yes | No | Required | Analytics (crash reporting) |
| Device or other IDs (FCM token) | Yes | No | Optional | App functionality (notifications) |

Processors (Supabase, Google Firebase) count as "service providers", not sharing.

### Sensitive permissions
- `READ_CONTACTS`: only after the person taps "Find contacts" on a screen that explains it.
- `ACCESS_COARSE_LOCATION`: foreground only, on the QRun tab.
- `ACTIVITY_RECOGNITION`: the phone's step counter.

## 4. Before you charge for Pro
Pro is admin-granted today. Selling Pro inside the Android app must use **Google Play Billing**;
an external payment link for a digital subscription gets the app rejected.
