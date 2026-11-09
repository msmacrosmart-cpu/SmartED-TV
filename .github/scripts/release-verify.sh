#!/bin/bash
# Lista as releases recentes e afirma a última (evita achar antiga por hábito).
set -uo pipefail
gh release list --limit 5
echo "── última release ──"
gh release view --json tagName,createdAt,isLatest \
  --jq '"tag=\(.tagName) latest=\(.isLatest) at=\(.createdAt)"'
