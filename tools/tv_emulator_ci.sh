#!/usr/bin/env bash
# Boot de um emulador Android TV no GitHub Actions com diagnóstico em annotations
# (o console do runner não é acessível externamente; só annotations passam).
set -uo pipefail

em_note() { # $1 title, $2 body
  local enc
  enc=$(printf '%s' "$2" | python3 -c "import sys;t=sys.stdin.read()[-3400:];sys.stdout.write(t.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))")
  echo "::error title=$1::$enc"
}

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
# cmdline-tools novos gravam AVDs em ~/.config/.android enquanto o `emulator` lê ~/.android —
# unificar no caminho legado ou o AVD criado "não existe" para o boot.
export ANDROID_USER_HOME="$HOME/.android"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
CLI="$SDK/cmdline-tools/latest/bin"
[ -x "$CLI/sdkmanager" ] || CLI="$(dirname "$(find "$SDK/cmdline-tools" -maxdepth 4 -name sdkmanager | head -1)")"

yes | "$CLI/sdkmanager" --licenses >/dev/null 2>&1 || true

# Imagens de sistema possíveis para TV x86_64; cai para imagem comum com perfil tv_1080p
# se a android-tv não existir no canal estável — os testes sobem a Activity direto.
CANDIDATES=(
  "system-images;android-30;android-tv;x86_64"
  "system-images;android-33;android-tv;x86_64"
  "system-images;android-34;android-tv;x86_64"
  "system-images;android-34;google_apis;x86_64"
)
CHOSEN=""
for img in "${CANDIDATES[@]}"; do
  if "$CLI/sdkmanager" --install "platform-tools" "emulator" "$img" > /tmp/sdkinstall.log 2>&1; then
    CHOSEN="$img"
    break
  fi
done
if [ -z "$CHOSEN" ]; then
  em_note "sdkmanager-fail" "$(tail -c 3000 /tmp/sdkinstall.log)"
  exit 1
fi
echo "using system image: $CHOSEN"

"$CLI/avdmanager" delete avd -n smartedtvt >/dev/null 2>&1 || true
printf 'no\n' | "$CLI/avdmanager" create avd -n smartedtvt -k "$CHOSEN" -d tv_1080p > /tmp/avdcreate.log 2>&1 || {
  em_note "avd-create-fail" "$(tail -c 3000 /tmp/avdcreate.log)"
  exit 1
}

"$SDK/emulator/emulator" -avd smartedtvt -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > /tmp/emulator-boot.log 2>&1 &
EMU_PID=$!

cleanup() { kill "$EMU_PID" 2>/dev/null || true; wait "$EMU_PID" 2>/dev/null || true; }
trap cleanup EXIT

if ! timeout 240 "$SDK/platform-tools/adb" wait-for-device; then
  em_note "adb-wait-fail" "$(tail -c 3000 /tmp/emulator-boot.log)"
  exit 1
fi

BOOT=""
for _ in $(seq 1 80); do
  BOOT=$("$SDK/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  [ "$BOOT" = "1" ] && break
  sleep 5
done
if [ "$BOOT" != "1" ]; then
  em_note "boot-timeout" "$(tail -c 3000 /tmp/emulator-boot.log)"
  exit 1
fi
echo "device booted ($CHOSEN)"

bash tools/run_tv_tests.sh
RC=$?
if [ "$RC" -ne 0 ]; then
  em_note "instrumentation-fail" "$(tail -c 3000 tv-build.log 2>/dev/null)"
  "$SDK/platform-tools/adb" logcat -d -t 400 > /tmp/tv-logcat-tail.txt 2>/dev/null || true
  em_note "logcat-tail" "$(tail -c 3000 /tmp/tv-logcat-tail.txt 2>/dev/null)"
fi
exit "$RC"
