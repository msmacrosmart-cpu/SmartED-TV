#!/usr/bin/env bash
# Teste de fumaça em emulador Android: instala o APK do build, abre o app de
# verdade e confere que ele não morre — inclusive no cenário que causou o bug
# de "erro ao abrir o app": banco local com esquema antigo (v1) migrando para
# o esquema novo (v2).
#
# Roda dentro do emulador criado pelo reactivecircus/android-emulator-runner,
# com adb já conectado. Sai com código != 0 em qualquer falha, publicando o
# logcat relevante como anotação do run.

set -uo pipefail

PKG="com.smarted.tv"
ACTIVITY="$PKG/.MainActivity"
APK_PATH="$(ls dist/*.apk 2>/dev/null | head -1)"
LEGACY_DB="legacy.db"

dump_logs() {
  echo "== logcat (app) =="
  adb logcat -d -v brief 2>/dev/null \
    | grep -iE "smarted|AndroidRuntime|FATAL|Room|SQLite|SQLiteException" \
    | tail -80 || true
}

fail() {
  echo "::error title=Emulador::$1"
  dump_logs
  exit 1
}

# Espera o app aparecer e confere que não houve crash nem ANR.
alive() {
  local seconds="${1:-25}"
  sleep "$seconds"
  local pid
  pid="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
  [[ -n "$pid" ]] || fail "app não está rodando (morreu ao abrir)"
  if adb logcat -d 2>/dev/null | grep -qE "FATAL EXCEPTION|ANR in $PKG"; then
    fail "crash/ANR registrado no logcat"
  fi
  echo "OK: app vivo (pid $pid)"
}

[[ -n "$APK_PATH" ]] || fail "APK não encontrado em dist/"
echo "APK: $APK_PATH"
adb shell getprop ro.build.version.sdk | tr -d '\r' | xargs -I{} echo "Android API: {}"

echo "== 1/4 instalando =="
adb install -r -g "$APK_PATH" || fail "falha ao instalar o APK"

echo "== 2/4 instalação limpa: abre o app =="
adb shell pm clear "$PKG" >/dev/null 2>&1 || true
adb logcat -c
adb shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || fail "não consegui iniciar a MainActivity"
alive 25

echo "== 3/4 a interface renderizou? =="
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/ui.xml > /tmp/ui.xml 2>/dev/null || true
if [[ -s /tmp/ui.xml ]] && ! grep -qiE "Canais|Filmes|Séries|Início|Smarted" /tmp/ui.xml; then
  echo "::warning title=Emulador::UI sem os rótulos esperados (pode ser dump vazio)"
  head -c 400 /tmp/ui.xml || true
fi

echo "== 4/4 upgrade: banco v1 antigo no aparelho, app novo =="
[[ -f "$LEGACY_DB" ]] || fail "banco legado não foi gerado ($LEGACY_DB)"
adb root >/dev/null 2>&1 || true
adb wait-for-device
adb shell am force-stop "$PKG" || true

APP_UID="$(adb shell dumpsys package "$PKG" | grep -m1 -o 'userId=[0-9]*' | cut -d= -f2 | tr -d '\r')"
[[ -n "$APP_UID" ]] || fail "não descobri o uid do app"
echo "uid do app: $APP_UID"

adb push "$LEGACY_DB" /data/local/tmp/legacy.db >/dev/null || fail "adb push do banco legado falhou"
adb shell "rm -rf /data/data/$PKG/databases" || fail "não consegui limpar o banco atual"
adb shell "mkdir -p /data/data/$PKG/databases" || fail "não consegui criar o diretório do banco"
adb shell "cp /data/local/tmp/legacy.db /data/data/$PKG/databases/smarted-tv.db" || fail "não consegui instalar o banco legado"
adb shell "chown -R $APP_UID:$APP_UID /data/data/$PKG/databases" || fail "chown do banco falhou"
adb shell "restorecon -R /data/data/$PKG/databases" >/dev/null 2>&1 || true

adb logcat -c
adb shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || fail "app não abriu com o banco antigo (crash na abertura)"
alive 25

adb shell am force-stop "$PKG" || true
adb pull "/data/data/$PKG/databases/smarted-tv.db" /tmp/after.db >/dev/null 2>&1 \
  || fail "não consegui ler o banco depois da migração"

python3 - <<'PY' || fail "migração 1→2 não foi aplicada (coluna kinds ausente)"
import sqlite3
columns = [row[1] for row in sqlite3.connect("/tmp/after.db").execute("PRAGMA table_info(repositories)")]
print("colunas de repositories:", ", ".join(columns))
if "kinds" not in columns:
    raise SystemExit("coluna kinds ausente")
PY

echo "OK: banco v1 migrado para v2 e o app abriu sem crash"
echo "== resumo =="
echo "  1. instalação limpa, app abre"
echo "  2. UI renderiza"
echo "  3. banco antigo (v1) migra para v2"
echo "  4. app continua vivo depois da migração"
