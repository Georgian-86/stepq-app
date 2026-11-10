#!/usr/bin/env bash
# Runs inside the CI emulator: instrumented smoke test, then a screenshot and the app's log.
set -uo pipefail
mkdir -p emulator-output
adb logcat -c || true

args=()
if [[ -n "${STEPQ_TEST_EMAIL:-}" && -n "${STEPQ_TEST_PASSWORD:-}" ]]; then
  args+=("-Pandroid.testInstrumentationRunnerArguments.email=$STEPQ_TEST_EMAIL")
  args+=("-Pandroid.testInstrumentationRunnerArguments.password=$STEPQ_TEST_PASSWORD")
fi
# Keep the app installed (and signed in) after the test, for the screenshot below.
./gradlew connectedDebugAndroidTest --console=plain \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "${args[@]}"
status=$?
echo "::group::What the smoke test reached"
adb logcat -d -s StepqSmoke:I || true
echo "::endgroup::"

# Screenshot of whatever the app shows after a fresh launch.
adb shell pm grant in.stepq.app android.permission.ACTIVITY_RECOGNITION || true
adb shell am start -W -n in.stepq.app/com.stepq.app.MainActivity || true
sleep 20
adb exec-out screencap -p > emulator-output/app.png || true
if ! adb shell pidof in.stepq.app > /dev/null; then
  echo "::error::StepQ isn't running 20 seconds after launch."
  status=1
fi
adb logcat -d > emulator-output/logcat.txt || true

if grep -q "FATAL EXCEPTION" emulator-output/logcat.txt; then
  echo "::error::The app crashed on the emulator. See logcat.txt in the emulator-check artifact."
  grep -A30 "FATAL EXCEPTION" emulator-output/logcat.txt | head -60
  status=1
fi
exit $status
