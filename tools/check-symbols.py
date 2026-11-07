#!/usr/bin/env python3
"""
check-symbols.py — verifica se símbolos usados no corpo dos arquivos têm o
import correspondente.

Motivação: um script de limpeza de imports (heurística por regex) removeu 258
imports legítimos porque não reconhecia usos como `16.dp` ou `Modifier.background()`.
Este verificador é a rede de segurança para esse tipo de erro: se o arquivo usa
`3.dp`, `.height(`, `.border(` etc., o import precisa existir.

Só cobre símbolos cujo uso é inequívoco (sufixo `.dp`, chamada `.metodo(`) e que
têm um único pacote de origem conhecido. Não tenta resolver nomes ambíguos.

Uso:  python3 tools/check-symbols.py
Saída: 0 se tudo certo; 1 se houver problema.
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java")

# símbolo -> (pacote, padrão de uso no corpo)
CHECKS = {
    "dp": ("androidx.compose.ui.unit.dp", r"\d\s*\.\s*dp\b"),
    "sp": ("androidx.compose.ui.unit.sp", r"\d\s*\.\s*sp\b"),
    "background": ("androidx.compose.foundation.background", r"\.\s*background\s*\("),
    "border": ("androidx.compose.foundation.border", r"\.\s*border\s*\("),
    "padding": ("androidx.compose.foundation.layout.padding", r"\.\s*padding\s*\("),
    "width": ("androidx.compose.foundation.layout.width", r"\.\s*width\s*\("),
    "height": ("androidx.compose.foundation.layout.height", r"\.\s*height\s*\("),
    "size": ("androidx.compose.foundation.layout.size", r"\.\s*size\s*\("),
    "offset": ("androidx.compose.foundation.layout.offset", r"\.\s*offset\s*\("),
    "fillMaxSize": ("androidx.compose.foundation.layout.fillMaxSize", r"\.\s*fillMaxSize\s*\("),
    "fillMaxWidth": ("androidx.compose.foundation.layout.fillMaxWidth", r"\.\s*fillMaxWidth\s*\("),
    "fillMaxHeight": ("androidx.compose.foundation.layout.fillMaxHeight", r"\.\s*fillMaxHeight\s*\("),
    "clip": ("androidx.compose.ui.draw.clip", r"\.\s*clip\s*\("),
    "alpha": ("androidx.compose.ui.draw.alpha", r"\.\s*alpha\s*\("),
    "aspectRatio": ("androidx.compose.foundation.layout.aspectRatio", r"\.\s*aspectRatio\s*\("),
    "scale": ("androidx.compose.ui.draw.scale", r"\.\s*scale\s*\("),
}


# ---------------------------------------------------------------------------
# Chamada de função de EXTENSÃO por nome totalmente qualificado.
#
# Kotlin aceita `androidx.compose.material3.Text(...)` (função top-level), mas
# NÃO aceita `androidx.compose.ui.composed { }` — `composed` é `fun Modifier.composed`,
# e a sintaxe de FQN só vale para funções sem receptor. O compilador responde
# "Unresolved reference 'composed'", o que custou três rodadas de CI.
# ---------------------------------------------------------------------------
EXTENSION_FQNS = {
    "androidx.compose.ui.composed",
    "androidx.compose.ui.draw.clip",
    "androidx.compose.ui.draw.alpha",
    "androidx.compose.ui.draw.scale",
    "androidx.compose.foundation.border",
    "androidx.compose.foundation.background",
    "androidx.compose.foundation.clickable",
    "androidx.compose.foundation.layout.padding",
    "androidx.compose.foundation.layout.width",
    "androidx.compose.foundation.layout.height",
    "androidx.compose.foundation.layout.size",
    "androidx.compose.foundation.layout.offset",
    "androidx.compose.foundation.layout.aspectRatio",
    "androidx.compose.foundation.layout.fillMaxSize",
    "androidx.compose.foundation.layout.fillMaxWidth",
    "androidx.compose.foundation.layout.fillMaxHeight",
    "androidx.compose.foundation.layout.weight",
    "androidx.compose.foundation.lazy.items",
    "androidx.compose.foundation.lazy.itemsIndexed",
    "androidx.compose.foundation.lazy.grid.items",
    "androidx.compose.foundation.lazy.grid.itemsIndexed",
    "androidx.compose.ui.input.key.onKeyEvent",
    "androidx.compose.ui.input.key.onPreviewKeyEvent",
    "androidx.compose.ui.focus.focusRequester",
    "androidx.compose.ui.focus.focusable",
    "androidx.compose.ui.focus.onFocusChanged",
}


def check_extension_fqn(files):
    """Aponta `pkg.extensao(` usado por FQN — sempre erro de compilação."""
    problems = []
    for path in files:
        source = open(path, encoding="utf-8").read()
        for number, line in enumerate(source.split("\n"), 1):
            if line.strip().startswith("import ") or line.strip().startswith("//"):
                continue
            for fqn in EXTENSION_FQNS:
                if re.search(r"(?<![\w.])" + re.escape(fqn) + r"\s*[({]", line):
                    problems.append((os.path.relpath(path, ROOT), number, fqn))
    return problems



# ---------------------------------------------------------------------------
# APIs de Compose Desktop usadas por engano no Android.
#
# `androidx.compose.ui.input.key.KeyEvent` no Android embrulha um
# `android.view.KeyEvent` e expõe `.nativeKeyEvent`. Já `.type` e
# `.key`/`KeyEventType` vêm do Compose Desktop (GLFW); importar
# `androidx.compose.ui.input.key.type` compila em desktop e falha no Android.
# ---------------------------------------------------------------------------
DESKTOP_ONLY = {
    "androidx.compose.ui.input.key.type": "use event.nativeKeyEvent.action",
    "androidx.compose.ui.input.key.KeyEventType": "use android.view.KeyEvent.ACTION_DOWN",
    # nativeKeyEvent é 'val' membro de KeyEvent: não é importável
    "androidx.compose.ui.input.key.nativeKeyEvent": "val membro; não precisa de import",
}
DESKTOP_PATTERNS = [
    (r"\bevent\.type\b", "KeyEvent do Android não tem .type; use event.nativeKeyEvent.action"),
]


def check_desktop_api(files):
    problems = []
    for path in files:
        source = open(path, encoding="utf-8").read()
        for number, line in enumerate(source.split("\n"), 1):
            stripped = line.strip()
            if stripped.startswith("//"):
                continue
            for imp, hint in DESKTOP_ONLY.items():
                if stripped.startswith("import " + imp):
                    problems.append((os.path.relpath(path, ROOT), number, f"API de desktop: {imp} ({hint})"))
            if stripped.startswith("import "):
                continue
            for pattern, hint in DESKTOP_PATTERNS:
                if re.search(pattern, line):
                    problems.append((os.path.relpath(path, ROOT), number, hint))
    return problems


def main() -> int:
    problems = []
    files = sorted(glob.glob(os.path.join(SRC, "**", "*.kt"), recursive=True))

    for path in files:
        source = open(path, encoding="utf-8").read()
        lines = source.split("\n")
        body = "\n".join(l for l in lines if not l.strip().startswith("import "))
        imports = set(re.findall(r"^import\s+([\w.]+)", source, re.M))
        rel = os.path.relpath(path, ROOT)

        for symbol, (package, pattern) in CHECKS.items():
            if not re.search(pattern, body):
                continue
            if package in imports:
                continue
            # já usado de forma qualificada (ex.: androidx.compose.foundation.border(...))
            # o pacote já inclui o nome do símbolo (…foundation.border.border não existe)
            quoted = r"(?:^|[\s.(])" + re.escape(package)
            if re.search(quoted, body, re.M):
                continue
            problems.append((rel, symbol, package))

    for rel, number, fqn in check_extension_fqn(files):
        problems.append((f"{rel}:{number}", fqn, "função de extensão chamada por FQN"))

    for rel, number, reason in check_desktop_api(files):
        problems.append((f"{rel}:{number}", "-", reason))

    print(f"{len(files)} arquivo(s) analisado(s)")
    if problems:
        print(f"\n{len(problems)} import(s) faltando:\n")
        for rel, symbol, package in problems:
            print(f"  {rel}\n     usa '{symbol}' → falta import {package}")
        return 1

    print("OK — símbolos usados têm imports correspondentes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
