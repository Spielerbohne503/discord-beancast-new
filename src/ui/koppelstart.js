/**
 * Ein Koppel-Link wurde geöffnet.
 *
 * Das ist der ganze Einrichtungsvorgang auf dem zweiten Gerät: Link antippen, fertig. Kein
 * Feld, keine Losung, keine Adresse — was gebraucht wird, steht im Link.
 *
 * Läuft **vor** dem ersten Abgleich und noch vor dem Willkommensdialog: Wer über einen
 * Koppel-Link hereinkommt, hat schon ein eingerichtetes Gerät und soll nicht zuerst
 * gefragt werden, ob er neu anfangen will.
 */

import { ausKoppelLink } from "../domain/koppeln.js";
import { ausGeheimnis } from "../data/krypto.js";
import * as repo from "../data/repo.js";

/**
 * Übernimmt das Geheimnis aus der Adresse, wenn eines darin steht.
 *
 * @returns ob gekoppelt wurde
 */
export async function koppelnAusAdresse() {
  const hash = globalThis.location?.hash ?? "";
  if (!hash.includes("#koppeln=")) return false;

  const gelesen = ausKoppelLink(hash);

  // Die Raute kommt in jedem Fall aus der Adresse — auch wenn nichts Brauchbares
  // drinstand. Sonst hinge sie beim nächsten Laden noch da und der Versuch liefe erneut.
  hashPutzen();
  if (gelesen === null) return false;

  const abgeleitet = await ausGeheimnis(gelesen.geheimnis);

  // Der Link wurde an dieser Adresse geöffnet, also ist sie die richtige. Die Angabe im
  // Link zählt nur dort, wo es keine eigene gibt — in der Hülle, und dorthin führt kein
  // geöffneter Link.
  await repo.saveSetting("syncAdresse", globalThis.location?.origin ?? gelesen.adresse ?? "");
  await repo.saveSetting("syncGeheimnis", gelesen.geheimnis);
  await repo.saveSetting("syncRaum", abgeleitet.raum);
  await repo.saveSetting("syncSchluessel", abgeleitet.schluessel);
  await repo.saveSetting("syncAktiv", true);

  // Wer über einen Koppel-Link kommt, hat die App schon kennengelernt.
  await repo.saveSetting("onboardingDone", true);

  return true;
}

/**
 * Nimmt das Geheimnis aus der Adresszeile.
 *
 * `replaceState` statt einer Zuweisung an `location.hash`: Das Geheimnis soll nicht im
 * Verlauf stehenbleiben, wo der Zurück-Knopf es wieder hervorholt.
 */
function hashPutzen() {
  const ohne = `${globalThis.location.pathname}${globalThis.location.search}`;
  globalThis.history?.replaceState?.(null, "", ohne);
}
