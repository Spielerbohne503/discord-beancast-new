/**
 * Das Sicherungsformat.
 *
 * Bewusst tabellennah statt hübsch: Was in der Datei steht, ist genau das, was in der
 * Datenbank steht — einschließlich Tombstones und `updatedAt`. Damit ist die Sicherung
 * zugleich die Vorstufe eines späteren Abgleichs zwischen Geräten, und eine
 * Wiederherstellung kann zusammenführen statt zu überschreiben.
 *
 * Das Format ist Zeichen für Zeichen dasselbe wie in der Android-Fassung — eine dort
 * erzeugte Sicherung lässt sich hier einlesen.
 *
 * `JSON` ist Teil der Sprache, keine Bibliothek; ein eigener Codec wie in der
 * Kotlin-Fassung wäre hier reine Beschäftigung.
 */

/** Kennung, damit eine fremde JSON-Datei nicht versehentlich eingelesen wird. */
export const BACKUP_MAGIC = "petodo-backup";
/**
 * Die Fassung bleibt 1.
 *
 * Tabellen kommen dazu — das Format ist tabellennah, eine unbekannte Tabelle wird beim
 * Lesen schlicht übergangen. Eine Sicherung aus der Android-Fassung bleibt damit
 * einlesbar, und eine von heute lässt sich dort öffnen, nur ohne Vorlagen. Die Fassung
 * steigt erst, wenn sich die **Bedeutung** eines Feldes ändert.
 */
export const BACKUP_VERSION = 1;

/** Reihenfolge der Tabellen in der Datei — nur der Lesbarkeit halber fest. */
export const BACKUP_TABLES = Object.freeze([
  "task_lists",
  "tasks",
  "tags",
  "task_tags",
  "reminders",
  "reward_events",
  "pet_state",
  "focus_sessions",
  "habits",
  "habit_checkins",
  "templates",
  "template_items",
]);

export function encodeBackup(tables, exportedAt, indent = 2) {
  const ordered = {};
  for (const table of BACKUP_TABLES) ordered[table] = tables[table] ?? [];

  return JSON.stringify(
    { format: BACKUP_MAGIC, version: BACKUP_VERSION, exportedAt, tables: ordered },
    null,
    indent,
  );
}

/**
 * Liest eine Sicherung.
 *
 * Gibt `null` zurück, wenn die Datei kaputt ist, keine PeTodo-Sicherung ist oder aus einer
 * neueren Fassung stammt, die dieses Programm nicht kennt. **Eine unlesbare Datei darf
 * nichts überschreiben.**
 */
export function decodeBackup(text) {
  let root;
  try {
    root = JSON.parse(text);
  } catch {
    return null;
  }

  if (root === null || typeof root !== "object" || Array.isArray(root)) return null;
  if (root.format !== BACKUP_MAGIC) return null;

  const version = Number(root.version);
  if (!Number.isInteger(version) || version > BACKUP_VERSION) return null;
  if (root.tables === null || typeof root.tables !== "object") return null;

  const tables = {};
  for (const [name, rows] of Object.entries(root.tables)) {
    tables[name] = Array.isArray(rows)
      ? rows.filter((row) => row !== null && typeof row === "object" && !Array.isArray(row))
      : [];
  }

  return {
    version,
    exportedAt: Number(root.exportedAt) || 0,
    tables,
    rows(table) {
      return this.tables[table] ?? [];
    },
    get rowCount() {
      return Object.values(this.tables).reduce((total, rows) => total + rows.length, 0);
    },
  };
}

/**
 * Last-Write-Wins über `updatedAt` — dieselbe Regel, die ein späterer Abgleich benutzt.
 *
 * Ein Tombstone ist dabei kein Sonderfall: Er trägt ein `updatedAt` wie jede andere
 * Änderung und gewinnt, wenn er jünger ist. Eine alte Sicherung überschreibt damit keine
 * neuere Arbeit.
 */
export function shouldReplace(existingUpdatedAt, incomingUpdatedAt) {
  if (incomingUpdatedAt === null || incomingUpdatedAt === undefined) return false;
  if (existingUpdatedAt === null || existingUpdatedAt === undefined) return true;
  return incomingUpdatedAt > existingUpdatedAt;
}

/** Was eine Wiederherstellung bewirkt hat. */
export function restoreReport(inserted = 0, updated = 0, skipped = 0) {
  return { inserted, updated, skipped, touched: inserted + updated };
}

export function addReports(a, b) {
  return restoreReport(a.inserted + b.inserted, a.updated + b.updated, a.skipped + b.skipped);
}
