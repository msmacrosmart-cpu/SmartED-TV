#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# verify-core.sh — compila e testa o núcleo puro (sem Android) do SmartED TV.
#
# O código de parsing (M3U, XMLTV, repositório Kodi), utilitários de texto e
# modelos não depende do Android SDK, então dá para compilar e executar testes
# reais em qualquer JVM — é o que este script faz.
#
# Requisitos: JDK 17+ e kotlinc (ou o pacote npm `kotlin-compiler`).
# Uso: ./tools/verify-core.sh
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/smarted-core-verify"
KOTLINC="${KOTLINC:-kotlinc}"
if ! command -v "$KOTLINC" >/dev/null 2>&1; then
  if [ -x "$ROOT/node_modules/kotlin-compiler/bin/kotlinc" ]; then
    KOTLINC="$ROOT/node_modules/kotlin-compiler/bin/kotlinc"
  elif [ -x "/tmp/kc/node_modules/kotlin-compiler/bin/kotlinc" ]; then
    KOTLINC="/tmp/kc/node_modules/kotlin-compiler/bin/kotlinc"
  else
    echo "kotlinc não encontrado. Instale o Kotlin ou rode: npm i kotlin-compiler" >&2
    exit 2
  fi
fi

rm -rf "$WORK"
mkdir -p "$WORK/src" "$WORK/out"

# Copia só o que não depende de Android/Room/Compose e remove anotações de
# serialização (o plugin é aplicado pelo Gradle no build real).
copy() {
  local rel="$1" dest="$2"
  mkdir -p "$WORK/src/$dest"
  sed -e 's/^import kotlinx\.serialization\..*$//' \
      -e 's/^import kotlinx\.serialization\.json\..*$//' \
      -e '/^@Serializable$/d' \
      -e 's/@SerialName([^)]*)//g' \
      "$ROOT/app/src/main/java/com/smarted/tv/$rel" >"$WORK/src/$dest/$(basename "$rel")"
}

copy core/model/Catalog.kt core/model
copy core/util/TextUtils.kt core/util
copy core/util/Parsers.kt core/util
copy core/util/LanguageFilter.kt core/util
copy core/epg/EpgTimeline.kt core/epg
copy core/addons/Addon.kt core/addons
copy core/addons/KodiRepo.kt core/addons

# Arquivo de teste (main com asserções) --------------------------------------
cp "$ROOT/app/src/test/kotlin/CoreVerify.kt" "$WORK/src/CoreVerify.kt"

echo "→ compilando ($KOTLINC)"
"$KOTLINC" "$WORK/src" -include-runtime -d "$WORK/core.jar" 2>&1 | grep -v "^warning:" || true

echo "→ executando testes"
if [ -x "${JAVA_HOME:-}/bin/java" ]; then
  "${JAVA_HOME}/bin/java" -jar "$WORK/core.jar"
else
  java -jar "$WORK/core.jar"
fi
