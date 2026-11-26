#!/usr/bin/env bash
# Installs the simulator build and takes screenshots of each screen in demo mode (sample data,
# no network). Used by CI; run it locally after building for the simulator the same way.
set -euo pipefail
cd "$(dirname "$0")/.."
APP=$(find build/Build/Products/Debug-iphonesimulator -maxdepth 1 -name 'StepQ.app' | head -1)
DEVICE=$(xcrun simctl list devices available | grep -E "iPhone 1[5-9]( Pro)? \(" | head -1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')
[ -n "$DEVICE" ] || { echo "No iPhone simulator found"; exit 1; }
xcrun simctl boot "$DEVICE" || true
xcrun simctl bootstatus "$DEVICE" -b
xcrun simctl status_bar "$DEVICE" override --time "9:41" --batteryState charged --batteryLevel 100 --cellularBars 4 || true
xcrun simctl install "$DEVICE" "$APP"
mkdir -p screenshots
for screen in welcome home friends community rules challenges profile paywall delete; do
  xcrun simctl terminate "$DEVICE" in.stepq.app >/dev/null 2>&1 || true
  xcrun simctl launch "$DEVICE" in.stepq.app -demoScreen "$screen" >/dev/null
  sleep 6
  xcrun simctl io "$DEVICE" screenshot "screenshots/$screen.png" >/dev/null
  echo "screenshots/$screen.png"
done
