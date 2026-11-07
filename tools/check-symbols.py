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
