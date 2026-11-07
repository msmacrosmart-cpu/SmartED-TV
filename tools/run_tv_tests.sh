#!/usr/bin/env bash
set -uo pipefail
./gradlew :app:connectedDebugAndroidTest --no-daemon --max-workers=2 --console=plain --stacktrace 2>&1 | tee tv-build.log
gradle_status=${PIPESTATUS[0]}
mkdir -p app/build
adb exec-out run-as com.smarted.tv.debug cat files/tv-player.png > app/build/tv-player.png || true
adb logcat -d > app/build/tv-logcat.txt || true
exit "$gradle_status"
