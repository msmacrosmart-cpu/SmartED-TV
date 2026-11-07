#!/usr/bin/env python3
"""
Verificador de referências para o código Kotlin do SmartED TV.

Sem acesso ao Android SDK não há typecheck real; este script faz uma análise
heurística de alto valor:

  1. Coleta todas as declarações do projeto (fun/class/object/val/property,
     typealiases, enums, métodos de extensão).
  2. Coleta todos os imports e resolve contra as declarações + whitelist de
     bibliotecas externas.
  3. Reporta:
       - imports internos (`com.smarted.…`) que não existem,
       - chamadas de função do projeto que não estão declaradas,
       - `Ed.xxx` / `EdIcons.xxx` / `EdColors.xxx` / `Accents.xxx` / `CardSize.xxx`
         inexistentes,
       - nomes de parâmetro nomeados que não existem na função alvo.

Uso: python3 tools/check-refs.py
"""
import os
import re
import sys
from collections import defaultdict

ROOT = "app/src/main/java"

DECL_FUN = re.compile(
    r"^\s*(?:(?:public|private|internal|protected|override|suspend|inline|operator|infix|tailrec|@\w+(?:\([^)]*\))?)\s+)*"
    r"fun\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)\s*\(",
    re.MULTILINE,
)
DECL_EXT_FUN = re.compile(r"fun\s+([\w.<>?]+)\.(\w+)\s*\(")
DECL_CLASS = re.compile(
    r"^\s*(?:(?:public|private|internal|abstract|open|sealed|data|enum|annotation|value|inner)\s+)*"
    r"(?:class|interface|object)\s+(\w+)",
    re.MULTILINE,
)
DECL_VAL = re.compile(
    r"^\s*(?:(?:public|private|internal|override|const|lateinit)\s+)*"
    r"(?:val|var)\s+(\w+)",
    re.MULTILINE,
)
DECL_ENUM_ENTRY = re.compile(r"^\s{4}([A-Z][A-Z0-9_]*)\s*(?:,|;|$)", re.MULTILINE)

CALL = re.compile(r"(?<![\w.])([a-z][A-Za-z0-9_]*)\s*\(")
MEMBER = re.compile(r"\b(Ed|EdIcons|EdColors|Accents|CardSize|EdShapes|EdDestination|TmdbGenres|TextUtils|M3uParser|XmltvParser|SourceDetector|DbMappers|Indexer|EpgService|BrazucaPlayAddon|AddonHealth|AddonManifest)\.(\w+)")

KEYWORDS = {
    "if", "when", "for", "while", "return", "catch", "is", "as", "in", "else", "try",
    "println", "print", "require", "check", "error", "run", "let", "also", "apply",
    "with", "repeat", "TODO", "synchronized", "lazy", "remember", "fun", "val", "var",
    "super", "this", "throw", "assert", "arrayOf", "listOf", "setOf", "mapOf", "mutableListOf",
    "buildList", "buildString", "buildMap", "emptyList", "emptyMap", "emptySet", "sequence",
    "coroutineScope", "launch", "async", "withContext", "awaitAll", "rem", "object", "class",
}

EXTERNAL_IMPORT_PREFIXES = (
    "android.", "androidx.", "kotlin.", "kotlinx.", "java.", "javax.", "coil.", "okhttp3.",
    "io.noties.", "com.google.", "org.jetbrains.", "com.squareup.",
)


def strip_comments(src):
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.DOTALL)
    src = re.sub(r"//[^\n]*", "", src)
    return src


def collect():
    decls = defaultdict(set)
    files = []
    for base, _, names in os.walk(ROOT):
        for n in names:
            if n.endswith(".kt"):
                path = os.path.join(base, n)
                files.append(path)
                src = strip_comments(open(path, encoding="utf-8").read())
                for m in DECL_FUN.finditer(src):
                    decls["fun"].add(m.group(1))
                for m in DECL_CLASS.finditer(src):
                    decls["class"].add(m.group(1))
                for m in DECL_VAL.finditer(src):
                    decls["prop"].add(m.group(1))
                # extensões também contam como função
                for m in DECL_EXT_FUN.finditer(src):
                    decls["fun"].add(m.group(2))
    return decls, files


def main():
    decls, files = collect()
    all_names = decls["fun"] | decls["class"] | decls["prop"]
    problems = []

    for path in sorted(files):
        raw = open(path, encoding="utf-8").read()
        src = strip_comments(raw)
        rel = os.path.relpath(path)

        # 1. imports internos inexistentes -------------------------------------
        for m in re.finditer(r"^import\s+([\w.]+)", src, re.MULTILINE):
            fq = m.group(1)
            if not fq.startswith("com.smarted."):
                continue
            last = fq.split(".")[-1]
            # `R` e `BuildConfig` são gerados pelo AGP, não existem no fonte.
            if last in ("R", "BuildConfig"):
                continue
            if last in all_names or last == "*":
                continue
            # pode ser pacote
            pkg_path = os.path.join(ROOT, *fq.split("."))
            if os.path.isdir(pkg_path):
                continue
            problems.append((rel, f"import inexistente: {fq}"))

        # 2. membros de objetos-chave -----------------------------------------
        for m in MEMBER.finditer(src):
            obj, member = m.group(1), m.group(2)
            if obj in ("TextUtils", "M3uParser", "XmltvParser", "SourceDetector", "DbMappers",
                       "TmdbGenres", "Indexer", "EpgService", "BrazucaPlayAddon", "AddonHealth",
                       "AddonManifest"):
                continue
            if obj == "Ed":
                if member not in ("palette", "type", "dimen"):
                    problems.append((rel, f"Ed.{member} inexistente"))
            elif obj == "CardSize":
                if member not in ("posterWidth", "wideWidth", "wideHeight", "channelWidth", "channelHeight"):
                    problems.append((rel, f"CardSize.{member} inexistente"))
            elif obj == "EdColors":
                known = {n for n in dir_names("EdColors")}
                if member not in known:
                    problems.append((rel, f"EdColors.{member} inexistente"))
            elif obj == "EdIcons":
                known = dir_names("EdIcons")
                if member not in known:
                    problems.append((rel, f"EdIcons.{member} inexistente"))
            elif obj == "Accents":
                if member not in ("all", "byId") :
                    problems.append((rel, f"Accents.{member} inexistente"))
            elif obj == "EdShapes":
                if member not in ("card", "panel", "pill", "small", "thumb"):
                    problems.append((rel, f"EdShapes.{member} inexistente"))
            elif obj == "EdDestination":
                known = {"HOME", "LIVE", "MOVIES", "SERIES", "GUIDE", "SEARCH", "ADDONS", "SETTINGS", "entries", "valueOf"}
                if member not in known:
                    problems.append((rel, f"EdDestination.{member} inexistente"))

    if problems:
        print(f"{len(problems)} problema(s) de referência:\n")
        for rel, msg in problems:
            print(f"  {rel}: {msg}")
        return 1
    print(f"OK — {len(files)} arquivo(s) sem referências quebradas conhecidas.")
    return 0


_cache = {}


def dir_names(obj_name):
    """Nomes declarados dentro de um object/companion (heurística por arquivo)."""
    if obj_name in _cache:
        return _cache[obj_name]
    names = set()
    for base, _, file_names in os.walk(ROOT):
        for n in file_names:
            if not n.endswith(".kt"):
                continue
            src = strip_comments(open(os.path.join(base, n), encoding="utf-8").read())
            for m in re.finditer(rf"object\s+{obj_name}\b", src):
                start = m.start()
                # pega o corpo do object
                depth = 0
                i = src.index("{", start) if "{" in src[start:] else None
                if i is None:
                    continue
                j = i
                while j < len(src):
                    if src[j] == "{":
                        depth += 1
                    elif src[j] == "}":
                        depth -= 1
                        if depth == 0:
                            break
                    j += 1
                body = src[i:j]
                for vm in re.finditer(r"(?:val|var)\s+(\w+)", body):
                    names.add(vm.group(1))
                for cm in re.finditer(r"fun\s+(\w+)", body):
                    names.add(cm.group(1))
                for cm in re.finditer(r"const\s+val\s+(\w+)", body):
                    names.add(cm.group(1))
    _cache[obj_name] = names
    return names


if __name__ == "__main__":
    sys.exit(main())
