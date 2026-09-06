/**
 * Wie ein Gerät an den Bestand kommt.
 *
 * Zwei Wege, und der erste schlägt den zweiten: ein Koppel-Link in der Adresse, oder eine
 * **offene Seite**, die das Geheimnis jedem herausgibt, der sie aufruft. Der Link gewinnt,
 * weil er ausdrücklich ist — wer einen anklickt, meint genau diesen Bestand.
 */

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

import { ausKoppelLink, istGeheimnis } from "../domain/koppeln.js";
import { inHuelle } from "./bruecke.js";
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

  // Der Link wurde an dieser Adresse geöffnet, also ist sie die richtige. Die Angabe im
  // Link zählt nur dort, wo es keine eigene gibt — in der Hülle, und dorthin führt kein
  // geöffneter Link.
  await uebernehmen(gelesen.geheimnis, globalThis.location?.origin ?? gelesen.adresse ?? "");
  return true;
}

/**
 * Die Seite ist offen — dann gibt es nichts einzurichten.
 *
 * Gefragt wird nur, wenn dieses Gerät noch keinen Bestand hat. Wer schon gekoppelt ist,
 * bleibt, wo er ist: Ein Gerät, das beim Öffnen stillschweigend den Raum wechselt, hätte
 * seine Aufgaben scheinbar verloren.
 *
 * Fehlschläge sind still. Ohne Netz oder mit zugemachter Seite ist das keine Störung,
 * sondern der Normalfall — dann bleibt es beim Koppel-Link.
 *
 * @returns ob übernommen wurde
 */
export async function offeneSeiteUebernehmen(einstellungen) {
  if (einstellungen.syncRaum) return false;
  // In der Hülle gibt es keine offene Seite: Ihr Ursprung ist eine örtliche Kennung, und
  // `Vermittler.kt` beantwortet jede Anfrage dorthin aus dem Paket. Der Versuch wäre nicht
  // nur zwecklos, er stünde bei jedem Start als 404 im Protokoll.
  if (inHuelle()) return false;

  let antwort;
  try {
    antwort = await (await fetch("offen", { cache: "no-store" })).json();
  } catch {
    return false;
  }

  if (antwort?.offen !== true || !istGeheimnis(antwort.geheimnis)) return false;

  await uebernehmen(antwort.geheimnis, globalThis.location?.origin ?? "");
  // Damit die Einstellungen es sagen können, ohne noch einmal zu fragen.
  await repo.saveSetting("syncOffen", true);
  return true;
}

/** Beide Wege enden hier. */
async function uebernehmen(geheimnis, adresse) {
  const abgeleitet = await ausGeheimnis(geheimnis);

  await repo.saveSetting("syncAdresse", adresse);
  await repo.saveSetting("syncGeheimnis", geheimnis);
  await repo.saveSetting("syncRaum", abgeleitet.raum);
  await repo.saveSetting("syncSchluessel", abgeleitet.schluessel);
  await repo.saveSetting("syncAktiv", true);

  // Wer so hereinkommt, hat die App schon kennengelernt.
  await repo.saveSetting("onboardingDone", true);
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
