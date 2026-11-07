#!/usr/bin/env python3
"""Trava de esquema do Room.

Motivação: adicionar `repositories.kinds` sem subir a versão do banco derrubou o
app na abertura ("Room cannot verify the data integrity"). O build compilava
normal — o erro só aparecia no aparelho, com banco antigo. Este verificador
roda no CI, antes do build, e pega essa classe de erro em segundos:

1. Se o esquema das entidades mudou em relação ao baseline da versão atual,
   falha mandando subir `version` e escrever a migração.
2. Se a versão mudou, exige um `object : Migration(a, b)` para cada passo.
3. Executa o SQL das migrações num SQLite de verdade, partindo do esquema
   antigo, e confere que o resultado é exatamente o esquema novo.

Uso:
    python3 tools/check-db-schema.py            # valida
    python3 tools/check-db-schema.py --accept   # grava o esquema atual no baseline
"""

from __future__ import annotations

import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENTITIES = ROOT / "app/src/main/java/com/smarted/tv/core/data/db/Entities.kt"
DATABASE = ROOT / "app/src/main/java/com/smarted/tv/core/data/db/SmartEdDatabase.kt"
BASELINE = ROOT / "tools/db-schema.baseline.json"

SQL_TYPES = {
    "String": "TEXT",
    "Int": "INTEGER",
    "Long": "INTEGER",
    "Short": "INTEGER",
    "Byte": "INTEGER",
    "Boolean": "INTEGER",
    "Float": "REAL",
    "Double": "REAL",
    "ByteArray": "BLOB",
}

PROP = re.compile(r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*val\s+([A-Za-z_]\w*)\s*:\s*(.+?)\s*(?:=\s*.+)?$")


def balanced(text: str, start: int, open_ch: str = "(", close_ch: str = ")") -> int:
    """Índice do fechamento que casa com a abertura em `start`."""
    level = 0
    for i in range(start, len(text)):
        if text[i] == open_ch:
            level += 1
        elif text[i] == close_ch:
            level -= 1
            if level == 0:
                return i
    raise ValueError("parênteses não balanceados")


def parse_entities() -> dict[str, list[list]]:
    text = ENTITIES.read_text(encoding="utf-8")
    tables: dict[str, list[list]] = {}

    for match in re.finditer(r"@Entity\s*\(", text):
        head_open = match.end() - 1
        head_close = balanced(text, head_open)
        header = text[head_open:head_close]
        name = re.search(r'tableName\s*=\s*"([^"]+)"', header)
        if not name:
            continue
        table = name.group(1)

        body_start = text.find("(", head_close)
        if body_start < 0:
            continue
        body_end = balanced(text, body_start)
        body = text[body_start + 1:body_end]

        columns: list[list] = []
        for line in body.splitlines():
            line = line.split("//")[0]
            prop = PROP.match(line)
            if not prop:
                continue
            column, raw_type = prop.group(1), prop.group(2).strip().rstrip(",").strip()
            nullable = raw_type.endswith("?")
            base = raw_type.rstrip("?").strip()
            # List<String>/Map<...> só existem com TypeConverter (guardado como TEXT).
            if base.startswith(("List<", "Map<", "Set<")):
                sql_type = "TEXT"
            else:
                sql_type = SQL_TYPES.get(base)
                if sql_type is None:
                    raise SystemExit(
                        f"{ENTITIES.name}: tipo não mapeado em {table}.{column}: {base}"
                    )
            columns.append([column, sql_type, not nullable])

        tables[table] = columns

    if not tables:
        raise SystemExit(f"nenhuma tabela encontrada em {ENTITIES}")
    return tables


def parse_version() -> int:
    text = DATABASE.read_text(encoding="utf-8")
    found = re.search(r"\bversion\s*=\s*(\d+)", text)
    if not found:
        raise SystemExit("versão do banco não encontrada em SmartEdDatabase.kt")
    return int(found.group(1))


def parse_migrations() -> dict[tuple[int, int], list[str]]:
    text = DATABASE.read_text(encoding="utf-8")
    migrations: dict[tuple[int, int], list[str]] = {}
    for match in re.finditer(r"object\s*:\s*Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)", text):
        start = text.find("{", match.end())
        end = balanced(text, start, "{", "}")
        body = text[start:end]
        steps = []
        for sql in re.finditer(r'execSQL\(\s*("(?:[^"\\]|\\.)*")', body, re.S):
            raw = sql.group(1)
            steps.append(json.loads(raw))
        migrations[(int(match.group(1)), int(match.group(2)))] = steps
    return migrations


def encode(columns: list[tuple[str, str, bool]]) -> list[str]:
    """Serializa colunas como "nome TIPO [NOT NULL]" (baseline legível e curto)."""
    return [f"{name} {sql_type}" + (" NOT NULL" if not_null else "")
            for name, sql_type, not_null in columns]


def decode(entries: list) -> list[tuple[str, str, bool]]:
    columns = []
    for entry in entries:
        parts = entry.split(" ")
        not_null = parts[-2:] == ["NOT", "NULL"]
        if not_null:
            parts = parts[:-2]
        name, sql_type = parts[0], " ".join(parts[1:])
        columns.append((name, sql_type.upper(), not_null))
    return columns


def schema_of(payload: dict) -> dict[str, list[tuple[str, str, bool]]]:
    return {table: decode(cols) for table, cols in payload.items()}


def build_v1(con: sqlite3.Connection, tables: dict[str, list[tuple[str, str, bool]]]) -> None:
    for table, columns in tables.items():
        defs = []
        for name, sql_type, not_null in columns:
            piece = f'"{name}" {sql_type}'
            if not_null:
                piece += " NOT NULL"
            defs.append(piece)
        con.execute(f'CREATE TABLE "{table}" ({", ".join(defs)})')


def read_back(con: sqlite3.Connection) -> dict[str, list[tuple[str, str, bool]]]:
    result: dict[str, list[tuple[str, str, bool]]] = {}
    names = [r[0] for r in con.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    )]
    for table in sorted(names):
        info = con.execute(f'PRAGMA table_info("{table}")').fetchall()
        result[table] = [(row[1], (row[2] or "").upper(), bool(row[3])) for row in info]
    return result


def normalize(columns: list[tuple[str, str, bool]]) -> list[tuple[str, str, bool]]:
    return sorted((c[0], c[1], c[2]) for c in columns)


def same(a, b) -> bool:
    return normalize(a) == normalize(b)


def describe(tables: dict[str, list[tuple[str, str, bool]]], label: str) -> str:
    lines = [f"  {label}:"]
    for table in sorted(tables):
        cols = ", ".join(c[0] for c in tables[table])
        lines.append(f"    {table}: {cols}")
    return "\n".join(lines)


def diff(previous, current) -> list[str]:
    problems = []
    for table in sorted(set(previous) | set(current)):
        old = {c[0]: c for c in previous.get(table, [])}
        new = {c[0]: c for c in current.get(table, [])}
        if table not in previous:
            problems.append(f"tabela nova: {table}")
        elif table not in current:
            problems.append(f"tabela removida: {table}")
        for column in sorted(set(new) - set(old)):
            problems.append(f"coluna nova: {table}.{column}")
        for column in sorted(set(old) - set(new)):
            problems.append(f"coluna removida: {table}.{column}")
        for column in sorted(set(old) & set(new)):
            if old[column] != new[column]:
                problems.append(
                    f"tipo mudou: {table}.{column} {old[column][1:]} → {new[column][1:]}"
                )
    return problems


def main() -> int:
    accept = "--accept" in sys.argv
    version = parse_version()
    current = parse_entities()
    migrations = parse_migrations()

    baseline = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {
        "current": version,
        "versions": {str(version): {t: encode(c) for t, c in current.items()}},
    }
    versions = {int(k): schema_of(v) for k, v in baseline["versions"].items()}

    if accept:
        versions[version] = current
        payload = {
            "current": version,
            "versions": {
                str(v): {t: encode(cols) for t, cols in versions[v].items()}
                for v in sorted(versions)
            },
        }
        BASELINE.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"baseline gravado: versão {version}, {len(current)} tabela(s)")
        return 0

    failures: list[str] = []

    if baseline.get("current") != version:
        failures.append(
            f"versão do banco é {version} mas o baseline registra {baseline.get('current')}; "
            "rode --accept depois de conferir a migração"
        )

    expected = versions.get(version)
    if expected is None:
        failures.append(f"baseline não tem o esquema da versão {version}")
    elif not same(expected, current):
        failures.extend(diff(expected, current))
        failures.append(
            "esquema mudou sem registrar: suba `version` e escreva a migração, depois --accept"
        )

    # Cada passo de versão precisa da sua migração.
    for step in range(1, version):
        if (step, step + 1) not in migrations:
            failures.append(f"falta a migração {step} → {step + 1} em SmartEdDatabase.kt")

    # Executa as migrações de verdade, partindo do esquema antigo.
    for step in range(1, version):
        previous = versions.get(step)
        nxt = versions.get(step + 1)
        steps = migrations.get((step, step + 1))
        if previous is None or nxt is None or steps is None:
            continue
        con = sqlite3.connect(":memory:")
        produced = None
        try:
            build_v1(con, previous)
            for sql in steps:
                con.execute(sql)
            produced = read_back(con)
        except sqlite3.Error as error:
            failures.append(f"migração {step}→{step + 1} falhou no SQLite: {error}")
        finally:
            con.close()
        if produced is None:
            continue
        for table in sorted(nxt):
            if table not in produced:
                failures.append(f"migração {step}→{step + 1} não criou a tabela {table}")
                continue
            if not same(nxt[table], produced[table]):
                failures.append(
                    f"migração {step}→{step + 1} não reproduz {table}: "
                    f"esperado {normalize(nxt[table])} · obtido {normalize(produced[table])}"
                )

    if failures:
        print("FALHA — esquema do Room inconsistente:", file=sys.stderr)
        for problem in failures:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    print(
        f"OK — esquema v{version} com {len(current)} tabela(s) e "
        f"{len(migrations)} migração(ões) reproduzidas no SQLite."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
