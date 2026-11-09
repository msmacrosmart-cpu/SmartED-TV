#!/usr/bin/env bash
# Smoke de emulador: instala o APK debug, inicia o MainActivity, pega screenshot
# e falha com ::error:: anotado (logs do job não são acessíveis do sandbox).
set -u

fail() {
  echo "::error::SMOKE_FAIL linha=$1 rc=$2 :: $3"
  exit 1
}

APK="app/build/outputs/apk/debug/app-debug.apk"
# buildType debug usa applicationIdSuffix ".debug"; classe em com.smarted.tv.
COMPONENT="com.smarted.tv.debug/com.smarted.tv.MainActivity"

[ -f "$APK" ] || fail "$LINENO" 1 "APK de debug ausente em $APK"

out="$(adb install -r "$APK" 2>&1)"; rc=$?
printf '%s\n' "$out"
case "$out" in
  *Success*) ;;
  *) fail "$LINENO" "$rc" "$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-600)" ;;
esac

out="$(adb shell am start -n "$COMPONENT" 2>&1)"; rc=$?
printf '%s\n' "$out"
case "$out" in
  *Error*|*"does not exist"*) fail "$LINENO" 1 "$(printf '%s' "$out" | tr '\n' ' ' | cut -c1-600)" ;;
esac

sleep 15
mkdir -p artifacts
adb exec-out screencap -p > artifacts/emulator-smoke.png || fail "$LINENO" 1 "screencap falhou"

if adb logcat -d | grep -qi "FATAL EXCEPTION"; then
  fail "$LINENO" 1 "CRASH: $(adb logcat -d | grep -A 40 'FATAL EXCEPTION' | tr '\n' ' ' | cut -c1-700)"
fi
echo "OK: app iniciou sem crash; screenshot salvo."
