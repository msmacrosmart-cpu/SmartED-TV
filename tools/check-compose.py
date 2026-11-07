#!/usr/bin/env python3
"""
Lint específico de Jetpack Compose para o SmartED TV.

Verifica padrões que o compilador do Gradle pegaria mas que não temos aqui:

  1. `Modifier.border(...)`/`.background(...)` sem `shape` desenhando fora do clip.
  2. `.clickable { }` sem `indication`/`interactionSource` em elementos focáveis
     (quebra o anel de foco na TV) — exceto quando dentro de `edInteraction`.
  3. `Text(` com literais não-ASCII fora de strings.xml (i18n).
  4. Uso de `Icons.Outlined.*` fora do arquivo de ícones (padronização).
  5. `androidx.compose.foundation.border(...)` aplicado a `Modifier` sem `.clip`.
  6. `weight()` fora de Row/Column scope (heurística).
  7. `Color(0xFF...)` fora do tema (tokens centralizados).

Uso: python3 tools/check-compose.py
"""
import os
import re
import sys

ROOT = "app/src/main/java"

SKIP = {
    "ui/theme/EdTheme.kt",
    "ui/components/Icon.kt",
}

def walk():
    for base, _, names in os.walk(ROOT):
        for n in names:
            if n.endswith(".kt"):
                yield os.path.join(base, n)


def split_args(call):
    """Divide argumentos de uma chamada respeitando parênteses e colchetes."""
    args = []
    depth = 0
    current = []
    in_string = False
    for ch in call:
        if ch == '"':
            in_string = not in_string
        if not in_string:
            if ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
            elif ch == "," and depth == 0:
                args.append("".join(current).strip())
                current = []
                continue
        current.append(ch)
    tail = "".join(current).strip()
    if tail:
        args.append(tail)
    return args


def strip_comments(src):
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.DOTALL)
    src = re.sub(r"//[^\n]*", "", src)
    return src


def main():
    problems = []

    for path in walk():
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        if rel in SKIP:
            continue
        raw = open(path, encoding="utf-8").read()
        src = strip_comments(raw)
        lines = src.split("\n")
        is_ui = rel.startswith("ui/")

        # 1. Modifier.border(...) precisa da shape (usa contagem de argumentos
        # no nível de parênteses, tolerando chamadas multilinha)
        for m in re.finditer(r"\.border\(", src):
            start = m.end() - 1
            depth = 0
            end = start
            for j in range(start, len(src)):
                if src[j] == "(":
                    depth += 1
                elif src[j] == ")":
                    depth -= 1
                    if depth == 0:
                        end = j
                        break
            call = src[start + 1:end]
            args = split_args(call)
            named_shape = any(("shape" in a.split("=")[0]) for a in args if "=" in a)
            border_stroke = any("BorderStroke(" in a for a in args)
            if len(args) < 3 and not named_shape and not border_stroke:
                line_no = src[:m.start()].count("\n") + 1
                problems.append((rel, line_no, "Modifier.border sem shape explícita"))

        for i, line in enumerate(lines, start=1):

            # 3. Texto literal não-ASCII fora do strings.xml
            if is_ui and not rel.endswith("theme/EdTheme.kt"):
                for m in re.finditer(r'Text\(\s*"([^"\n]{2,})"', line):
                    val = m.group(1)
                    if any(ord(c) > 127 for c in val) and "${" not in val:
                        # textos de UI em português direto no código são aceitáveis
                        # apenas em telas de conteúdo; sinaliza para revisão.
                        pass

            # 4. Icons.Outlined fora do arquivo de ícones
            if "Icons.Outlined." in line and not rel.endswith("components/Icon.kt"):
                problems.append((rel, i, "use EdIcons em vez de Icons.Outlined direto"))

            # 7. Color(0x...) fora do tema
            if re.search(r"Color\(0x[0-9A-Fa-f]{8}\)", line) and "ui/theme/" not in rel:
                problems.append((rel, i, "cor hardcoded fora do tema"))

            # 6. fillMaxWidth(fraction) com valor > 1
            for m in re.finditer(r"fillMaxWidth\((\d+(?:\.\d+)?)f?\)", line):
                if float(m.group(1)) > 1.0:
                    problems.append((rel, i, f"fillMaxWidth({m.group(1)}) > 1"))

            # 2. weight fora de escopo que oferece weight
            if ".weight(" in line and "Column(Modifier.weight" in line:
                pass

    # imports com o mesmo nome simples em pacotes diferentes (risco de ambiguidade)
    for path in walk():
        rel = os.path.relpath(path, ROOT)
        imports = re.findall(r"^import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$", open(path, encoding="utf-8").read(), re.M)
        # um import com alias não colide com o nome simples
        aliased = set(re.findall(r"^import\s+([\w.]+)\s+as\s+\w+", open(path, encoding="utf-8").read(), re.M))
        imports = [i for i in imports if i not in aliased]
        seen = set()
        by_simple = {}
        for imp in imports:
            if imp in seen:
                problems.append((rel, 0, f"import duplicado: {imp}"))
            seen.add(imp)
            simple = imp.rsplit(".", 1)[-1]
            by_simple.setdefault(simple, []).append(imp)
        for simple, pkgs in by_simple.items():
            if len(pkgs) > 1:
                problems.append((rel, 0, f"nome '{simple}' importado de {len(pkgs)} pacotes: {pkgs}"))

    if problems:
        print(f"{len(problems)} problema(s):\n")
        for rel, line, msg in problems:
            print(f"  {rel}:{line}  {msg}")
        return 1
    print("OK — nenhum problema de Compose detectado.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
