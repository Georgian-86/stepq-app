# StepQ for iOS

The SwiftUI app for iPhone. It shares its data layer with Android through the Kotlin
Multiplatform module in [`../shared`](../shared): the Supabase calls, the models and the Free/Pro,
streak and validation rules are the same code on both platforms. Only the screens are native.

- **SwiftUI**, iOS 17+, iPhone only. Fonts (Fredoka, Nunito) and Piko come from the Android app.
- **Supabase** (same project, schema and row-level security as Android) through `StepqApi`
  in `shared/src/iosMain`. The session is kept in the Keychain. Only the public anon key ships.
- **Apple Health** (HealthKit, read only) for steps, distance, active energy and exercise minutes.
- **Sign in with Apple**, email + password, and Google when it's switched on in Supabase.
- **StoreKit 2** for StepQ Pro, checked on the server by the `appstore-verify` Edge Function.

App Store review notes and the list of things to set up in App Store Connect are in
[APP_STORE.md](APP_STORE.md).

## Build it

You need a Mac with Xcode 16 or newer, JDK 21 and [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`).

1. `cp iosApp/Config/Secrets.example.xcconfig iosApp/Config/Secrets.xcconfig` and fill in the
   Supabase **anon** key and your Apple Developer Team ID. The file is git-ignored. Never put the
   `service_role` key here.
2. `cd iosApp && xcodegen generate`
3. Open `iosApp/StepQ.xcodeproj` and run the **StepQ** scheme. The first build compiles the
   shared Kotlin framework through Gradle (a few minutes); later builds are quicker.

The scheme uses `StepQ.storekit`, so StepQ Pro can be bought in the simulator without App Store
Connect. Real purchases need the products set up in App Store Connect (see APP_STORE.md).

## Checks

```sh
./gradlew :shared:iosSimulatorArm64Test        # shared rules, natively on the simulator
./gradlew :shared:compileKotlinIosArm64        # also works on Linux (klib cross-compilation)
```

CI (`.github/workflows/ios.yml`) builds the app for the simulator on every pull request that
touches iOS or shared code, and uploads screenshots of each screen taken in demo mode
(`-demoScreen home`, debug builds only).

## Layout

| Folder | What's there |
| --- | --- |
| `StepQ/App` | App entry, `AppModel` (auth state, routing), config, demo mode |
| `StepQ/Auth` | Log in, sign up, Sign in with Apple, Google, password reset |
| `StepQ/Onboarding` | Name, about you, avatar, goal, Apple Health, parental consent, re-consent |
| `StepQ/Health` | `HealthSync`: reads daily totals from Apple Health and saves them |
| `StepQ/Home`, `Friends`, `Community`, `Challenges`, `Profile` | The five tabs |
| `StepQ/Pro` | `ProStore` (StoreKit 2) and the paywall |
| `StepQ/PrivacyInfo.xcprivacy` | Privacy manifest |

Not on iOS yet (Android only for now): QRuns, groups, contacts matching, push notifications,
profile photos and themes.
