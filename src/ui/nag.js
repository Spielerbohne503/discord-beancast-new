/**
 * Erinnerungen anzeigen.
 *
 * Zwei Wege, in dieser Reihenfolge: eine Browser-Meldung, wenn sie erlaubt ist — sonst
 * eine Meldung in der Seite. Eine Erinnerung, die niemand sieht, ist keine Erinnerung;
 * eine, die nach einer Erlaubnis fragt, die man nie gegeben hat, ist eine Zumutung.
 */

import { NagStage, stageTraits } from "../domain/nag.js";
import { nagDurchlauf } from "../data/nagrunner.js";
import { aktualisieren, state } from "./store.js";
import { meldung } from "./toast.js";
import { S } from "./strings.js";

/** Einmal je Minute reicht; Erinnerungen sind auf den Tag genau, nicht auf die Sekunde. */
const TAKT = 60_000;

export function erinnerungenStarten() {
  void durchlauf();
  setInterval(() => void durchlauf(), TAKT);
}

async function durchlauf() {
  if (!state.bereit || document.visibilityState !== "visible") return;

  const meldungen = await nagDurchlauf(state.tasks, state.lists, state.settings, Date.now());
  if (meldungen.length === 0) return;

  await aktualisieren();
  for (const eintrag of meldungen) zeigen(eintrag);
}

function zeigen(eintrag) {
  const text = eintrag.gruppiert
    ? S.nag_group(eintrag.anzahl)
    : `${eintrag.task.title} — ${S.nag_stage(eintrag.stage)}`;

  if (browserMeldung(text, eintrag.stage)) return;
  meldung(text);
}

/**
 * Der Browser-Weg. Gibt `false` zurück, wenn er nicht offensteht — dann greift die
 * Meldung in der Seite.
 */
function browserMeldung(text, stage) {
  if (!state.settings.notifications) return false;
  if (typeof Notification === "undefined" || Notification.permission !== "granted") return false;

  try {
    const anzeige = new Notification(S.app_name, {
      body: text,
      icon: "icon.svg",
      badge: "icon.svg",
      tag: "petodo-nag",
      // Lautlos, bis die Kette eskaliert — genau dafür gibt es die Stufen.
      silent: !stageTraits(stage).makesSound,
      requireInteraction: stage === NagStage.CLEANUP,
    });
    anzeige.onclick = () => {
      globalThis.focus();
      anzeige.close();
    };
    return true;
  } catch {
    return false;
  }
}
