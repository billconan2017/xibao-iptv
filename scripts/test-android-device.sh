#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../android"
capture_logs() {
  mkdir -p app/build/reports/androidTests
  adb logcat -d > app/build/reports/androidTests/device-logcat.txt || true
}
trap capture_logs EXIT
adb logcat -c
bash gradlew --no-daemon :app:connectedDebugAndroidTest
