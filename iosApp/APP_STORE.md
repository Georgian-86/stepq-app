# StepQ for iOS: App Store review checklist

This file maps every App Review Guideline that affects StepQ to what the app does about it. It also lists the steps that only the account owner can do. Section numbers are from Apple's App Review Guidelines.

## What the app already does

| Area | Guideline | How StepQ handles it |
| --- | --- | --- |
| Sign in with Apple | 4.8 | Apple is the first button on Welcome. Google only shows when the Apple provider is also turned on in Supabase (`AuthProviders.load()`). Email and password sign-in is also offered. |
| Account deletion | 5.1.1(v) | Profile → Privacy & data → Delete account. It deletes the account inside the app, with no email or website needed. Apple users re-authenticate, and the `apple-revoke` function revokes StepQ's Apple tokens. The screen warns that an App Store subscription has to be cancelled in Settings and links to Manage subscription. Deleting is also offered on the parent-consent and re-consent screens, so nobody is stuck. |
| HealthKit | 2.5.1, 5.1.3 | Read only: steps, distance, active energy and exercise minutes. The purpose string says exactly that. The app shows its own explainer before Apple's permission sheet. Health data is used only for the person's own steps and leaderboards. There are no ads and no analytics SDKs, health data is never in iCloud, and nothing is written to Health. Not having Health connected doesn't block the app. |
| In-app purchase | 3.1.1, 3.1.2 | StoreKit 2 `SubscriptionStoreView` for monthly and yearly StepQ Pro. The paywall has Terms (Apple's standard EULA), Privacy, Restore purchases and Manage subscription. No prices, links or wording point to buying anywhere else. The server verifies every purchase (`appstore-verify`), and Apple's server notifications keep renewals, refunds and expiry in sync. |
| User-generated content | 1.2 | Agreeing to the Community rules (versioned) is required before reading or posting. Every post and reply has Report (with reasons) and Block, and blocked people disappear right away. A server-side word filter (`app.blocked_terms`) refuses abusive words. Free members' posts wait for approval, and reports are stored for admins to review. |
| Kids and age | 1.3, 5.1.4 | Age 13+ only. The birthdate is asked during onboarding. 13 to 17 year olds need a parent's consent through stepq.in (DPDP Act) before using the app. Not in the Kids category. |
| Contests and rewards | 5.3 | The Challenges tab shows "Apple is not a sponsor of, and is not involved in, StepQ challenges or rewards" and links to the official rules. |
| Privacy | 5.1.1, 5.1.2 | Consent to Terms and Privacy is recorded with the policy version, and a re-consent screen appears when the policy changes. Data export is available in Privacy & data. `PrivacyInfo.xcprivacy` declares the data collected and the required-reason APIs. There is no tracking and no ATT prompt (none is needed). |
| Export compliance | — | `ITSAppUsesNonExemptEncryption = false`, because the app only uses HTTPS. |
| Completeness | 2.1 | No "coming soon" screens on iOS. QRuns, Groups and push notifications are Android-only for now and simply aren't shown on iOS. |

## What only you can do (before the first TestFlight build)

### Apple Developer and App Store Connect
1. Join the Apple Developer Program as an organisation, so the seller name is the company and not your personal name. Note the Team ID.
2. Register the bundle ID `in.stepq.app` with three capabilities: HealthKit, Sign in with Apple and In-App Purchase.
3. In App Store Connect, create the app. Then sign the **Paid Apps agreement** and add bank and tax details. Subscriptions can't be sold or reviewed without these.
4. Create a subscription group, **StepQ Pro**, with `in.stepq.app.pro.monthly` and `in.stepq.app.pro.yearly`. Set prices, a display name and a description, plus a review screenshot of the paywall.
5. Under App Information → App Store Server Notifications, set both Production and Sandbox to `https://koagkmedvsplbgmltehk.supabase.co/functions/v1/appstore-notifications` (Version 2).
6. Set the age rating questionnaire to 13+. Answer "yes" to unrestricted user-generated content with moderation.
7. Fill in the App Privacy labels so they match `PrivacyInfo.xcprivacy`:
   - Data collected and linked to the person, not used for tracking: contact info (email, name), health and fitness, user content, identifiers (user ID), purchases, and other data (birthdate and gender).
   - Purposes: app functionality.
8. Create a demo account for App Review. Pick one that is already past onboarding, an adult, with a few friends and posts. Put its details in the review notes, plus one line: "Health data: steps are read from Apple Health; you can skip connecting it."

### Supabase
1. Auth → Providers → **Apple**: turn it on, using the bundle ID `in.stepq.app` as the client ID (for native sign-in). Add the Services ID too if you want web sign-in.
2. Auth → URL configuration: add `stepq://auth-callback` to the redirect URLs, so Google sign-in and password resets come back to the app.
3. Apply the migration `supabase/migrations/20261128000000_ios_app_store.sql`. I haven't applied anything to production.
4. Deploy the Edge Functions and set their secrets. The `.p8` key comes from Certificates, Identifiers & Profiles → Keys, with Sign in with Apple enabled.
   ```sh
   supabase secrets set APPLE_BUNDLE_ID=in.stepq.app APPLE_APP_ID=<numeric Apple ID> \
     APPLE_TEAM_ID=<team id> APPLE_SIWA_KEY_ID=<key id> APPLE_SIWA_PRIVATE_KEY="$(cat AuthKey_XXXX.p8)"
   supabase functions deploy appstore-verify
   supabase functions deploy apple-revoke
   supabase functions deploy appstore-notifications --no-verify-jwt
   ```
5. Add the anon key (never the service role key) to `iosApp/Config/Secrets.xcconfig` locally, and to the repository secret `SUPABASE_ANON_KEY` for CI.

### Website (stepq.in)
- `stepq.in/terms#challenges-and-rewards` has to exist. It needs official contest rules covering eligibility, how winners are picked, the prizes, the dates, and "Apple is not a sponsor".
- The privacy policy should mention Apple Health data (read only, never sold, never used for ads) and App Store purchases.

### Signing
- Open the generated Xcode project, choose your team under Signing & Capabilities, and archive. Alternatively, add an App Store Connect API key to CI later for automatic TestFlight uploads.

## Risks to decide on

1. **Rewards that need Pro.** If a challenge with a prize is open only to paying members, Apple can treat it as a paid contest or lottery (5.3.3). That is also legally sensitive in India. The safest setup is for every prize challenge to be free to enter, with Pro-only challenges carrying no prizes.
2. **Age-assurance laws in US states.** Texas (SB 2420, from 2026) and similar laws require apps to use Apple's Declared Age Range API for users in those states. The app doesn't do this yet. Launching in India first avoids the problem. Adding the API is a follow-up before a US launch.
3. **Report response time.** Apple expects objectionable content to be acted on within 24 hours. Someone needs to check the dashboard's reports every day.
4. **No push notifications on iOS v1.** This is fine for review. Water reminders from friends only arrive on Android until APNs is added.
