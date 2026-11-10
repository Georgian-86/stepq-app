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
./gradlew connectedDebugAndroidTest --console=plain "${args[@]}"
status=$?

# Screenshot of whatever the app shows after a fresh launch.
adb shell pm grant in.stepq.app android.permission.ACTIVITY_RECOGNITION || true
adb shell am start -W -n in.stepq.app/com.stepq.app.MainActivity || true
sleep 15
adb exec-out screencap -p > emulator-output/app.png || true
adb logcat -d > emulator-output/logcat.txt || true

if grep -q "FATAL EXCEPTION" emulator-output/logcat.txt; then
  echo "::error::The app crashed on the emulator. See logcat.txt in the emulator-check artifact."
  grep -A30 "FATAL EXCEPTION" emulator-output/logcat.txt | head -60
  status=1
fi
exit $status
