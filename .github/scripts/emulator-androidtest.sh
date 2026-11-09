#!/bin/bash
# Testes de instrumentação do add-on no emulador: instala o ZIP REAL e confirma
# o fluxo de EXECUÇÃO (entrypoint/capacidade) — nunca listagem de arquivos.
set -uo pipefail

fail() { echo "::error::$1"; exit 1; }
cd "$(dirname "$0")/../.." || fail "cd raiz do repo"

echo "▶ connectedDebugAndroidTest (KodiInstallExecutionTest…)"
if ! ./gradlew :app:connectedDebugAndroidTest --no-daemon --max-workers=2 2>&1 | tee /tmp/androidtest.log; then
    echo "── falhas relevantes ──"
    grep -E "FAILED|AssertionError|expected:|not found|Process crashed|INSTRUMENTATION_STATUS: stack" /tmp/androidtest.log \
        | head -40 | while IFS= read -r l; do echo "::error::$l"; done
    fail "instrumentação do add-on falhou"
fi
echo "GREEN: execução do add-on verificada no emulador."
