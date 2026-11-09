#!/bin/bash
# Confirma a conclusão de um run do emulador/build pelo ID (nunca `| tail; echo $?`).
set -uo pipefail
id="${1:?uso: smoke-verify.sh <run-id>}"
gh run view "$id" --json status,conclusion,displayTitle \
  --jq '"status=\(.status) conclusion=\(.conclusion) title=\(.displayTitle)"'
c=$(gh run view "$id" --json conclusion --jq .conclusion)
[ "$c" = "success" ] && echo "VERDE" || { echo "VERMELHO ($c)"; exit 1; }
