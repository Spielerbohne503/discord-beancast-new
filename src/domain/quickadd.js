/**
 * Deutsche Datums- und Zeitangaben in der Schnell-Eingabe.
 *
 * „morgen 9 Uhr Zahnarzt“ ergibt die Aufgabe „Zahnarzt“ mit Termin. **Was erkannt wird,
 * verschwindet aus dem Titel** — ein Termin daneben und derselbe Text noch einmal im Titel
 * wäre schlimmer als keine Erkennung.
 *
 * Erkannt wird eine überschaubare Liste, mehr nicht. Ein Parser, der alles versucht,
 * verschluckt irgendwann einen Aufgabentitel: Wer „Freitagsessen planen“ schreibt, meint
 * keinen Freitag.
 *
 * ## Wortgrenzen
 *
 * `\b` zählt in JavaScript wie in Java nur `[A-Za-z0-9_]` als Wortzeichen. Vor „ü“ steht
 * damit **keine** Wortgrenze, und `\bübermorgen\b` findet nie etwas. Dieselbe Falle
 * trifft „März“ und „nächsten“.
 *
 * Die Wortgrenzen stehen deshalb ausgeschrieben: hinten als Vorausschau
 * `(?![\p{L}\p{N}_])`, vorne als **mitgelesene Gruppe** `(^|[^\p{L}\p{N}_])` statt als
 * Rückschau. Rückschau (`(?<!…)`) kennen ältere Safari-Fassungen nicht, und ein Ausdruck,
 * der sich nicht übersetzen lässt, nimmt beim Laden das ganze Modul mit. Genau dieser
 * Fehler — derselbe Ausdruck, eine andere Regex-Maschine — hat die Android-Fassung
 * unstartbar gemacht.
 *
 * Reine Funktion: `now` kommt herein, nichts wird aus der Systemuhr gelesen.
 */

import { Balance } from "./balance.js";
import {
  dayOf,
  daysInMonth,
  epochDayOf,
  fromEpochDay,
  isoWeekday,
  minutesOfDay,
  plusYears,
} from "./time.js";

const WORD_START = "(^|[^\\p{L}\\p{N}_])";
const WORD_END = "(?![\\p{L}\\p{N}_])";
const OPTION = "am|an|bis|für";

/**
 * Baut einen Ausdruck mit Wortgrenzen. Gruppe 1 ist immer die verschluckte Grenze —
 * [find] rechnet sie wieder heraus.
 */
function bounded(body, { prefix = null, end = true } = {}) {
  const lead = prefix === null ? "" : `(?:(?:${prefix})\\s+)?`;
  return new RegExp(`${WORD_START}${lead}(?:${body})${end ? WORD_END : ""}`, "iu");
}

/** Sucht und rechnet die Grenzgruppe heraus. Gruppen sind ab 0 durchnummeriert. */
function find(regex, input) {
  const match = regex.exec(input);
  if (match === null) return null;
  const start = match.index + match[1].length;
  return {
    start,
    end: start + match[0].length - match[1].length,
    groups: match.slice(2).map((group) => group ?? ""),
  };
}

const OPTIONAL = { prefix: OPTION };

const RELATIVE_DAYS = [
  [bounded("übermorgen", OPTIONAL), 2],
  [bounded("uebermorgen", OPTIONAL), 2],
  [bounded("morgen", OPTIONAL), 1],
  [bounded("heute", OPTIONAL), 0],
];

const IN_N = bounded("in\\s+(\\d{1,3})\\s+(tagen|tage|tag|wochen|woche)");

const NEXT_WEEK = bounded("nächste[rn]?\\s+woche");

const WEEKDAY = bounded(
  "(?:(?:nächsten|nächste|kommenden|kommende)\\s+)?(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonnabend|sonntag)",
  OPTIONAL,
);

const WEEKDAYS = {
  montag: 1,
  dienstag: 2,
  mittwoch: 3,
  donnerstag: 4,
  freitag: 5,
  samstag: 6,
  sonnabend: 6,
  sonntag: 7,
};

/** `12.8.`, `12.08.2026`, `1.9.26` */
const NUMERIC_DATE = bounded("(\\d{1,2})\\.\\s?(\\d{1,2})\\.(?:\\s?(\\d{2,4}))?", {
  ...OPTIONAL,
  end: false,
});

/** `12. August`, `3 Januar 2027` */
const MONTH_NAME_DATE = bounded(
  "(\\d{1,2})\\.?\\s+(januar|februar|märz|maerz|april|mai|juni|juli|august|september|oktober|november|dezember)(?:\\s+(\\d{4}))?",
  OPTIONAL,
);

const MONTHS = {
  januar: 1, februar: 2, "märz": 3, maerz: 3, april: 4, mai: 5, juni: 6,
  juli: 7, august: 8, september: 9, oktober: 10, november: 11, dezember: 12,
};

/** `14:30`, `um 9:05` */
const CLOCK_TIME = bounded("(\\d{1,2}):(\\d{2})", { prefix: "um" });

/** `9 Uhr`, `um 9 Uhr`, `9.30 Uhr` */
const HOUR_ONLY = bounded("(\\d{1,2})(?:[.:](\\d{2}))?\\s*uhr", { prefix: "um" });

const VAGUE_TIME = bounded("(morgens|vormittags|mittags|nachmittags|abends|nachts)");

/** Die Uhrzeiten hinter „mittags“ und „abends“ stehen in `Balance`, nicht hier. */
const VAGUE_HOURS = {
  morgens: Balance.VAGUE_MORNING_HOUR,
  vormittags: Balance.VAGUE_MORNING_HOUR,
  mittags: Balance.VAGUE_NOON_HOUR,
  nachmittags: Balance.VAGUE_AFTERNOON_HOUR,
  abends: Balance.VAGUE_EVENING_HOUR,
  nachts: Balance.VAGUE_NIGHT_HOUR,
};

/**
 * Liest eine Zeile.
 *
 * @returns `{ title, day, minutes }` — `day` als Epochentag, `minutes` seit Mitternacht;
 *          beide `null`, wenn nichts erkannt wurde.
 */
export function parseQuickAdd(input, now) {
  const text = String(input ?? "");
  if (text.trim().length === 0) return { title: text.trim(), day: null, minutes: null };

  const hits = [];
  const today = dayOf(now);

  const day = findDate(text, today, hits);
  const minutes = findTime(text, hits);

  // Eine Uhrzeit ohne Tag meint heute — es sei denn, sie ist schon vorbei.
  let finalDay = day;
  if (day === null && minutes !== null) {
    finalDay = minutes > minutesOfDay(now) ? today : today + 1;
  }

  return { title: removeAll(text, hits), day: finalDay, minutes };
}

// ------------------------------------------------------------------------- Datum

function findDate(input, today, hits) {
  for (const [regex, days] of RELATIVE_DAYS) {
    const hit = find(regex, input);
    if (hit !== null) {
      hits.push(hit);
      return today + days;
    }
  }

  const inN = find(IN_N, input);
  if (inN !== null) {
    const amount = Number.parseInt(inN.groups[0], 10);
    if (Number.isFinite(amount)) {
      hits.push(inN);
      return today + (inN.groups[1].toLowerCase().startsWith("woche") ? amount * 7 : amount);
    }
  }

  const nextWeek = find(NEXT_WEEK, input);
  if (nextWeek !== null) {
    hits.push(nextWeek);
    return today + 7;
  }

  const weekday = find(WEEKDAY, input);
  if (weekday !== null) {
    const target = WEEKDAYS[weekday.groups[0].toLowerCase()];
    if (target !== undefined) {
      hits.push(weekday);
      return nextWeekday(today, target);
    }
  }

  const numeric = find(NUMERIC_DATE, input);
  if (numeric !== null) {
    const built = buildDate(
      Number.parseInt(numeric.groups[0], 10),
      Number.parseInt(numeric.groups[1], 10),
      numeric.groups[2] === "" ? null : Number.parseInt(numeric.groups[2], 10),
      today,
    );
    if (built !== null) {
      hits.push(numeric);
      return built;
    }
  }

  const named = find(MONTH_NAME_DATE, input);
  if (named !== null) {
    const built = buildDate(
      Number.parseInt(named.groups[0], 10),
      MONTHS[named.groups[1].toLowerCase()],
      named.groups[2] === "" ? null : Number.parseInt(named.groups[2], 10),
      today,
    );
    if (built !== null) {
      hits.push(named);
      return built;
    }
  }

  return null;
}

/**
 * Ein Datum ohne Jahr meint das nächste Vorkommen.
 *
 * Wer am 30. Dezember „2.1.“ tippt, meint den Januar danach und nicht den vergangenen.
 */
function buildDate(day, month, year, today) {
  if (!Number.isFinite(day) || !Number.isFinite(month)) return null;
  if (month < 1 || month > 12) return null;

  const fullYear = year === null ? fromEpochDay(today).getFullYear() : year < 100 ? 2000 + year : year;
  if (day < 1 || day > daysInMonth(fullYear, month - 1)) return null;

  const result = epochDayOf(fullYear, month - 1, day);
  return year === null && result < today ? plusYears(result, 1) : result;
}

/** Der nächste solche Wochentag — nie heute: „Montag“ am Montag meint den nächsten. */
function nextWeekday(today, target) {
  for (let offset = 1; offset <= 7; offset++) {
    if (isoWeekday(today + offset) === target) return today + offset;
  }
  return today + 7;
}

// ------------------------------------------------------------------------ Uhrzeit

function findTime(input, hits) {
  const clock = find(CLOCK_TIME, input);
  if (clock !== null && !overlapsAny(clock, hits)) {
    const minutes = toMinutes(clock.groups[0], clock.groups[1]);
    if (minutes !== null) {
      hits.push(clock);
      return minutes;
    }
  }

  const hourOnly = find(HOUR_ONLY, input);
  if (hourOnly !== null && !overlapsAny(hourOnly, hits)) {
    const minutes = toMinutes(hourOnly.groups[0], hourOnly.groups[1]);
    if (minutes !== null) {
      hits.push(hourOnly);
      return minutes;
    }
  }

  const vague = find(VAGUE_TIME, input);
  if (vague !== null && !overlapsAny(vague, hits)) {
    const hour = VAGUE_HOURS[vague.groups[0].toLowerCase()];
    if (hour !== undefined) {
      hits.push(vague);
      return hour * 60;
    }
  }

  return null;
}

function toMinutes(hourText, minuteText) {
  const hour = Number.parseInt(hourText, 10);
  const minute = minuteText === "" ? 0 : Number.parseInt(minuteText, 10);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null;
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
  return hour * 60 + minute;
}

// --------------------------------------------------------------------------- Rest

function overlapsAny(hit, others) {
  return others.some((other) => hit.start < other.end && other.start < hit.end);
}

/** Entfernt die erkannten Stellen und räumt die übrig gebliebenen Leerzeichen auf. */
function removeAll(input, hits) {
  if (hits.length === 0) return input.trim();

  let text = input;
  for (const hit of [...hits].sort((a, b) => b.start - a.start)) {
    text = text.slice(0, hit.start) + text.slice(hit.end);
  }
  return text
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^[,;–-]+|[,;–-]+$/g, "")
    .trim();
}
