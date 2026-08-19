/**
 * Gewohnheiten.
 *
 * Bewusst **keine** Aufgaben: kein Fälligkeitsdatum, keine Priorität, keine Erinnerung.
 * Was keine Fälligkeit hat, kann nicht überfällig werden — Gewohnheiten mahnen nie,
 * tauchen nie im Überfällig-Block auf und gehen nicht in die Überfälligkeitslast des
 * Begleiters ein. Die eigene Tabelle macht diese Regel strukturell unumgehbar.
 *
 * Ein Haken gehört zu einem **Kalendertag** (Epochentag in Ortszeit), nicht zu einem
 * Zeitpunkt. Mit einem Zeitstempel würde ein Zeitzonenwechsel Haken auf den Vortag
 * schieben.
 */

import { isoWeekday, startOfWeek } from "./time.js";

/** Bitmaske der Wochentage. Bit 0 ist Montag. */
export const Schedule = Object.freeze({
  DAILY: 0b111_1111,
  WEEKDAYS: 0b000_1111 | 0b001_0000,
  WEEKEND: 0b110_0000,
  NONE: 0,

  /**
   * Aus der Datenbank gelesene Masken werden beschnitten.
   *
   * Ein Wert mit gesetzten Bits jenseits der sieben Tage käme aus einer beschädigten
   * Zeile — er darf keine Endlosschleife bei der Seriensuche auslösen.
   */
  of(mask) {
    return (Number(mask) || 0) & 0b111_1111;
  },

  isDueOn(mask, day) {
    return ((Schedule.of(mask) >> (isoWeekday(day) - 1)) & 1) === 1;
  },

  isEmpty(mask) {
    return Schedule.of(mask) === 0;
  },

  isDaily(mask) {
    return Schedule.of(mask) === Schedule.DAILY;
  },

  /** Wie oft in der Woche — zugleich das Wochenziel. */
  timesPerWeek(mask) {
    let count = 0;
    for (let bit = 0; bit < 7; bit++) if ((Schedule.of(mask) >> bit) & 1) count++;
    return count;
  },

  /** Einen Wochentag an- oder abschalten. `weekday` ist 1 = Montag … 7 = Sonntag. */
  toggle(mask, weekday) {
    return Schedule.of(mask) ^ (1 << (weekday - 1));
  },

  /** Die angeschalteten Wochentage als 1…7. */
  weekdays(mask) {
    const days = [];
    for (let bit = 0; bit < 7; bit++) if ((Schedule.of(mask) >> bit) & 1) days.push(bit + 1);
    return days;
  },
});

/** Weiter zurück wird nicht gesucht. Schützt vor Endlosschleifen bei kaputten Daten. */
const MAX_LOOKBACK_STEPS = 366 * 5;

/** Der letzte Termin an oder vor [day] — `null`, wenn der Zeitplan leer ist. */
function lastScheduledOnOrBefore(mask, day) {
  for (let offset = 0; offset < 7; offset++) {
    if (Schedule.isDueOn(mask, day - offset)) return day - offset;
  }
  return null;
}

/** Der Termin davor. */
function previousScheduled(mask, day) {
  for (let offset = 1; offset <= 7; offset++) {
    if (Schedule.isDueOn(mask, day - offset)) return day - offset;
  }
  return null;
}

/**
 * Die laufende Serie in Terminen.
 *
 * Serien zählen nur Tage, an denen die Gewohnheit **anstand**. Wer montags, mittwochs und
 * freitags läuft, verliert seine Serie nicht am Dienstag — sonst wäre jeder Zeitplan
 * außer „täglich“ eine eingebaute Niederlage.
 *
 * **Der heutige Tag zählt nie gegen einen**: Steht die Gewohnheit heute an und ist noch
 * nicht abgehakt, läuft die Serie weiter, bis der Tag vorbei ist.
 *
 * @param checkins Menge von Epochentagen (`Set<number>`)
 */
export function currentStreak(checkins, mask, today) {
  if (Schedule.isEmpty(mask)) return 0;

  let day = lastScheduledOnOrBefore(mask, today);
  if (day === null) return 0;

  if (day === today && !checkins.has(today)) {
    day = previousScheduled(mask, today);
    if (day === null) return 0;
  }

  let length = 0;
  let steps = 0;
  while (checkins.has(day) && steps < MAX_LOOKBACK_STEPS) {
    length++;
    steps++;
    const previous = previousScheduled(mask, day);
    if (previous === null) break;
    day = previous;
  }
  return length;
}

/** Die längste Serie, die je zustande kam. */
export function longestStreak(checkins, mask) {
  if (Schedule.isEmpty(mask) || checkins.size === 0) return 0;

  const days = [...checkins].filter((day) => Schedule.isDueOn(mask, day)).sort((a, b) => a - b);
  if (days.length === 0) return 0;

  let best = 1;
  let running = 1;
  for (let index = 1; index < days.length; index++) {
    running = previousScheduled(mask, days[index]) === days[index - 1] ? running + 1 : 1;
    if (running > best) best = running;
  }
  return best;
}

/**
 * Wie viele der diese Woche anstehenden Termine schon erledigt sind.
 *
 * Die Woche beginnt am Montag — keine Geschmacksfrage, sondern die Wochendefinition, die
 * zu den Wochentagsmasken passt.
 */
export function weekProgress(checkins, mask, today) {
  const monday = startOfWeek(today);
  let done = 0;
  let due = 0;
  for (let offset = 0; offset < 7; offset++) {
    const day = monday + offset;
    if (!Schedule.isDueOn(mask, day)) continue;
    due++;
    if (checkins.has(day)) done++;
  }
  return { done, due };
}
