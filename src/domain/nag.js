/**
 * Erinnerungen: Eskalation, Ruhezeit, die Entscheidung „melden oder nicht“.
 *
 * **Eskalation statt Wiederholung.** Die immer gleiche Meldung wird nach drei Tagen zu
 * Hintergrundrauschen, und dann schaltet man den Kanal ab — samt der Fälligkeiten, auf
 * die es ankommt.
 *
 * Die eine Regel, an der selbstgebaute Aufgaben-Apps scheitern: Der nächste Termin wird
 * über *Kalendertag + Uhrzeit* berechnet, **niemals** über `+ 86.400.000 ms`. Sonst
 * wandert die 8-Uhr-Erinnerung bei jeder Zeitumstellung um eine Stunde — zweimal im Jahr,
 * und danach für immer schief.
 */

import { Balance } from "./balance.js";
import { atTime, dayOf, minutesOfDay, parseHhMm } from "./time.js";
import { isCompleted, isDeleted } from "./tasks.js";

// ------------------------------------------------------------------ Eskalation

export const NagStage = Object.freeze({
  /** Tag 1: normale Erinnerung, lautlos. */
  FIRST: "first",
  /** Tag 2–3: „steht immer noch offen“, der Begleiter kommentiert. */
  AGAIN: "again",
  /** Tag 4–6: höhere Dringlichkeit, mit Ton. */
  LOUD: "loud",
  /** Ab Tag 7: die Aufräum-Frage „Willst du das noch?“ */
  CLEANUP: "cleanup",
});

const STAGE_TRAITS = Object.freeze({
  [NagStage.FIRST]: { makesSound: false, petComments: false, asksCleanupQuestion: false },
  [NagStage.AGAIN]: { makesSound: false, petComments: true, asksCleanupQuestion: false },
  [NagStage.LOUD]: { makesSound: true, petComments: true, asksCleanupQuestion: false },
  [NagStage.CLEANUP]: { makesSound: true, petComments: true, asksCleanupQuestion: true },
});

export function stageTraits(stage) {
  return STAGE_TRAITS[stage] ?? STAGE_TRAITS[NagStage.FIRST];
}

/**
 * Der wievielte Erinnerungstag ansteht.
 *
 * `nagCount` zählt, wie oft bereits gemahnt wurde — beim allerersten Alarm ist er 0, und
 * das ist Tag 1.
 */
export function nagDay(nagCount) {
  return Math.max(0, nagCount) + 1;
}

export function nagStageFor(nagCount) {
  const day = nagDay(nagCount);
  if (day >= Balance.NAG_DAY_CLEANUP) return NagStage.CLEANUP;
  if (day >= Balance.NAG_DAY_LOUD) return NagStage.LOUD;
  if (day >= Balance.NAG_DAY_AGAIN) return NagStage.AGAIN;
  return NagStage.FIRST;
}

/**
 * Ab drei überfälligen Aufgaben eine gruppierte Meldung statt Einzelvibrationen — sonst
 * schaltet man nach einer Woche alles stumm.
 */
export function shouldGroup(overdueCount) {
  return overdueCount >= Balance.NAG_GROUP_THRESHOLD;
}

// -------------------------------------------------------------------- Ruhezeit

/**
 * Ein Ruhefenster, in Minuten seit Mitternacht.
 *
 * Fällige Erinnerungen werden ans Fensterende **geschoben**, nicht verworfen. Eine
 * verworfene Erinnerung ist eine verpasste Aufgabe, und genau dafür benutzt man die App
 * nicht.
 */
export function quietHours(start, end, enabled = true) {
  return Object.freeze({ start, end, enabled });
}

export const QUIET_HOURS_DEFAULT = quietHours(
  parseHhMm(Balance.QUIET_HOURS_DEFAULT_START),
  parseHhMm(Balance.QUIET_HOURS_DEFAULT_END),
);

export const QUIET_HOURS_OFF = quietHours(QUIET_HOURS_DEFAULT.start, QUIET_HOURS_DEFAULT.end, false);

/** Fenster über Mitternacht (23:00–08:00) ebenso wie am Tag (13:00–14:00). */
export function isInQuietHours(quiet, minutes) {
  if (!quiet.enabled || quiet.start === quiet.end) return false;
  return quiet.start < quiet.end
    ? minutes >= quiet.start && minutes < quiet.end
    : minutes >= quiet.start || minutes < quiet.end;
}

/**
 * Verschiebt [at] ans Ende der Ruhezeit, falls es hineinfällt. Sonst unverändert.
 *
 * Über Mitternacht hinweg landet eine Erinnerung um 23:30 am nächsten Morgen um 08:00,
 * eine um 02:00 ebenfalls — beide am selben Fensterende.
 */
export function shiftOutOfQuietHours(at, quiet) {
  if (!isInQuietHours(quiet, minutesOfDay(at))) return at;

  const day = dayOf(at);
  const endsToday = atTime(day, 0, quiet.end);
  return endsToday > at ? endsToday : atTime(day + 1, 0, quiet.end);
}

// -------------------------------------------------------------------- Termine

/**
 * Die lokale Uhrzeit, an der die Erinnerungskette hängt, in Minuten seit Mitternacht.
 *
 * Vorrang hat `dueTimeLocal` — genau dafür existiert das Feld. Fehlt es, gilt bei einer
 * Aufgabe mit Uhrzeit deren Uhrzeit, bei einem Tagestermin die Vorgabe.
 */
export function anchorMinutes(task) {
  if (task.dueTimeLocal !== null && task.dueTimeLocal !== undefined) return task.dueTimeLocal;
  if (task.hasTime && task.dueAt !== null && task.dueAt !== undefined) return minutesOfDay(task.dueAt);
  return Balance.DEFAULT_DUE_HOUR * 60;
}

/**
 * Die erste Erinnerung: zur Fälligkeit selbst, bei Tagesterminen am Fälligkeitstag zur
 * Ankeruhrzeit. Ohne Fälligkeit gibt es keine Erinnerung.
 */
export function firstNagAt(task) {
  if (task.dueAt === null || task.dueAt === undefined) return null;
  if (task.hasTime) return task.dueAt;
  return atTime(dayOf(task.dueAt), 0, anchorMinutes(task));
}

/**
 * Der Termin am Folgetag zur selben lokalen Uhrzeit.
 *
 * An einer Zeitumstellung liegen zwischen zwei Erinnerungen 23 bzw. 25 Stunden — die
 * Uhrzeit bleibt trotzdem dieselbe.
 */
export function nextNagAt(after, anchor) {
  return atTime(dayOf(after) + 1, 0, anchor);
}

/**
 * „Morgen“ aus der Erinnerung: Die Fälligkeit wandert einen Kalendertag weiter, die
 * Uhrzeit bleibt. Auch hier keine Millisekunden-Arithmetik.
 */
export function postponeToTomorrow(task, now) {
  const basis = Math.max(task.dueAt ?? now, now);
  return atTime(dayOf(basis) + 1, 0, anchorMinutes(task));
}

// ------------------------------------------------------------------ Entscheidung

export const NagOutcome = Object.freeze({
  CANCEL: "cancel",
  RESCHEDULE: "reschedule",
  POST: "post",
});

/**
 * Die eine Entscheidung, die der Erinnerungsdienst braucht — er entscheidet nichts
 * selbst, er führt aus.
 *
 * Die Reihenfolge der Prüfungen ist bewusst so gewählt:
 * 1. Gibt es überhaupt noch etwas zu mahnen?
 * 2. Hat der Nutzer aufgeschoben? (Sein Wille schlägt den Zeitplan.)
 * 3. Ist der Termin überhaupt schon erreicht?
 * 4. Ist gerade Ruhezeit? (Verschieben, nie verwerfen.)
 * 5. Sonst: melden.
 */
export function decideNag(task, listExcludedFromNag, now, quiet) {
  if (isDeleted(task)) return { outcome: NagOutcome.CANCEL, reason: "deleted" };
  if (isCompleted(task)) return { outcome: NagOutcome.CANCEL, reason: "completed" };
  if (listExcludedFromNag) return { outcome: NagOutcome.CANCEL, reason: "list_excluded" };
  if (task.dueAt === null || task.dueAt === undefined) {
    return { outcome: NagOutcome.CANCEL, reason: "no_due_date" };
  }

  if (task.snoozedUntil !== null && task.snoozedUntil !== undefined && task.snoozedUntil > now) {
    return { outcome: NagOutcome.RESCHEDULE, at: shiftOutOfQuietHours(task.snoozedUntil, quiet), reason: "snoozed" };
  }

  const first = firstNagAt(task) ?? task.dueAt;
  if (first > now) {
    return { outcome: NagOutcome.RESCHEDULE, at: shiftOutOfQuietHours(first, quiet), reason: "not_due_yet" };
  }

  if (isInQuietHours(quiet, minutesOfDay(now))) {
    return { outcome: NagOutcome.RESCHEDULE, at: shiftOutOfQuietHours(now, quiet), reason: "quiet_hours" };
  }

  return {
    outcome: NagOutcome.POST,
    stage: nagStageFor(task.nagCount),
    day: nagDay(task.nagCount),
    nagCount: task.nagCount + 1,
    nextNagAt: shiftOutOfQuietHours(nextNagAt(now, anchorMinutes(task)), quiet),
  };
}
