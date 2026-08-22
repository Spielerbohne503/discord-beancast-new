/**
 * Abgleich zwischen Geräten.
 *
 * Das hier ist der Punkt, für den die Einbahnstraßen von Anfang an gebaut wurden:
 * **UUIDs** als Schlüssel, **`updatedAt`** in jeder Zeile, **Tombstones** statt echtem
 * Löschen. Ohne diese drei ginge Abgleich gar nicht — mit ihnen ist er eine Handvoll
 * Zeilen.
 *
 * Die Regel ist dieselbe wie beim Einlesen einer Sicherung: **Last-Write-Wins je Zeile
 * über `updatedAt`.** Nicht je Datei, nicht je Tabelle — je Zeile. Wer auf dem Telefon
 * eine Aufgabe abhakt und am Rechner eine andere anlegt, behält beides.
 *
 * Ein Tombstone ist dabei kein Sonderfall: Er trägt ein `updatedAt` wie jede andere
 * Änderung und gewinnt, wenn er jünger ist. Deshalb kommt eine gelöschte Aufgabe nicht
 * beim nächsten Abgleich zurück — der Fehler, an dem selbstgebauter Abgleich fast immer
 * scheitert.
 *
 * Rein: kein Netz, keine Verschlüsselung, keine Uhr. Nur die Entscheidung, welche Zeile
 * gewinnt.
 */

import { BACKUP_TABLES, shouldReplace } from "./backup.js";

/** Fassung des Übertragungsformats. Eine neuere Fassung wird nicht geraten. */
export const SYNC_VERSION = 1;

/** Trennt die Teile eines zusammengesetzten Schlüssels. */
const TRENNER = "|";

/**
 * Der Schlüssel einer Zeile.
 *
 * Zwei Tabellen haben zusammengesetzte Schlüssel — `task_tags` und `habit_checkins`. Ohne
 * diese Sonderbehandlung würden dort alle Zeilen als dieselbe gelten und sich gegenseitig
 * überschreiben.
 */
export function zeilenSchluessel(tabelle, zeile) {
  if (tabelle === "task_tags") return `${zeile.taskId}${TRENNER}${zeile.tagId}`;
  if (tabelle === "habit_checkins") return `${zeile.habitId}${TRENNER}${zeile.day}`;
  return zeile.id ?? null;
}

/**
 * Führt zwei Tabellensätze zusammen.
 *
 * Symmetrisch: Es ist egal, welcher Satz `lokal` und welcher `fremd` ist — dieselben Daten
 * ergeben dasselbe Ergebnis. Genau das macht den Abgleich in beide Richtungen möglich,
 * ohne mitzuschreiben, wer wann was gesehen hat.
 *
 * @returns `{ tabellen, bericht }` — `bericht` zählt, was von `fremd` übernommen wurde
 */
export function zusammenfuehren(lokal, fremd) {
  const tabellen = {};
  let uebernommen = 0;
  let behalten = 0;

  for (const tabelle of BACKUP_TABLES) {
    const zusammen = new Map();

    for (const zeile of lokal[tabelle] ?? []) {
      const schluessel = zeilenSchluessel(tabelle, zeile);
      if (schluessel !== null) zusammen.set(schluessel, zeile);
    }

    for (const zeile of fremd[tabelle] ?? []) {
      const schluessel = zeilenSchluessel(tabelle, zeile);
      if (schluessel === null) continue;

      const vorhanden = zusammen.get(schluessel);
      if (vorhanden === undefined || shouldReplace(vorhanden.updatedAt, zeile.updatedAt)) {
        zusammen.set(schluessel, zeile);
        uebernommen++;
      } else {
        behalten++;
      }
    }

    tabellen[tabelle] = [...zusammen.values()];
  }

  return { tabellen, bericht: { uebernommen, behalten } };
}

/**
 * Ob sich ein Hochladen überhaupt lohnt.
 *
 * Nach dem Zusammenführen ist der lokale Stand oft Zeichen für Zeichen der ferne. Dann
 * schweigt man besser, statt jedes Mal dieselben Daten hin- und herzuschieben.
 */
export function unterscheidetSich(a, b) {
  for (const tabelle of BACKUP_TABLES) {
    const links = a[tabelle] ?? [];
    const rechts = b[tabelle] ?? [];
    if (links.length !== rechts.length) return true;

    const nachSchluessel = new Map(
      rechts.map((zeile) => [zeilenSchluessel(tabelle, zeile), zeile.updatedAt ?? null]),
    );
    for (const zeile of links) {
      const schluessel = zeilenSchluessel(tabelle, zeile);
      if (!nachSchluessel.has(schluessel)) return true;
      if (nachSchluessel.get(schluessel) !== (zeile.updatedAt ?? null)) return true;
    }
  }
  return false;
}

/**
 * Räumt alte Tombstones weg.
 *
 * Ein Grabstein muss so lange stehen, dass ihn **jedes** Gerät gesehen hat — sonst kommt
 * die gelöschte Aufgabe von dort zurück. Ein Jahr ist großzügig gerechnet und kostet
 * nichts: Ein Tombstone ist eine Handvoll Bytes.
 */
export const TOMBSTONE_TAGE = 365;

export function ohneAlteTombstones(tabellen, now) {
  const grenze = now - TOMBSTONE_TAGE * 86_400_000;
  const gefiltert = {};

  for (const tabelle of BACKUP_TABLES) {
    gefiltert[tabelle] = (tabellen[tabelle] ?? []).filter((zeile) => {
      const geloescht = zeile.deletedAt;
      return !(geloescht !== null && geloescht !== undefined && geloescht < grenze);
    });
  }
  return gefiltert;
}
