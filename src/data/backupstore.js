/**
 * Sichern und Wiederherstellen.
 *
 * Ausgeführt wird hier, entschieden in `domain/backup.js`. Zwei Regeln:
 *
 * - **Tombstones fahren mit.** Eine Sicherung ohne sie holt gelöschte Aufgaben zurück.
 * - **Wiederhergestellt wird zusammenführend**, nicht überschreibend: Je Zeile gewinnt der
 *   jüngere `updatedAt`. Eine alte Datei kann damit keine neuere Arbeit auslöschen.
 */

import { BACKUP_TABLES, decodeBackup, encodeBackup, restoreReport, shouldReplace } from "../domain/backup.js";
import { getAll, request, transaction } from "./db.js";
import { STORES } from "./schema.js";

/** Der Schlüssel einer Zeile, passend zum `keyPath` ihrer Tabelle. */
function keyOf(table, row) {
  const keyPath = STORES[table]?.keyPath;
  if (keyPath === undefined) return null;
  return Array.isArray(keyPath) ? keyPath.map((feld) => row[feld]) : row[keyPath];
}

export async function exportBackup(at) {
  const tabellen = {};
  for (const tabelle of BACKUP_TABLES) tabellen[tabelle] = await getAll(tabelle);
  return encodeBackup(tabellen, at);
}

/** Ein Dateiname, der sich später sortieren lässt. */
export function backupFileName(at) {
  const stempel = new Date(at).toISOString().slice(0, 19).replaceAll(":", "-");
  return `petodo-${stempel}.json`;
}

/**
 * Liest eine Sicherung ein.
 *
 * Gibt `null` zurück, wenn die Datei nicht lesbar ist — dann wurde **nichts** angefasst.
 */
export async function importBackup(text) {
  const dokument = decodeBackup(text);
  if (dokument === null) return null;

  let bericht = restoreReport();

  for (const tabelle of BACKUP_TABLES) {
    const zeilen = dokument.rows(tabelle);
    if (zeilen.length === 0) continue;

    bericht = await transaction([tabelle], "readwrite", async (tx) => {
      const store = tx.objectStore(tabelle);
      let eingefuegt = 0;
      let ersetzt = 0;
      let uebergangen = 0;

      for (const zeile of zeilen) {
        const key = keyOf(tabelle, zeile);
        if (key === null || key === undefined) {
          uebergangen++;
          continue;
        }

        const vorhanden = await request(store.get(key));
        if (vorhanden === undefined) {
          store.put(zeile);
          eingefuegt++;
        } else if (shouldReplace(vorhanden.updatedAt, zeile.updatedAt)) {
          store.put(zeile);
          ersetzt++;
        } else {
          uebergangen++;
        }
      }

      return {
        inserted: bericht.inserted + eingefuegt,
        updated: bericht.updated + ersetzt,
        skipped: bericht.skipped + uebergangen,
        touched: bericht.touched + eingefuegt + ersetzt,
      };
    });
  }

  return bericht;
}
