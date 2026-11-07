#!/usr/bin/env python3
"""Gera um arquivo SQLite com o esquema de uma versão anterior do app.

Serve para o teste de fumaça no emulador reproduzir o cenário que derrubou o
app: o usuário atualiza o APK, o banco local continua com o esquema antigo e,
se faltar migração, o Room mata o app na abertura.

O esquema vem de `tools/db-schema.baseline.json` (a mesma fonte do
check-db-schema.py), então o banco gerado aqui é fiel ao que os usuários têm.

Uso:
    python3 tools/make-legacy-db.py saida.db [versao]
"""

from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASELINE = ROOT / "tools/db-schema.baseline.json"

# Chaves primárias de cada tabela (o baseline guarda só nome/tipo/nulidade).
PRIMARY_KEYS = {
    "repositories": ["id"],
    "installed_addons": ["id"],
    "favorites": ["refKey"],
    "watch_progress": ["refKey"],
    "catalog_cache": ["key"],
    "public_sources": ["id"],
    "hidden_categories": ["addonId", "kind", "name"],
    "epg_sources": ["url"],
    "search_history": ["query"],
}


def column_sql(entry: str) -> str:
    parts = entry.split(" ")
    not_null = parts[-2:] == ["NOT", "NULL"]
    if not_null:
        parts = parts[:-2]
    name, sql_type = parts[0], " ".join(parts[1:])
    piece = f'"{name}" {sql_type}'
    if not_null:
        piece += " NOT NULL"
    return piece


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    target = Path(sys.argv[1])
    version = int(sys.argv[2]) if len(sys.argv) > 2 else 1

    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    tables = baseline["versions"][str(version)]

    if target.exists():
        target.unlink()
    con = sqlite3.connect(target)
    try:
        for table, columns in tables.items():
            defs = [column_sql(entry) for entry in columns]
            keys = PRIMARY_KEYS.get(table)
            if keys:
                defs.append("PRIMARY KEY(" + ", ".join(f'"{k}"' for k in keys) + ")")
            con.execute(f'CREATE TABLE "{table}" ({", ".join(defs)})')
        con.execute(f"PRAGMA user_version = {version}")
        con.commit()
    finally:
        con.close()

    print(f"{target}: esquema v{version}, {len(tables)} tabela(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
