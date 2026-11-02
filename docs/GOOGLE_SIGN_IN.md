# Google sign-in setup

The app uses Android Credential Manager to get a Google ID token, then signs in to Supabase
with it (`signInWith(IDToken)`, with a nonce). Three things must line up: a Google Cloud
**Web** client (its ID goes in the app and in Supabase), a Google Cloud **Android** client
(package + signing SHA-1), and the Google provider in Supabase.

## 1. Google Cloud (console.cloud.google.com)

1. Pick or create a project, e.g. "StepQ".
2. **APIs & Services → OAuth consent screen** (Google Auth Platform → Branding/Audience):
   app name StepQ, support email, audience **External**. Add your own Google account as a
   test user while the app is in testing.
3. **Clients → Create client → Web application**, name "StepQ Supabase".
   Authorized redirect URI: `https://koagkmedvsplbgmltehk.supabase.co/auth/v1/callback`.
   Copy the **Client ID** and **Client secret**.
4. **Clients → Create client → Android**, name "StepQ Android (debug)".
   - Package name: `in.stepq.app`
   - SHA-1: `C5:13:1B:F1:20:22:2E:F7:AE:E3:BB:C0:C7:F1:52:5F:69:11:4E:80`
     (the shared debug key in `app/keystore/debug.keystore`, used by every preview APK).
   Later, add another Android client with the Play App Signing SHA-1 for release.

## 2. Supabase (project koagkmedvsplbgmltehk)

**Authentication → Sign In / Providers → Google**: enable it, paste the Web **Client ID** and
**Client secret**, leave "Skip nonce check" off, save.

## 3. The app

The Web client ID is public. Set it as `GOOGLE_WEB_CLIENT_ID` in `local.properties` locally, and
as a repo **variable** `GOOGLE_WEB_CLIENT_ID` (Settings → Secrets and variables → Actions →
Variables) for CI preview APKs. Never put the client secret in the app.
