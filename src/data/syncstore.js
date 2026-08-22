/**
 * Der Abgleich zwischen Geräten.
 *
 * Der Ablauf ist bewusst dumm und in dieser Reihenfolge:
 *
 * 1. **Holen.** Den fernen Klumpen laden und entschlüsseln.
 * 2. **Zusammenführen.** Je Zeile gewinnt der jüngere `updatedAt` (`domain/sync.js`).
 * 3. **Schreiben.** Was von fern gewonnen hat, kommt in die Datenbank.
 * 4. **Ablegen.** Der zusammengeführte Stand geht zurück — aber nur, wenn er sich vom
 *    fernen unterscheidet.
 *
 * Kein Änderungsprotokoll, keine Vektoruhren, kein Mitschreiben, wer wann was gesehen hat.
 * Bei ein paar hundert Zeilen ist der ganze Bestand kleiner als ein Bild; die einfache
 * Lösung ist hier auch die richtige, weil sie keine Zustände hat, die auseinanderlaufen
 * können.
 *
 * Gegen gleichzeitiges Schreiben von zwei Geräten hilft ein `ETag`: Wer auf einem
 * veralteten Stand aufsetzt, bekommt eine Abfuhr und fängt von vorn an.
 */

import { SYNC_VERSION, ohneAlteTombstones, unterscheidetSich, zusammenfuehren } from "../domain/sync.js";
import { entschluesseln, verschluesseln } from "./krypto.js";
import { tabellenLesen, tabellenZusammenfuehren } from "./backupstore.js";

/** Wie oft bei einem Zusammenstoß neu versucht wird, bevor aufgegeben wird. */
const VERSUCHE = 3;

export const SyncErgebnis = Object.freeze({
  AUS: "aus",
  GLEICHSTAND: "gleichstand",
  ABGEGLICHEN: "abgeglichen",
  LOSUNG_FALSCH: "losung_falsch",
  UNERREICHBAR: "unerreichbar",
  FEHLER: "fehler",
});

/**
 * Gleicht einmal ab.
 *
 * Wirft nie. Ein Abgleich, der beim Verbindungsabbruch die App mitnimmt, wäre schlimmer
 * als gar keiner — die Daten liegen ohnehin auf dem Gerät.
 */
export async function abgleichen(einstellungen, now = Date.now()) {
  if (!einstellungen.syncAktiv || !einstellungen.syncSchluessel || !einstellungen.syncRaum) {
    return { ergebnis: SyncErgebnis.AUS };
  }

  const adresse = raumAdresse(einstellungen);

  for (let versuch = 0; versuch < VERSUCHE; versuch++) {
    let fern;
    try {
      fern = await holen(adresse, einstellungen.syncSchluessel);
    } catch (fehler) {
      return { ergebnis: SyncErgebnis.UNERREICHBAR, meldung: String(fehler?.message ?? fehler) };
    }

    if (fern.ergebnis !== undefined) return fern;

    const lokal = ohneAlteTombstones(await tabellenLesen(), now);
    const { tabellen, bericht } = zusammenfuehren(lokal, fern.tabellen);

    // Erst schreiben, dann ablegen: Bricht das Ablegen ab, ist wenigstens hier alles da.
    if (bericht.uebernommen > 0) await tabellenZusammenfuehren(fern.tabellen);

    if (!unterscheidetSich(tabellen, fern.tabellen)) {
      return { ergebnis: SyncErgebnis.GLEICHSTAND, bericht, stand: now };
    }

    const abgelegt = await ablegen(adresse, einstellungen.syncSchluessel, tabellen, fern.etag);
    if (abgelegt === "zusammenstoss") continue; // Jemand war schneller — von vorn.
    if (abgelegt !== true) {
      return { ergebnis: SyncErgebnis.UNERREICHBAR, meldung: String(abgelegt) };
    }

    return { ergebnis: SyncErgebnis.ABGEGLICHEN, bericht, stand: now };
  }

  return { ergebnis: SyncErgebnis.FEHLER, meldung: "zu viele Zusammenstöße" };
}

/**
 * Wohin abgeglichen wird.
 *
 * Ohne Angabe: derselbe Ursprung wie die App. Das ist der Normalfall — der Worker liefert
 * die Seite **und** beantwortet `/sync/…`. Eine fremde Adresse ist für den Fall gedacht,
 * dass die App woanders liegt als die Ablage; die Sicherheitsrichtlinie müsste dafür
 * gelockert werden, deshalb steht es nicht im Vordergrund.
 */
function raumAdresse(einstellungen) {
  const basis = String(einstellungen.syncAdresse || globalThis.location?.origin || "").replace(/\/+$/, "");
  return `${basis}/sync/${einstellungen.syncRaum}`;
}

async function holen(adresse, schluessel) {
  const antwort = await fetch(adresse, { method: "GET", cache: "no-store" });

  // Noch nichts abgelegt: Das ist kein Fehler, das ist das erste Gerät.
  if (antwort.status === 404) return { tabellen: {}, etag: null };
  if (!antwort.ok) throw new Error(`HTTP ${antwort.status}`);

  const umschlag = await antwort.json();
  if (Number(umschlag.v) > SYNC_VERSION) {
    return { ergebnis: SyncErgebnis.FEHLER, meldung: "neuere Fassung des Formats" };
  }

  const klartext = await entschluesseln(schluessel, umschlag.iv, umschlag.daten);
  if (klartext === null) return { ergebnis: SyncErgebnis.LOSUNG_FALSCH };

  try {
    return { tabellen: JSON.parse(klartext), etag: antwort.headers.get("ETag") };
  } catch {
    return { ergebnis: SyncErgebnis.FEHLER, meldung: "unlesbarer Inhalt" };
  }
}

async function ablegen(adresse, schluessel, tabellen, etag) {
  const { iv, daten } = await verschluesseln(schluessel, JSON.stringify(tabellen));

  const kopfzeilen = { "Content-Type": "application/json" };
  // Ohne `If-Match` überschriebe ein langsames Gerät die Arbeit eines schnellen.
  if (etag !== null) kopfzeilen["If-Match"] = etag;
  else kopfzeilen["If-None-Match"] = "*";

  let antwort;
  try {
    antwort = await fetch(adresse, {
      method: "PUT",
      headers: kopfzeilen,
      body: JSON.stringify({ v: SYNC_VERSION, iv, daten }),
    });
  } catch (fehler) {
    return String(fehler?.message ?? fehler);
  }

  if (antwort.status === 412) return "zusammenstoss";
  return antwort.ok ? true : `HTTP ${antwort.status}`;
}
