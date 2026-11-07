#!/usr/bin/env python3
"""
check-ptbr.py — garante que a interface do Smarted TV continue 100% em pt-BR.

O app é pt-BR por decisão de produto. Este gate olha apenas para textos que o
usuário vê (parâmetros de texto em Compose e literais usados como conteúdo) e
aponta palavras de inglês que costumam vazar de código/placeholder para a tela.

Não é um tradutor automático: é uma trava de regressão. Falsos positivos são
resolvidos com uma entrada em ALLOW (por exemplo, nomes próprios e formatos
técnicos que devem mesmo aparecer: "4K", "EPG", "VLC").
"""

import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java")

# Palavras que denunciam texto em inglês na UI. Deliberadamente curtas na
# origem e checadas por palavra inteira, para não pegar "live" dentro de
# "delivery" nem "top" dentro de "stop".
ENGLISH = {
    # artigos, preposições e conectores
    # ("a", "e", "o" são portuguesas; não entram no dicionário)
    "the", "an", "with", "from", "this", "that", "these", "those",
    "your", "you", "its", "their", "are", "and", "but", "not",
    "have", "has", "will", "would", "can", "could", "should", "when",
    "what", "which", "about", "into", "over", "under", "than", "then",
    "there", "here", "where", "why", "how", "who", "much", "many",
    "most", "more", "less", "very", "also", "just", "only", "some",
    # substantivos e verbos típicos de media center
    "movie", "movies", "show", "shows", "season", "seasons", "episode",
    "episodes", "watch", "watching", "loading", "error", "errors",
    "settings", "setting", "search", "home", "library", "list", "lists",
    "favorites", "favourites", "continue", "next", "previous", "prev",
    "close", "open", "save", "cancel", "back", "delete", "remove", "add",
    "download", "downloads", "update", "updates", "available", "unavailable",
    "source", "sources", "channel", "channels", "genre", "genres",
    "filter", "filters", "language", "languages", "country", "quality",
    "resolution", "duration", "unknown", "none", "empty", "retry",
    "refresh", "sync", "setup", "guide", "player", "track", "tracks",
    "subtitle", "subtitles", "audio", "volume", "mute", "live", "details",
    "warning", "history", "recent", "popular", "trending", "top", "rated",
    "vote", "votes", "voted", "year", "years", "runtime", "overview",
    "cast", "crew", "similar", "recommended", "playing", "resume", "pause",
    "stop", "account", "network", "storage", "select", "selected", "new",
    "edit", "all", "today", "yesterday", "tomorrow", "sign", "login",
    "logout", "password", "username", "email", "profile", "connected",
    "disconnected", "enabled", "disabled", "success", "failed", "failure",
    "try", "again", "done", "ready", "none", "found", "results", "result",
}

# Textos técnicos/nomes próprios que aparecem de propósito.
ALLOW = {
    "ok", "hd", "4k", "8k", "epg", "xmltv", "m3u", "m3u8", "mpd", "vlc",
    "tmdb", "imdb", "api", "json", "html", "xml", "http", "https", "url",
    "id", "tv", "smarttv", "kodi", "compose", "kotlin", "exoplayer",
    "youtube", "android", "github", "vikings", "brazuca", "smarted",
    "top", "player", "switch", "track", "season", "live",
    "online", "offline", "smart", "max", "hbo", "disney", "netflix",
    "globoplay", "prime", "video", "app", "apk", "dpad", "d-pad",
}

# Parâmetros Compose cujo valor é, por definição, texto visível ao usuário.
# `title`, `subtitle`, `key`, `route` e afins ficam de fora de propósito:
# são nomes de coluna/rota e usam minúsculas em inglês como identificador.
STRICT_PARAMS = re.compile(
    r"\b(?:text|label|message|overline|placeholder|hint|contentDescription|"
    r"eyebrow|trailing|emptyMessage|emptyTitle)\s*=\s*"
)
# Em `title`/`subtitle` só olhamos valores com cara de frase (maiúscula inicial
# ou mais de uma palavra), porque o mesmo parâmetro recebe nomes de coluna.
SOFT_PARAMS = re.compile(r"\b(?:title|subtitle)\s*=\s*")


def looks_like_ui_phrase(value: str) -> bool:
    text = value.strip()
    if not text or "{" in text and "$" not in text:
        return False
    if re.match(r"^[A-ZÀ-Ý]", text):          # maiúscula inicial → frase
        return True
    return " " in text                         # mais de uma palavra → frase

LITERAL = re.compile(r'"([^"\\\n]{2,120})"')

# Strings que são chaves técnicas (animações, listas, tags) e nunca chegam à tela.
KEY_LITERAL = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)+$")

# Linhas que constroem texto na tela (mesmo dentro de if/else inline).
UI_CALL_LINE = re.compile(
    r"\b(?:Text|ScreenTitle|EmptyState|EdPill|EdBadge|EdChip|EdTextField|"
    r"SearchField|SettingRow|SectionHeader|Rail|EdSecondaryButton|"
    r"EdPrimaryButton)\s*\("
)

# Linhas que não representam texto de UI.
SKIP = re.compile(
    r"^\s*(//|\*|/\*|import |package |@|name\s*=\s*\"|key\s*=\s*\")"
)

# Falsos positivos estruturais: chaves de lista, ids, tags de animação.
NON_TEXT_HINTS = (
    "theme/", "Icons.", "EdIcons.", "R.", "BuildConfig", "android.",
    "com.", "http", "www.", "${",
)


def words(text):
    return re.findall(r"[A-Za-zÀ-ÿ']+", text.lower())


def scan_file(path):
    """Devolve [(linha, texto, palavras_inglesas)] para um arquivo de UI."""
    problems = []
    with open(path, encoding="utf-8") as fh:
        lines = fh.read().split("\n")

    for number, line in enumerate(lines, 1):
        if SKIP.match(line):
            continue

        current = line.rstrip()

        # Qual parâmetro rege esta linha?
        #   strict → `text =`, `label =`, `message =` … valor é texto de tela
        #   soft   → `title =`, `subtitle =` … pode ser texto OU chave de coluna
        param = None
        if STRICT_PARAMS.search(current):
            param = "strict"
        elif SOFT_PARAMS.search(current):
            param = "soft"
        elif current.lstrip().startswith('"') and number >= 2:
            previous = lines[number - 2].rstrip()
            if STRICT_PARAMS.search(previous):
                param = "strict"
            elif SOFT_PARAMS.search(previous):
                param = "soft"

        for match in LITERAL.finditer(line):
            value = match.group(1).strip()
            if not value:
                continue
            if any(hint in value for hint in NON_TEXT_HINTS):
                continue
            # Identificadores: chaves de animação, listas e rotas de navegação.
            # ("switch-track", "hero-ring", "movies", "continue", "cast")
            if KEY_LITERAL.match(value) or re.fullmatch(r"[a-z0-9_.:-]+", value):
                continue

            if param is None:
                # Literal dentro de chamada de composição de texto. A chamada
                # pode estar algumas linhas acima (parâmetros multilinha).
                window = lines[max(0, number - 4):number]
                if not any(UI_CALL_LINE.search(w) for w in window):
                    continue
            elif param == "soft" and not looks_like_ui_phrase(value):
                continue

            hits = [w for w in words(value) if w in ENGLISH and w not in ALLOW]
            if hits:
                problems.append((number, value, hits))
    return problems


def missing_string_resources():
    """Todo `R.string.x` usado no código precisa existir em values/strings.xml.

    O compilador pegaria isso, mas só no job de build (que leva minutos). Aqui
    falha em segundos, junto das demais travas de texto.
    """
    import xml.etree.ElementTree as ET
    xml_path = os.path.join(ROOT, "app", "src", "main", "res", "values", "strings.xml")
    try:
        root = ET.parse(xml_path).getroot()
    except Exception as exc:  # arquivo ausente ou XML inválido
        print(f"ERRO ao ler {xml_path}: {exc}")
        return [("values/strings.xml", 0, "arquivo inválido", [])]
    declared = {el.get("name") for el in root.findall("string")}

    problems = []
    pattern = re.compile(r"R\.string\.(\w+)")
    for path in sorted(glob.glob(os.path.join(SRC, "**", "*.kt"), recursive=True)):
        with open(path, encoding="utf-8") as fh:
            for number, line in enumerate(fh, 1):
                if line.strip().startswith("//"):
                    continue
                for name in pattern.findall(line):
                    if name not in declared:
                        problems.append((path, number, f"R.string.{name}", ["não declarado"]))
    return problems


def main():
    files = sorted(glob.glob(os.path.join(SRC, "**", "*.kt"), recursive=True))
    files = [f for f in files if os.sep + "ui" + os.sep in f or f.endswith("MainActivity.kt")]

    problems = []
    for path in files:
        for number, value, hits in scan_file(path):
            problems.append((path, number, value, hits))
    problems.extend(missing_string_resources())

    if not problems:
        print(f"OK — {len(files)} arquivo(s) de UI sem texto em inglês e todos os R.string declarados.")
        return 0

    print(f"{len(problems)} texto(s) em inglês na interface:\n")
    for path, number, value, hits in problems:
        rel = os.path.relpath(path, ROOT)
        print(f"  {rel}:{number}")
        print(f"    \"{value}\"")
        print(f"    palavras: {', '.join(sorted(set(hits)))}")
        print()
    print("Traduza para pt-BR ou, se for nome próprio/termo técnico, adicione em ALLOW.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
