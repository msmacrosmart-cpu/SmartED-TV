#!/bin/bash
# Gate autoritativo PRÉ-PUSH (local):
#  1) padrões banidos (magenta+hex, token smokedGlass, whiteFocus ≠ #FFFFFF);
#  2) núcleo JVM-pure (tools/check_architecture.py — também roda no CI);
#  3) NUL em recursos de teste; 4) balance {} () dos .kt alterados.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

fail=0

# 1) padrões banidos
hits=$(grep -rInE "magenta|#ff00ff|#00ff00|0xFF00FF" app/src --include='*.kt' || true)
if [ -n "$hits" ]; then echo "$hits"; echo "::error::padrão banido (magenta/hex)"; fail=1; fi
# token do dono único (ui/theme/Theme.kt) — vazar em outras camadas é proibido
hits=$(grep -rIn "smokedGlass" app/src --include='*.kt' | grep -v "ui/theme/Theme.kt" || true)
if [ -n "$hits" ]; then echo "$hits"; echo "::error::token 'smokedGlass' fora de Theme.kt"; fail=1; fi
# só linhas que definem uma COR perto de whiteFocus (evita comentários/declarações)
hits=$(grep -rInE "[wW]hiteFocus" app/src --include='*.kt' \
    | grep -E "#[0-9a-fA-F]{6}|0x[0-9A-Fa-f]{6}" | grep -viE "FFFFFF" || true)
if [ -n "$hits" ]; then echo "$hits"; echo "::error::whiteFocus fora de #FFFFFF"; fail=1; fi

# 2) arquitetura (identidade/camadas — núcleo JVM-pure)
python3 tools/check_architecture.py || { echo "::error::check_architecture falhou"; fail=1; }

# 3) NUL em recursos de teste (bytes)
while IFS= read -r f; do
    [ -f "$f" ] || continue
    if python3 -c "import sys; sys.exit(0 if b'\x00' in open(sys.argv[1],'rb').read() else 1)" "$f"; then
        echo "::error::NUL em $f"; fail=1
    fi
done < <(git ls-files 'app/src/test/resources/**' 'app/src/androidTest/assets/**' \
    | grep -E '\.(xml|json|txt|py|m3u|m3u8|pls|md|kodi|ini)$' || true)

# 4) balance dos .kt alterados (comentários // removidos antes de contar)
{
    git diff --name-only HEAD | grep '\.kt$' || true
    git ls-files --others --exclude-standard | grep '\.kt$' || true
} | sort -u | while IFS= read -r f; do
    [ -f "$f" ] || continue
    python3 - "$f" <<'PY' || exit 9
import re, sys
from pathlib import Path
t = Path(sys.argv[1]).read_text()
t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)  # comentários de bloco/KDoc
t = t.replace("://", "\x00")                  # protege URLs dentro de strings
t = re.sub(r"//.*", "", t)                     # comentários de linha
t = t.replace("\x00", "://")
b = t.count("{") - t.count("}")
p = t.count("(") - t.count(")")
if b != 0 or p != 0:
    print(f"::error::balance ruim {sys.argv[1]}: {{}}={b} ()={p}")
    sys.exit(1)
PY
done || fail=1

[ "$fail" -eq 0 ] && echo "GATE OK" || { echo "GATE FAIL"; exit 1; }
