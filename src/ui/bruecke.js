/**
 * Die Brücke zur Android-Hülle.
 *
 * Die Hülle ist eine App, die **nichts weiter tut**, als diese Webseite anzuzeigen und
 * einen Wecker zu stellen. Sie kennt keine Aufgaben, keine Datenbank und keine Regeln:
 * Die Webseite rechnet aus, wann geklingelt werden soll, das Betriebssystem klingelt.
 * Damit gibt es weiterhin **eine** Stelle, an der die Fachlogik steht.
 *
 * Im Browser ist `window.Petodo` nicht da; dann fällt alles hier auf `false` und `null`
 * zurück und die Seite verhält sich wie zuvor.
 *
 * ## Der Vertrag
 *
 * Was die Hülle anbietet (alles Zeichenketten, damit über die Brücke nichts anderes als
 * JSON wandert):
 *
 * | Aufruf                          | Bedeutung                                            |
 * | ------------------------------- | ---------------------------------------------------- |
 * | `zeitplanSetzen(json)`          | Alle Wecker verwerfen und diese hier stellen          |
 * | `offeneAktionen()`              | Was seit dem letzten Öffnen an der Meldung passierte  |
 * | `aktionenErledigt(json)`        | Diese Kennungen sind verarbeitet und dürfen weg       |
 * | `erinnerungenErlaubt()`         | Darf die App melden?                                  |
 * | `erlaubnisAnfragen()`           | Fragt den Nutzer (Android 13 und neuer)               |
 * | `exakteWeckerErlaubt()`         | Darf minutengenau geweckt werden?                     |
 * | `fassung()`                     | Fassungsnummer der Hülle, für die Einstellungen       |
 * | `dateiSichern(name, inhalt)`    | Eine Datei ablegen — die Hülle fragt wohin            |
 *
 * Der Zeitplan ist eine Liste aus `{ id, at, titel, stufe, ton }`.
 */

import { RewardType } from "../domain/pet.js";
import { erinnerungsplan } from "../data/nagrunner.js";
import { stageTraits } from "../domain/nag.js";
import { dayOf } from "../domain/time.js";
import * as repo from "../data/repo.js";
import { S } from "./strings.js";

/**
 * So viele Wecker werden höchstens gestellt.
 *
 * Ein Telefon, das vierzig Mal wegen derselben Liste klingelt, wird stummgeschaltet — und
 * danach ist auch die eine wichtige Meldung weg.
 */
const MAX_WECKER = 16;

/** Die Hülle, oder `null`. Nie zwischenspeichern: Im Browser bleibt es für immer `null`. */
function huelle() {
  return globalThis.Petodo ?? null;
}

export function inHuelle() {
  return huelle() !== null;
}

export function huellenFassung() {
  try {
    return huelle()?.fassung() ?? null;
  } catch {
    return null;
  }
}

export function erinnerungenErlaubt() {
  try {
    return huelle()?.erinnerungenErlaubt() === true;
  } catch {
    return false;
  }
}

export function exakteWeckerErlaubt() {
  try {
    return huelle()?.exakteWeckerErlaubt() === true;
  } catch {
    return false;
  }
}

/**
 * Reicht eine Datei an die Hülle weiter, damit sie sie ablegen kann.
 *
 * Im Browser speichert ein `<a download>` mit einem `blob:`-Verweis. In einem WebView tut
 * derselbe Verweis **nichts**: Ein Download braucht dort einen `DownloadListener`, und der
 * kann einen `blob:`-Verweis nicht auflösen — die Bytes liegen im Browser, nicht im
 * Dateisystem. Deshalb wandert der Inhalt hier über die Brücke, und die Hülle fragt den
 * Nutzer über die Systemauswahl, wohin damit.
 *
 * Gibt zurück, ob die Hülle es übernommen hat. `false` heißt: Es ist ein Browser, mach es
 * wie bisher.
 */
export function dateiSichern(name, inhalt) {
  const ziel = huelle();
  if (ziel === null || typeof ziel.dateiSichern !== "function") return false;

  try {
    ziel.dateiSichern(name, inhalt);
    return true;
  } catch {
    // Eine ältere Hülle kennt das nicht. Dann soll der Browserweg greifen, statt dass gar
    // nichts passiert.
    return false;
  }
}

/** Fragt nach der Erlaubnis. Die Antwort kommt nicht hier an — sie steht beim nächsten Zeichnen. */
export function erlaubnisAnfragen() {
  try {
    huelle()?.erlaubnisAnfragen();
  } catch {
    /* Eine Hülle ohne diese Funktion ist eine ältere — kein Grund für einen Absturz. */
  }
}

/**
 * Stellt die Wecker neu.
 *
 * Immer **alle** auf einmal: Die Hülle verwirft ihren ganzen Zeitplan und übernimmt
 * diesen. Einzelne Wecker nachzupflegen hieße, den Stand an zwei Stellen zu führen — und
 * dann klingelt irgendwann etwas für eine Aufgabe, die es nicht mehr gibt.
 */
export function zeitplanSenden(state) {
  const ziel = huelle();
  if (ziel === null) return false;

  const titel = new Map(state.tasks.map((task) => [task.id, task.title]));
  const plan = erinnerungsplan(state.tasks, state.lists, state.settings, Date.now(), MAX_WECKER)
    .map((eintrag) => ({
      id: eintrag.taskId,
      at: eintrag.at,
      titel: titel.get(eintrag.taskId) ?? S.app_name,
      stufe: S.nag_stage(eintrag.stage),
      ton: stageTraits(eintrag.stage).makesSound,
    }));

  try {
    ziel.zeitplanSetzen(JSON.stringify(plan));
    return true;
  } catch {
    return false;
  }
}

/**
 * Holt nach, was an der Meldung passiert ist, während die Seite zu war.
 *
 * Die Hülle kann die Datenbank nicht anfassen — sie liegt im Browser-Speicher und die
 * Regeln liegen hier. Sie schreibt deshalb nur auf, **was** wann angetippt wurde; gerechnet
 * wird beim nächsten Öffnen, mit dem Zeitstempel von damals. Wer am Dienstag auf
 * „Erledigt“ tippt und die App am Freitag öffnet, hat am Dienstag erledigt.
 *
 * @returns wie viele Aktionen übernommen wurden
 */
export async function aktionenNachholen() {
  const ziel = huelle();
  if (ziel === null) return 0;

  let aktionen;
  try {
    aktionen = JSON.parse(ziel.offeneAktionen() || "[]");
  } catch {
    return 0;
  }
  if (!Array.isArray(aktionen) || aktionen.length === 0) return 0;

  const verarbeitet = [];

  for (const aktion of aktionen) {
    const task = await repo.loadTask(aktion.taskId);
    // Eine Aufgabe, die es nicht mehr gibt, ist kein Fehler — sie wurde inzwischen
    // gelöscht. Die Aktion gilt trotzdem als verarbeitet, sonst bleibt sie ewig liegen.
    if (task && Number.isFinite(aktion.at)) await anwenden(task, aktion);
    verarbeitet.push(aktion.id);
  }

  try {
    ziel.aktionenErledigt(JSON.stringify(verarbeitet));
  } catch {
    /* Bleibt sie liegen, wird sie beim nächsten Mal noch einmal angewandt — bei
       „erledigt“ und „gemahnt“ ist das folgenlos, bei „morgen“ ein Tag zu viel. */
  }
  return verarbeitet.length;
}

async function anwenden(task, aktion) {
  if (aktion.art === "erledigt") {
    await repo.completeTask(task.id, aktion.at);
    return;
  }

  if (aktion.art === "morgen") {
    const { postponeToTomorrow } = await import("../domain/nag.js");
    const { minutesOfDay } = await import("../domain/time.js");
    const ziel = postponeToTomorrow(task, aktion.at);
    await repo.setDue(task.id, dayOf(ziel), task.hasTime ? minutesOfDay(ziel) : null, aktion.at);
    return;
  }

  if (aktion.art === "gemahnt") {
    // Die Hülle hat gemeldet, während die Seite zu war. Ohne diesen Nachtrag bliebe die
    // Kette auf Tag 1 stehen und würde nie lauter.
    await repo.updateTask(task.id, { nagCount: task.nagCount + 1, nagLastAt: aktion.at }, aktion.at);
  }
}

export { RewardType };
