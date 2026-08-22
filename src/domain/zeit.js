/**
 * Wie lange etwas gedauert hat.
 *
 * Gerechnet wird aus den **abgeschlossenen Fokusrunden**, nicht aus einer eigenen Stoppuhr.
 * Eine zweite Zeiterfassung neben dem Fokus wäre eine zweite Wahrheit über dieselbe Frage —
 * und die, die niemand pflegt.
 *
 * Abgebrochene Runden zählen nicht. Wer eine Runde nach drei Minuten abbricht, hat nicht
 * drei Minuten fokussiert gearbeitet; er hat aufgehört.
 */

import { dayOf } from "./time.js";

/** Die tatsächliche Dauer einer Runde in Millisekunden. */
export function dauerVon(sitzung) {
  if (!sitzung?.completedAt || sitzung.abortedAt) return 0;
  return Math.max(0, sitzung.completedAt - sitzung.startedAt);
}

/**
 * Fokuszeit je Aufgabe.
 *
 * @returns `Map<taskId, { millis, runden }>` — Runden ohne Aufgabe fallen weg, sie
 *          gehören zu keinem Eintrag.
 */
export function jeAufgabe(sitzungen, nurPhase = "focus") {
  const summe = new Map();

  for (const sitzung of sitzungen) {
    if (sitzung.phase !== nurPhase) continue;
    if (!sitzung.taskId) continue;

    const dauer = dauerVon(sitzung);
    if (dauer === 0) continue;

    const bisher = summe.get(sitzung.taskId) ?? { millis: 0, runden: 0 };
    summe.set(sitzung.taskId, { millis: bisher.millis + dauer, runden: bisher.runden + 1 });
  }
  return summe;
}

/** Fokuszeit je Kalendertag — für den Rückblick. */
export function jeTag(sitzungen, nurPhase = "focus") {
  const summe = new Map();

  for (const sitzung of sitzungen) {
    if (sitzung.phase !== nurPhase) continue;
    const dauer = dauerVon(sitzung);
    if (dauer === 0) continue;

    const tag = dayOf(sitzung.completedAt);
    summe.set(tag, (summe.get(tag) ?? 0) + dauer);
  }
  return summe;
}

/**
 * Wohin die Zeit geht: Fokusminuten und Erledigtes je Liste.
 *
 * Die Frage dahinter ist nicht „war ich fleißig“, sondern „wofür ging der Monat drauf“ —
 * und die beantwortet nur eine Aufteilung nach Listen. Absteigend nach Zeit, weil man
 * zuerst wissen will, was am meisten frisst.
 */
export function jeListe(tasks, sitzungen, listen, vonTag, bisTag) {
  const listeVonAufgabe = new Map(tasks.map((task) => [task.id, task.listId]));
  const zeit = new Map();
  const erledigt = new Map();

  for (const sitzung of sitzungen) {
    if (sitzung.phase !== "focus") continue;
    const dauer = dauerVon(sitzung);
    if (dauer === 0) continue;

    const tag = dayOf(sitzung.completedAt);
    if (tag < vonTag || tag > bisTag) continue;

    const listId = listeVonAufgabe.get(sitzung.taskId) ?? null;
    if (listId === null) continue;
    zeit.set(listId, (zeit.get(listId) ?? 0) + dauer);
  }

  for (const task of tasks) {
    if (!task.completedAt || task.deletedAt) continue;
    const tag = dayOf(task.completedAt);
    if (tag < vonTag || tag > bisTag) continue;
    erledigt.set(task.listId, (erledigt.get(task.listId) ?? 0) + 1);
  }

  return listen
    .map((liste) => ({
      listId: liste.id,
      name: liste.name,
      millis: zeit.get(liste.id) ?? 0,
      erledigt: erledigt.get(liste.id) ?? 0,
    }))
    .filter((eintrag) => eintrag.millis > 0 || eintrag.erledigt > 0)
    .sort((a, b) => b.millis - a.millis || b.erledigt - a.erledigt);
}
