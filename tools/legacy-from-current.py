#!/usr/bin/env python3
"""Deriva um banco "v1" a partir do banco real que o app acabou de criar.

Por que assim: reconstruir o esquema antigo à mão (a partir dos data classes)
erra detalhes que o Room valida na migração — índices, chaves compostas,
valores padrão. Derivar do banco real garante que o teste roda contra o mesmo
esquema que está no aparelho de quem atualiza o app: basta remover a coluna
introduzida na versão nova e voltar o `user_version`.

Também grava uma linha marcadora em `search_history` para provar que a
migração **preserva dados** (e que o app não recriou o banco do zero).

Uso:
    python3 tools/legacy-from-current.py <banco-v2.db> <saida-v1.db>
"""

from __future__ import annotations

import shutil
import sqlite3
import sys
from pathlib import Path

# Colunas que entraram depois da v1 — precisam sair para simular a versão 1.
DROPPED_COLUMNS = [("repositories", "kinds")]
# Tabelas que entraram depois da v1 (v3: lista do usuário).
DROPPED_TABLES = ["saved_items"]
MARKER_QUERY = "smoke-migracao-v1"


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    source, target = Path(sys.argv[1]), Path(sys.argv[2])

    if not source.exists() or source.stat().st_size == 0:
        raise SystemExit(f"banco de origem vazio ou ausente: {source}")

    shutil.copyfile(source, target)
    con = sqlite3.connect(target)
    try:
        for table, column in DROPPED_COLUMNS:
            columns = [row[1] for row in con.execute(f'PRAGMA table_info("{table}")')]
            if column in columns:
                con.execute(f'ALTER TABLE "{table}" DROP COLUMN "{column}"')
                print(f"removida {table}.{column} (esquema v1)")
        for table in DROPPED_TABLES:
            if con.execute(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", (table,)
            ).fetchone()[0]:
                con.execute(f'DROP TABLE "{table}"')
                print(f"removida tabela {table} (esquema v1)")
        con.execute("PRAGMA user_version = 1")
        con.execute(
            'INSERT OR REPLACE INTO "search_history" ("query", "usedAt") VALUES (?, ?)',
            (MARKER_QUERY, 1),
        )
        con.commit()
    finally:
        con.close()

    print(f"{target}: banco v1 derivado de {source}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
