#!/usr/bin/env python3
"""
Lint de imports/símbolos para os arquivos Kotlin do SmartED TV.

O compilador do Gradle resolve imports; aqui não temos o SDK. Este script
garante que todo símbolo de framework usado no arquivo tenha o import
correspondente — o erro mais comum em código Compose escrito à mão.

Uso: python3 tools/check-imports.py
"""
import os
import re
import sys

ROOT = "app/src/main/java"

# símbolo usado -> import necessário
SYMBOLS = {
    # unidade / dimensões
    ".sp": "androidx.compose.ui.unit",     # família: basta um import
    ".dp": "androidx.compose.ui.unit",     # família: basta um import
    ".em": "androidx.compose.ui.unit",     # família: basta um import
    "TextUnit": "androidx.compose.ui.unit",
    "Dp": "androidx.compose.ui.unit",
    # layout
    "Arrangement.": "androidx.compose.foundation.layout.Arrangement",
    # alinhamento
    "Alignment.": "androidx.compose.ui.Alignment",
    "Alignment": "androidx.compose.ui.Alignment",
    # tipografia
    "FontFamily": "androidx.compose.ui.text.font.FontFamily",
    "TextAlign": "androidx.compose.ui.text.style.TextAlign",
    # desenho
    "Brush.": "androidx.compose.ui.graphics.Brush",
    "SolidColor": "androidx.compose.ui.graphics.SolidColor",
    # modificadores
    ".background(": "androidx.compose.foundation.background",
    ".clickable": "androidx.compose.foundation.clickable",
    ".border(": "androidx.compose.foundation.border",
    ".clip(": "androidx.compose.ui.draw.clip",
    ".scale(": "androidx.compose.ui.draw.scale",
    ".padding(": "androidx.compose.foundation.layout.padding",
    ".size(": "androidx.compose.foundation.layout.size",
    ".height(": "androidx.compose.foundation.layout.height",
    ".width(": "androidx.compose.foundation.layout.width",
    ".fillMaxWidth(": "androidx.compose.foundation.layout.fillMaxWidth",
    ".fillMaxHeight(": "androidx.compose.foundation.layout.fillMaxHeight",
    ".fillMaxSize(": "androidx.compose.foundation.layout.fillMaxSize",
    ".aspectRatio(": "androidx.compose.foundation.layout.aspectRatio",
    ".offset(": "androidx.compose.foundation.layout.offset",
    ".weight(": "androidx.compose.foundation.layout",  # família
    ".focusRequester(": "androidx.compose.ui.focus.focusRequester",
    ".onFocusChanged(": "androidx.compose.ui.focus.onFocusChanged",
    ".focusable(": "androidx.compose.foundation.focusable",
    # animação
    "animateFloatAsState": "androidx.compose.animation.core.animateFloatAsState",
    "animateColorAsState": "androidx.compose.animation.animateColorAsState",
    "animateDpAsState": "androidx.compose.animation.core.animateDpAsState",
    "tween(": "androidx.compose.animation.core.tween",
    # runtime
    # material
    # lazy
    # plataforma
    "Color.White": "androidx.compose.ui.graphics.Color",
    "Color.Black": "androidx.compose.ui.graphics.Color",
    "Color.Transparent": "androidx.compose.ui.graphics.Color",
    # utilidades
    "verticalArrangement": "androidx.compose.foundation.layout.Arrangement",
    "horizontalArrangement": "androidx.compose.foundation.layout.Arrangement",
}

# arquivos que declaram o símbolo (não precisam importar)
DECLARERS = ("ui/theme/EdTheme.kt",)


def walk():
    for base, _, names in os.walk(ROOT):
        for n in names:
            if n.endswith(".kt"):
                yield os.path.join(base, n)


def strip_comments(src):
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.DOTALL)
    src = re.sub(r"//[^\n]*", "", src)
    return src


def main():
    problems = []
    for path in walk():
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        if "/ui/" not in "/" + rel:
            continue
        raw = open(path, encoding="utf-8").read()
        src = strip_comments(raw)
        imports = set(re.findall(r"^import\s+([\w.]+)", src, re.M))
        wildcard = {i.rsplit(".", 1)[0] for i in imports if i.endswith(".*")}

        # imports do mesmo pacote nunca são necessários
        own_pkg = re.search(r"^package\s+([\w.]+)", src, re.M)
        own_pkg = own_pkg.group(1) if own_pkg else ""

        for symbol, required in SYMBOLS.items():
            if symbol not in src:
                continue
            pkg = required.rsplit(".", 1)[0]
            if required in imports or pkg in wildcard:
                continue
            # família (ex.: androidx.compose.ui.unit): qualquer import do pacote serve
            if any(i == pkg or i.startswith(pkg + ".") for i in imports):
                continue
            if own_pkg and pkg == own_pkg:
                continue
            if rel in DECLARERS and symbol in (".sp", ".dp", "Dp"):
                continue
            # símbolos declarados no próprio arquivo/objeto local
            short = required.rsplit(".", 1)[-1]
            if re.search(rf"\b(class|interface|object|fun)\s+{short}\b", src):
                continue
            problems.append((rel, symbol, required))

    if problems:
        print(f"{len(problems)} import(s) faltando:\n")
        for rel, symbol, required in problems:
            print(f"  {rel}: usa '{symbol}' → falta import {required}")
        return 1
    print("OK — imports consistentes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
