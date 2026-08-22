/**
 * Der Abgleich, wie ihn die Oberfläche sieht.
 *
 * Ein Ort, an dem entschieden wird, **wann** abgeglichen wird — das Wie steht in
 * `data/syncstore.js`, das Was in `domain/sync.js`.
 *
 * Drei Anlässe, und keiner davon ist ein Takt: beim Start, bei der Rückkehr in den Tab,
 * und kurz nach einer Änderung. Ein Abgleich im Minutentakt kostet Akku und Datenvolumen
 * für nichts — es ändert sich ja nichts, wenn niemand etwas tut.
 */

import { SyncErgebnis, abgleichen } from "../data/syncstore.js";
import { inHuelle } from "./bruecke.js";
import { aktualisieren, nachLaden, state } from "./store.js";
import { S } from "./strings.js";
import { meldung } from "./toast.js";

/**
 * So lange wird nach einer Änderung gewartet.
 *
 * Wer eine Liste abarbeitet, hakt fünf Dinge in zwanzig Sekunden ab. Ohne diese Bremse
 * wären das fünf Abgleiche statt einem.
 */
const RUHE_MS = 4000;

let laeuft = false;
let geplant = null;

/** Der letzte Ausgang — für die Anzeige in den Einstellungen. */
export let letzterAusgang = null;

export function abgleichStarten() {
  void anstossen();

  // Zurück im Tab: Auf dem anderen Gerät kann inzwischen alles Mögliche passiert sein.
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") void anstossen();
  });

  // Nach jeder echten Änderung, mit Ruhefrist.
  nachLaden(() => {
    if (!state.settings.syncAktiv) return;
    clearTimeout(geplant);
    geplant = setTimeout(() => void anstossen({ still: true }), RUHE_MS);
  });
}

/**
 * Gleicht ab, wenn nicht ohnehin gerade abgeglichen wird.
 *
 * Zwei gleichzeitige Läufe würden sich gegenseitig den Stempel unter dem Stand wegziehen
 * und beide von vorn anfangen — der Riegel ist billiger als das Aufräumen danach.
 */
export async function anstossen({ still = true } = {}) {
  // Ohne Netzerlaubnis würde jeder Versuch scheitern — und zwar mit derselben Meldung
  // wie ein Server, der gerade nicht erreichbar ist. Das ist hier aber dauerhaft so, nicht
  // vorübergehend, und wird deshalb erst gar nicht versucht.
  if (inHuelle() || laeuft || !state.settings.syncAktiv) return null;

  laeuft = true;
  try {
    const ausgang = await abgleichen(state.settings, Date.now());
    letzterAusgang = ausgang;

    const gelungen =
      ausgang.ergebnis === SyncErgebnis.ABGEGLICHEN || ausgang.ergebnis === SyncErgebnis.GLEICHSTAND;

    if (gelungen) {
      await merken(Date.now());

      /*
       * Neu gezeichnet wird, sobald **hereingekommen** ist — unabhängig davon, ob auch
       * etwas hinausging.
       *
       * Genau hier lag ein Fehler: Wer nur empfängt und nichts zurückzuschicken hat,
       * landet bei „Gleichstand“. Die Daten standen dann längst in der Datenbank, aber
       * auf dem Bildschirm blieb der alte Stand stehen — bis irgendetwas anderes ein
       * Neuladen auslöste. Ein Abgleich, den man nicht sieht, ist keiner.
       */
      if (ausgang.bericht?.uebernommen > 0) await aktualisieren();
    }

    if (!still) zeigen(ausgang);
    return ausgang;
  } finally {
    laeuft = false;
  }
}

function zeigen(ausgang) {
  const text = S.settings_sync_ergebnis[ausgang.ergebnis] ?? S.error_generic;
  const dazu =
    ausgang.bericht?.uebernommen > 0 ? ` ${S.settings_sync_uebernommen(ausgang.bericht.uebernommen)}.` : "";
  meldung(text + dazu);
}

/**
 * Den Zeitpunkt festhalten — ohne den Umweg über ein volles Neuladen.
 *
 * `saveSetting` allein schriebe zwar, aber die Oberfläche wüsste nichts davon; und ein
 * `aktualisieren()` nach jedem stillen Abgleich wäre für eine Zeitangabe zu viel.
 */
async function merken(stand) {
  const { saveSetting } = await import("../data/repo.js");
  await saveSetting("syncStand", stand);
  state.settings.syncStand = stand;
}

export { SyncErgebnis };
