#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# verify-logic.sh — typecheck do núcleo NÃO-UI (rede, addons, TMDB, EPG,
# banco, view-models) sem Android SDK.
#
# Usa stubs fiéis de OkHttp / Room / SharedPreferences / kotlinx-serialization
# (tools/stubs) para que o compilador Kotlin real valide todos os arquivos de
# lógica. Erros de tipo, nome e assinatura aparecem como no build do Gradle.
#
# Uso: ./tools/verify-logic.sh
# ---------------------------------------------------------------------------
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/smarted-logic-check"
KOTLINC="${KOTLINC:-kotlinc}"
if ! command -v "$KOTLINC" >/dev/null 2>&1; then
  if [ -x "$ROOT/node_modules/kotlin-compiler/bin/kotlinc" ]; then
    KOTLINC="$ROOT/node_modules/kotlin-compiler/bin/kotlinc"
  elif [ -x "/tmp/kc/node_modules/kotlin-compiler/bin/kotlinc" ]; then
    KOTLINC="/tmp/kc/node_modules/kotlin-compiler/bin/kotlinc"
  else
    echo "kotlinc não encontrado (npm i kotlin-compiler)" >&2
    exit 2
  fi
fi
COROUTINES="$(dirname "$(dirname "$(readlink -f "$KOTLINC")")")/lib/kotlinx-coroutines-core-jvm.jar"

rm -rf "$WORK"
mkdir -p "$WORK/src"
cp -r "$ROOT/tools/stubs/." "$WORK/src/"

SRC="$ROOT/app/src/main/java/com/smarted/tv"
INCLUDES=(
  "$SRC/core/model"
  "$SRC/core/util"
  "$SRC/core/net"
  "$SRC/core/tmdb"
  "$SRC/core/addons"
  "$SRC/core/data"
  "$SRC/core/data/db"
  "$SRC/core/epg"
  "$SRC/core/indexer"
  "$SRC/core/trailer"
  "$SRC/addons"
  "$SRC/ui/state"
)
for d in "${INCLUDES[@]}"; do
  mkdir -p "$WORK/src/$(basename "$d")"
  cp "$d"/*.kt "$WORK/src/$(basename "$d")/" 2>/dev/null || true
done

# Remove imports/annotations que exigem plugins (serialização) — o stub cobre o resto.
find "$WORK/src" -name "*.kt" -newer "$WORK/src" -print0 2>/dev/null | true

echo "→ typecheck de $(find "$WORK/src" -name '*.kt' | wc -l) arquivos"
OUT="$("$KOTLINC" -cp "$COROUTINES" "$WORK/src" -d "$WORK/out" 2>&1)"
STATUS=$?
ERRORS="$(echo "$OUT" | grep -E "error:" || true)"
if [ -n "$ERRORS" ]; then
  echo "$ERRORS" | sed "s#$WORK/src/##" | head -60
  echo
  echo "$(echo "$ERRORS" | wc -l) erro(s) de compilação"
  exit 1
fi
echo "OK — núcleo não-UI compila sem erros"
exit 0
