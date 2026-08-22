/**
 * Kalenderrechnung in Ortszeit.
 *
 * Der Unterschied zwischen *Zeitpunkt* und *Tag* zieht sich durch die ganze App: Eine
 * Fälligkeit ist ein Zeitpunkt, ein Haken an einer Gewohnheit ist ein Tag. Wer beides
 * vermischt, verschiebt bei einem Zeitzonenwechsel Einträge auf den Vortag.
 *
 * Alle Funktionen hier sind rein: Sie lesen nie die Uhr, sondern bekommen `now` gereicht.
 */

export const MS_PER_MINUTE = 60_000;
export const MS_PER_HOUR = 3_600_000;
export const MS_PER_DAY = 86_400_000;

/** Der Kalendertag eines Zeitpunkts als Tage seit dem 1.1.1970, in Ortszeit. */
export function dayOf(millis) {
  const date = new Date(millis);
  return epochDayOf(date.getFullYear(), date.getMonth(), date.getDate());
}

/** Tagesbeginn (00:00 Ortszeit) eines Kalendertags als Zeitpunkt. */
export function startOfDay(day) {
  const date = fromEpochDay(day);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

/** Tagesende, also der Beginn des Folgetags. */
export function endOfDay(day) {
  return startOfDay(day + 1);
}

/** Ein Zeitpunkt an einem Tag zu einer Uhrzeit. */
export function atTime(day, hour, minute = 0) {
  const date = fromEpochDay(day);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), hour, minute, 0, 0).getTime();
}

/** Kalendertag aus Jahr/Monat/Tag — Monat ist nullbasiert wie in `Date`. */
export function epochDayOf(year, monthIndex, dayOfMonth) {
  // Über UTC rechnen, damit Sommerzeit die Tagesgrenze nicht verschiebt: Es geht hier
  // um die Nummer des Kalendertags, nicht um eine Dauer.
  return Math.floor(Date.UTC(year, monthIndex, dayOfMonth) / MS_PER_DAY);
}

/** Ein `Date` in Ortszeit (Mittag), das diesen Kalendertag darstellt. */
export function fromEpochDay(day) {
  const utc = new Date(day * MS_PER_DAY);
  // Mittag statt Mitternacht: So kann keine Zeitumstellung den Tag kippen.
  return new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate(), 12);
}

/** Wochentag: 1 = Montag … 7 = Sonntag (wie ISO, nicht wie `Date.getDay()`). */
export function isoWeekday(day) {
  const weekday = fromEpochDay(day).getDay();
  return weekday === 0 ? 7 : weekday;
}

/** Der Montag der Woche, in der [day] liegt. */
export function startOfWeek(day) {
  return day - (isoWeekday(day) - 1);
}

/** Tage zwischen zwei Kalendertagen. */
export function daysBetween(from, to) {
  return to - from;
}

/** `YYYY-MM-DD` — für Sicherungen und Tests lesbar. */
export function isoDate(day) {
  const date = fromEpochDay(day);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const dayOfMonth = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${dayOfMonth}`;
}

/** Umkehrung von [isoDate]. */
export function dayFromIso(iso) {
  const [year, month, dayOfMonth] = iso.split("-").map(Number);
  return epochDayOf(year, month - 1, dayOfMonth);
}

/** Minuten seit Mitternacht eines Zeitpunkts, in Ortszeit. */
export function minutesOfDay(millis) {
  const date = new Date(millis);
  return date.getHours() * 60 + date.getMinutes();
}

/** `HH:MM` aus Minuten seit Mitternacht. */
export function formatHhMm(minutes) {
  const hour = Math.floor(minutes / 60) % 24;
  const minute = minutes % 60;
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

/** Minuten seit Mitternacht aus `HH:MM`; `null`, wenn unlesbar. */
export function parseHhMm(text) {
  const match = /^(\d{1,2}):(\d{2})$/.exec(String(text ?? "").trim());
  if (!match) return null;

  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (hour > 23 || minute > 59) return null;
  return hour * 60 + minute;
}

/**
 * Monate weiterrücken, mit **Abschneiden am Monatsende**.
 *
 * Der 31. Januar wird im Februar zum 28. (bzw. 29.), nicht ausgelassen. Für eine
 * Aufgabenverwaltung ist das die nützlichere Auslegung — eine Monatsaufgabe soll auch im
 * Februar erscheinen.
 */
export function plusMonths(day, months) {
  const date = fromEpochDay(day);
  const year = date.getFullYear();
  const monthIndex = date.getMonth() + months;
  const dayOfMonth = Math.min(date.getDate(), daysInMonth(year, monthIndex));
  return epochDayOf(year, monthIndex, dayOfMonth);
}

/** Jahre weiterrücken. Der 29. Februar wird im Normaljahr zum 28. */
export function plusYears(day, years) {
  const date = fromEpochDay(day);
  const year = date.getFullYear() + years;
  const dayOfMonth = Math.min(date.getDate(), daysInMonth(year, date.getMonth()));
  return epochDayOf(year, date.getMonth(), dayOfMonth);
}

/** Länge eines Monats; `monthIndex` darf über 11 hinaus- oder unter 0 gehen. */
export function daysInMonth(year, monthIndex) {
  return new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
}


/**
 * Das Raster eines Monats, wie ein Kalender es zeigt.
 *
 * Immer volle Wochen von Montag bis Sonntag — die Tage davor und danach gehören zum
 * Nachbarmonat und stehen trotzdem da. Ein Kalender, dessen erste Zeile mit einer Lücke
 * anfängt, ist schwerer zu lesen als einer, der den 30. Juni zeigt.
 *
 * @returns Array von Wochen, jede Woche ein Array aus 7 `{ day, imMonat }`
 */
export function monatsRaster(imMonat) {
  const datum = fromEpochDay(imMonat);
  const erster = epochDayOf(datum.getFullYear(), datum.getMonth(), 1);
  const letzter = epochDayOf(datum.getFullYear(), datum.getMonth() + 1, 0);

  const wochen = [];
  for (let tag = startOfWeek(erster); tag <= letzter; tag += 7) {
    wochen.push(
      Array.from({ length: 7 }, (_, versatz) => ({
        day: tag + versatz,
        imMonat: tag + versatz >= erster && tag + versatz <= letzter,
      })),
    );
  }
  return wochen;
}

/** Einen Monat weiter oder zurück, immer auf dem Ersten. */
export function monatVerschieben(imMonat, schritte) {
  const datum = fromEpochDay(imMonat);
  return epochDayOf(datum.getFullYear(), datum.getMonth() + schritte, 1);
}

/** `August 2026` — die Überschrift über dem Raster. */
const MONAT_JAHR = new Intl.DateTimeFormat("de-DE", { month: "long", year: "numeric" });

export function monatsName(day) {
  return MONAT_JAHR.format(fromEpochDay(day));
}
