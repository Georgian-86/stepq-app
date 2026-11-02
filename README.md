# StepQ for Android

The StepQ app: daily steps with friends, groups, QRuns and challenges. Designs live in the
Figma file "StepQ · Kadam — App, Website & Admin", page **App** (screens A01–A27).

- **Kotlin + Jetpack Compose**, Material 3 themed with the StepQ tokens (Fredoka, Nunito, leaf/deep/mango).
- **Supabase** (same project as stepq.in) for sign-in and data, through
  [supabase-kt](https://github.com/supabase-community/supabase-kt). Only the public anon key is used;
  row-level security decides what each user can read and write.
- **Health Connect** for steps, distance and active calories.
- **Firebase** only for push (FCM) and Crashlytics.

See [docs/PLAN.md](docs/PLAN.md) for the screens, data model and the order of work.

## Run it

1. Android Studio (latest stable) with the Android 37 SDK, or JDK 21 and the command-line SDK.
2. Create `local.properties` in the repo root (it is git-ignored):

   ```properties
   sdk.dir=/path/to/Android/sdk
   SUPABASE_URL=https://koagkmedvsplbgmltehk.supabase.co
   SUPABASE_ANON_KEY=<Supabase → Project Settings → API Keys → anon / publishable key>
   ```

   Never put the `service_role` / secret key here: it would ship inside the APK.
   Without a key the app still opens, and log-in says it isn't configured.
3. Optional: put the Firebase Android config at `app/google-services.json` (also git-ignored) to turn
   on push and Crashlytics. The build skips Firebase setup when the file is absent.
4. `./gradlew installDebug`, or Run in Android Studio.

## Checks

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew recordRoborazziDebug   # screenshots of each screen → app/build/outputs/roborazzi/
```

CI runs the same on every pull request and uploads the screenshots.

## Credits

Fredoka and Nunito are bundled from Google Fonts under the SIL Open Font License 1.1.
Piko artwork comes from `packages/brand` in the stepq website repo.
