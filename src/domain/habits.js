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

/**
 * Zwei Arten von Gewohnheit.
 *
 * `TAGE` — feste Wochentage: montags, mittwochs, freitags. Gezählt wird in Terminen.
 * `ZIEL` — eine Zahl pro Woche: dreimal, egal wann. Gezählt wird in Wochen.
 *
 * Der Unterschied ist keine Verpackung, sondern eine andere Frage: „Habe ich meinen
 * Termin gehalten?“ gegen „Habe ich die Woche geschafft?“. Wer dreimal die Woche laufen
 * will, hat am Dienstag nichts versäumt — er hat nur noch nicht angefangen.
 */
export const Art = Object.freeze({ TAGE: "tage", ZIEL: "ziel" });

/** Ob eine Gewohnheit über ein Wochenziel läuft. */
export function hatZiel(habit) {
  return Number(habit?.target) > 0;
}

/** Das Wochenziel — bei festen Tagen ist es die Anzahl der Termine. */
export function wochenziel(habit) {
  return hatZiel(habit) ? Number(habit.target) : Schedule.timesPerWeek(habit.schedule);
}

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
/**
 * Ob an diesem Tag abgehakt werden **darf**.
 *
 * Bei einem Wochenziel: jeden Tag. Bei festen Tagen: nur an den gewählten. Das ist der
 * ganze Unterschied in der Bedienung.
 */
export function darfAbhaken(habit, day) {
  return hatZiel(habit) ? true : Schedule.isDueOn(habit.schedule, day);
}

/**
 * Die laufende Serie, je nach Art in Terminen oder in Wochen.
 *
 * **Der heutige Tag zählt nie gegen einen** — und bei einem Wochenziel gilt dasselbe für
 * die laufende Woche: Sie bricht die Serie nicht, solange sie noch läuft.
 */
export function serie(habit, checkins, today) {
  if (!hatZiel(habit)) return currentStreak(checkins, habit.schedule, today);
  return wochenSerie(checkins, Number(habit.target), today);
}

/**
 * Wie viele Wochen in Folge das Ziel erreicht wurde.
 *
 * Die laufende Woche zählt mit, wenn sie schon geschafft ist — sonst wird sie
 * übersprungen, statt die Serie zu beenden. Wer montags nachsieht, hat sonst jeden Montag
 * eine Null vor sich.
 */
export function wochenSerie(checkins, ziel, today) {
  if (!(ziel > 0)) return 0;

  let woche = startOfWeek(today);
  let laenge = 0;

  if (hakenInWoche(checkins, woche) >= ziel) laenge++;

  for (let schritt = 0; schritt < 520; schritt++) {
    woche -= 7;
    if (hakenInWoche(checkins, woche) < ziel) break;
    laenge++;
  }
  return laenge;
}

export function hakenInWoche(checkins, montag) {
  let anzahl = 0;
  for (let versatz = 0; versatz < 7; versatz++) if (checkins.has(montag + versatz)) anzahl++;
  return anzahl;
}

/** Fortschritt dieser Woche — `done` von `due`, für beide Arten. */
export function fortschritt(habit, checkins, today) {
  if (!hatZiel(habit)) return weekProgress(checkins, habit.schedule, today);
  return { done: hakenInWoche(checkins, startOfWeek(today)), due: Number(habit.target) };
}

/** Die längste Serie, je nach Art. */
export function besteSerie(habit, checkins) {
  if (!hatZiel(habit)) return longestStreak(checkins, habit.schedule);
  return besteWochenSerie(checkins, Number(habit.target));
}

function besteWochenSerie(checkins, ziel) {
  if (checkins.size === 0 || !(ziel > 0)) return 0;

  const wochen = [...new Set([...checkins].map(startOfWeek))].sort((a, b) => a - b);
  let beste = 0;
  let laufend = 0;
  let vorige = null;

  for (const woche of wochen) {
    if (hakenInWoche(checkins, woche) < ziel) {
      laufend = 0;
      vorige = woche;
      continue;
    }
    laufend = vorige !== null && woche - vorige === 7 ? laufend + 1 : 1;
    if (laufend > beste) beste = laufend;
    vorige = woche;
  }
  return beste;
}

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
