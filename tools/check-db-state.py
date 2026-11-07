#!/usr/bin/env python3
"""Confere o estado de um arquivo SQLite do app (usado pelo teste do emulador).

Verifica a versão do esquema (`user_version`), se colunas obrigatórias existem e,
com `--marker`, se a linha marcadora gravada antes da migração sobreviveu —
prova de que a migração preservou os dados em vez de recriar o banco.

Uso:
    python3 tools/check-db-state.py <banco.db> <versao> [coluna ...] [--marker]
"""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

MARKER = "smoke-migracao-v1"


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    want_marker = "--marker" in sys.argv
    if len(args) < 2:
        print(__doc__)
        return 2

    path = Path(args[0])
    expected_version = int(args[1])
    required = args[2:]

    if not path.exists() or path.stat().st_size == 0:
        print(f"banco ausente ou vazio: {path}", file=sys.stderr)
        return 1

    con = sqlite3.connect(path)
    try:
        version = con.execute("PRAGMA user_version").fetchone()[0]
        columns = [row[1] for row in con.execute('PRAGMA table_info("repositories")')]
        print(f"user_version = {version} | repositories: {', '.join(columns)}")

        problems = []
        if version != expected_version:
            problems.append(f"user_version={version}, esperado {expected_version}")
        for column in required:
            if column not in columns:
                problems.append(f"coluna ausente: repositories.{column}")

        if want_marker:
            row = con.execute(
                'SELECT COUNT(*) FROM "search_history" WHERE "query" = ?', (MARKER,)
            ).fetchone()[0]
            print(f"linha marcadora preservada: {row}")
            if row != 1:
                problems.append("dados antigos perdidos — o banco foi recriado")
    finally:
        con.close()

    if problems:
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    print("OK — estado do banco confere.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
