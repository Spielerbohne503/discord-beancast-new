/**
 * Erinnerungen, solange die Seite offen ist.
 *
 * Eine Webseite hat keine Weckuhr im Betriebssystem: Ohne Server und ohne Konto kann
 * niemand um acht Uhr morgens etwas auslösen, wenn der Tab zu ist. Was geht, ist ehrlich
 * benannt — **erinnert wird, sobald die Seite wieder da ist.** Das ist weniger als eine
 * App mit Alarm, aber es ist nichts Vorgetäuschtes.
 *
 * Entschieden wird nichts hier: `domain/nag.js` sagt, was zu tun ist, das hier führt aus.
 */

import {
  NagOutcome, alreadyNaggedToday, decideNag, nagStageFor, plannedNags, quietHours,
  shouldGroup, stageTraits,
} from "../domain/nag.js";
import { isOpen, isOverdue } from "../domain/tasks.js";
import { parseHhMm } from "../domain/time.js";
import { updateTask } from "./repo.js";

function fensterAus(settings) {
  return quietHours(
    parseHhMm(settings.quietHoursStart) ?? 0,
    parseHhMm(settings.quietHoursEnd) ?? 0,
    settings.quietHoursEnabled === true,
  );
}

/**
 * Geht einmal durch alle Aufgaben und meldet, was ansteht.
 *
 * @returns die Meldungen, die gezeigt werden sollen — das Anzeigen macht die Oberfläche.
 */
export async function nagDurchlauf(tasks, lists, settings, now) {
  const fenster = fensterAus(settings);
  const ohneMahnung = new Set(lists.filter((liste) => liste.excludeFromNag).map((liste) => liste.id));

  const faellig = [];

  for (const task of tasks) {
    if (!isOpen(task) || alreadyNaggedToday(task, now)) continue;

    const entscheidung = decideNag(task, ohneMahnung.has(task.listId), now, fenster);
    if (entscheidung.outcome !== NagOutcome.POST) continue;

    await updateTask(task.id, { nagCount: entscheidung.nagCount, nagLastAt: now }, now);
    faellig.push({ task, stage: entscheidung.stage, day: entscheidung.day });
  }

  if (faellig.length === 0) return [];

  // Ab drei Meldungen eine Sammelmeldung statt vieler einzelner — sonst schaltet man
  // nach einer Woche alles stumm.
  if (shouldGroup(faellig.length)) {
    return [
      {
        gruppiert: true,
        anzahl: faellig.length,
        stage: faellig.reduce(
          (schlimmste, eintrag) => (eintrag.day > schlimmste.day ? eintrag : schlimmste),
          faellig[0],
        ).stage,
      },
    ];
  }

  return faellig.map((eintrag) => ({ gruppiert: false, ...eintrag }));
}

/** Wie viele Aufgaben gerade überfällig sind — für die Sammelmeldung und das Abzeichen. */
export function ueberfaelligeAnzahl(tasks, now) {
  return tasks.filter((task) => isOverdue(task, now)).length;
}

/**
 * Der Zeitplan für einen Wecker im Betriebssystem.
 *
 * Im Browser braucht das niemand — dort läuft der Durchlauf, solange die Seite offen ist.
 * In der Android-Hülle ist es der ganze Sinn der Übung: Die Webseite rechnet aus, wann
 * geklingelt werden soll, das Betriebssystem klingelt. Gerechnet wird weiterhin nur an
 * einer Stelle.
 */
export function erinnerungsplan(tasks, lists, settings, now, limit) {
  const ohneMahnung = new Set(lists.filter((liste) => liste.excludeFromNag).map((liste) => liste.id));
  return plannedNags(tasks, ohneMahnung, now, fensterAus(settings), limit);
}

export { nagStageFor, stageTraits };
