#!/bin/bash
# Extrai falhas do log de um run de emulador como annotations do GitHub.
# Uso: smoke-annotate.sh <run-id>
set -uo pipefail
id="${1:?uso: smoke-annotate.sh <run-id>}"
gh run view "$id" --log-failed 2>/dev/null \
  | grep -E "::error|FAILED|AssertionError|expected:|Process crashed|FATAL" \
  | head -60 | while IFS= read -r l; do echo "::error::${l#*:}"; done
