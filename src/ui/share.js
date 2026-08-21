/**
 * „Teilen an PeTodo“.
 *
 * Als installierte Anwendung meldet sich die Seite als Teilen-Ziel an; geteilt wird dann
 * über Adressparameter. Was daraus wird — Titel, Notiz oder ein Verweis mit Beschriftung —
 * entscheidet `domain/share.js`.
 *
 * Die Adresse wird danach aufgeräumt: Ein Neuladen darf dieselbe Aufgabe nicht ein zweites
 * Mal anlegen.
 */

import { captureShare } from "../domain/share.js";
import { parseQuickAdd } from "../domain/quickadd.js";
import * as repo from "../data/repo.js";
import { S } from "./strings.js";
import { meldung } from "./toast.js";

export async function geteiltesUebernehmen(settings) {
  const parameter = new URLSearchParams(globalThis.location.search);
  if (!parameter.has("title") && !parameter.has("text") && !parameter.has("url")) return false;

  const geteilt = captureShare(parameter.get("title"), parameter.get("text"), parameter.get("url"));
  aufraeumen();
  if (geteilt === null) return false;

  // Auch im Geteilten steckt manchmal ein Datum — „morgen“ soll auch hier zählen.
  const gelesen = parseQuickAdd(geteilt.title, Date.now());

  const aufgabe = await repo.createTask({
    listId: settings.defaultListId ?? (await repo.loadLists())[0]?.id,
    title: gelesen.title.length > 0 ? gelesen.title : geteilt.title,
    note: geteilt.note,
    day: gelesen.day,
    minutes: gelesen.minutes,
  });

  meldung(`${S.quickadd_add}: ${aufgabe.title}`);
  return true;
}

function aufraeumen() {
  const sauber = globalThis.location.pathname + globalThis.location.hash;
  globalThis.history?.replaceState?.(null, "", sauber);
}
