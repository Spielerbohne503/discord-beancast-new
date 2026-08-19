#!/usr/bin/env python3
"""Prüft die Room-Migrationen gegen die exportierten Schemadateien.

Vorgehen für jeden Schritt n → n+1:

1.  Datenbank aus `app/schemas/…/n.json` frisch erzeugen (die Datei enthält das
    vollständige CREATE-SQL, das Room selbst benutzt).
2.  Die Migration `n → n+1` anwenden — die SQL-Anweisungen werden aus
    `data/db/Migrations.kt` gelesen, damit Prüfung und Produktionscode nicht
    auseinanderlaufen können.
3.  Das Ergebnis mit einer frisch aus `n+1.json` erzeugten Datenbank vergleichen.

Ein Unterschied bedeutet: Room würde beim Start "Migration didn't properly handle"
werfen — beim Nutzer, nicht hier.

Aufruf:  python3 tools/verify_migrations.py
"""

from __future__ import annotations

import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMA_DIR = ROOT / "app/schemas/uk.spielerbohne.petodo.data.db.PetodoDatabase"
MIGRATIONS_KT = ROOT / "app/src/main/java/uk/spielerbohne/petodo/data/db/Migrations.kt"


def load_schema(version: int) -> dict:
    with (SCHEMA_DIR / f"{version}.json").open() as handle:
        return json.load(handle)["database"]


def create_from_schema(schema: dict) -> sqlite3.Connection:
    """Baut eine leere Datenbank exakt so auf, wie Room sie anlegen würde."""
    connection = sqlite3.connect(":memory:")
    for entity in schema["entities"]:
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for view in schema.get("views", []):
        connection.execute(view["createSql"].replace("${VIEW_NAME}", view["viewName"]))
    return connection


def migration_statements(from_version: int, to_version: int) -> list[str]:
    """Zieht die execSQL-Anweisungen der Migration aus dem Kotlin-Quelltext."""
    source = MIGRATIONS_KT.read_text()
    marker = f"Migration({from_version}, {to_version})"
    start = source.index(marker)
    # Bis zur nächsten Migration bzw. bis zum Ende der Datei lesen.
    next_marker = re.search(r"Migration\(\d+, \d+\)", source[start + len(marker):])
    end = start + len(marker) + next_marker.start() if next_marker else len(source)
    block = source[start:end]

    statements: list[str] = []
    for call in execsql_calls(block):
        # Mehrere Zeichenketten in einem Aufruf gehören zusammen: In Kotlin steht dort
        # ein "…" + "…". Nur die erste zu nehmen ergäbe eine abgeschnittene Anweisung —
        # und die könnte zufällig gültiges SQL sein und stillschweigend durchgehen.
        parts = re.findall(r'"""(.*?)"""|"((?:[^"\\]|\\.)*)"', call, re.DOTALL)
        text = "".join((dreifach if dreifach else einfach) for dreifach, einfach in parts)
        if text.strip():
            statements.append(text.strip())
    return statements


def execsql_calls(block: str) -> list[str]:
    """Der Inhalt jedes execSQL(...)-Aufrufs, Klammern korrekt gezählt."""
    calls: list[str] = []
    for match in re.finditer(r"execSQL\(", block):
        start = match.end()
        depth = 1
        index = start
        while index < len(block) and depth > 0:
            character = block[index]
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            index += 1
        calls.append(block[start:index - 1])
    return calls


def fingerprint(connection: sqlite3.Connection) -> dict[str, list]:
    """Tabellen, Spalten und Indizes in vergleichbarer Form."""
    result: dict[str, list] = {}
    tables = connection.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' "
        "AND name != 'room_master_table' ORDER BY name"
    ).fetchall()
    for (table,) in tables:
        columns = [
            (row[1], row[2].upper(), row[3], row[4], row[5])  # name, typ, notnull, default, pk
            for row in connection.execute(f"PRAGMA table_info(`{table}`)")
        ]
        indices = sorted(
            (row[1], row[2])  # name, unique
            for row in connection.execute(f"PRAGMA index_list(`{table}`)")
            if not row[1].startswith("sqlite_autoindex")
        )
        index_columns = {
            name: [r[2] for r in connection.execute(f"PRAGMA index_info(`{name}`)")]
            for name, _ in indices
        }
        result[table] = [sorted(columns), indices, index_columns]
    return result


def describe_difference(expected: dict, actual: dict) -> list[str]:
    problems = []
    for table in sorted(set(expected) | set(actual)):
        if table not in actual:
            problems.append(f"Tabelle fehlt nach der Migration: {table}")
        elif table not in expected:
            problems.append(f"Tabelle zu viel nach der Migration: {table}")
        elif expected[table] != actual[table]:
            problems.append(f"Tabelle {table} weicht ab:")
            problems.append(f"    erwartet: {expected[table]}")
            problems.append(f"    erhalten: {actual[table]}")
    return problems


def main() -> int:
    versions = sorted(int(p.stem) for p in SCHEMA_DIR.glob("*.json"))
    if len(versions) < 2:
        print(f"Nur Schemaversion {versions} vorhanden — nichts zu prüfen.")
        return 0

    failed = False
    for from_version, to_version in zip(versions, versions[1:]):
        migrated = create_from_schema(load_schema(from_version))
        statements = migration_statements(from_version, to_version)
        if not statements:
            print(f"FEHLER  {from_version} → {to_version}: keine execSQL-Anweisungen gefunden")
            failed = True
            continue
        for statement in statements:
            migrated.execute(statement)

        expected = fingerprint(create_from_schema(load_schema(to_version)))
        problems = describe_difference(expected, fingerprint(migrated))

        if problems:
            failed = True
            print(f"FEHLER  Migration {from_version} → {to_version}")
            for problem in problems:
                print(f"        {problem}")
        else:
            print(
                f"OK      Migration {from_version} → {to_version} "
                f"({len(statements)} Anweisungen, {len(expected)} Tabellen)"
            )

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
