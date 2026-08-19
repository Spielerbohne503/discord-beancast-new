/**
 * Wiederholungen nach RFC 5545 — kein Eigenbau-Format (Einbahnstraße).
 *
 * Unterstützt wird die Teilmenge, die eine Aufgabenverwaltung braucht: `FREQ`,
 * `INTERVAL`, `BYDAY`, `COUNT`, `UNTIL`. Alles andere wird beim Lesen übergangen statt
 * abgelehnt — eine fremde Regel aus einer fremden Sicherung darf die App nicht lahmlegen.
 *
 * Gerechnet wird in **Kalendertagen**, nicht in Zeitpunkten.
 */

import { isoWeekday, startOfWeek, plusMonths, plusYears, dayFromIso, isoDate } from "./time.js";

export const Frequency = Object.freeze({
  DAILY: "DAILY",
  WEEKLY: "WEEKLY",
  MONTHLY: "MONTHLY",
  YEARLY: "YEARLY",
});

const CODES = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"];
export const WEEKDAYS = [1, 2, 3, 4, 5];

/** Obergrenze gegen Endlosschleifen bei absurden Regeln. */
const MAX_STEPS = 5_000;

/**
 * Liest eine Regel. Gibt `null` zurück, wenn nichts Brauchbares darin steht — eine kaputte
 * Regel bedeutet „wiederholt sich nicht“, nicht „Absturz“.
 */
export function parseRule(text) {
  const body = String(text ?? "").trim().replace(/^RRULE:/i, "");
  if (body.length === 0) return null;

  const parts = new Map();
  for (const part of body.split(";")) {
    const index = part.indexOf("=");
    if (index <= 0) continue;
    parts.set(part.slice(0, index).toUpperCase(), part.slice(index + 1));
  }

  const frequency = Frequency[String(parts.get("FREQ") ?? "").toUpperCase()];
  if (!frequency) return null;

  const interval = Number.parseInt(parts.get("INTERVAL") ?? "", 10);
  const count = Number.parseInt(parts.get("COUNT") ?? "", 10);

  const byDay = (parts.get("BYDAY") ?? "")
    .split(",")
    // „2MO“ (zweiter Montag) wird auf den Wochentag reduziert; die Ordnungszahl
    // unterstützt diese Fassung nicht.
    .map((code) => CODES.indexOf(code.trim().slice(-2).toUpperCase()) + 1)
    .filter((weekday) => weekday > 0);

  return Object.freeze({
    frequency,
    interval: Number.isFinite(interval) && interval >= 1 ? interval : 1,
    byDay: Object.freeze([...new Set(byDay)].sort((a, b) => a - b)),
    count: Number.isFinite(count) && count >= 1 ? count : null,
    until: parseUntil(parts.get("UNTIL")),
  });
}

/** `UNTIL` darf laut Norm auch einen Zeitanteil tragen („20261231T235959Z“). */
function parseUntil(value) {
  const digits = String(value ?? "").slice(0, 8);
  if (!/^\d{8}$/.test(digits)) return null;
  const day = dayFromIso(`${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`);
  return Number.isFinite(day) ? day : null;
}

/** Zurück in einen RFC-5545-String. */
export function formatRule(rule) {
  const parts = [`FREQ=${rule.frequency}`];
  if (rule.interval !== 1) parts.push(`INTERVAL=${rule.interval}`);
  if (rule.byDay.length > 0) parts.push(`BYDAY=${rule.byDay.map((day) => CODES[day - 1]).join(",")}`);
  if (rule.count !== null) parts.push(`COUNT=${rule.count}`);
  if (rule.until !== null) parts.push(`UNTIL=${isoDate(rule.until).replaceAll("-", "")}`);
  return parts.join(";");
}

/** Regel mit einer verbrauchten Wiederholung. Bei `COUNT=1` ist danach Schluss. */
export function consumeOne(rule) {
  if (rule.count === null) return rule;
  if (rule.count <= 1) return null;
  return Object.freeze({ ...rule, count: rule.count - 1 });
}

/** Der nächste Termin nach [current]. */
export function nextAfter(rule, current) {
  if (rule.frequency === Frequency.DAILY) return current + rule.interval;
  if (rule.frequency === Frequency.WEEKLY) return nextWeekly(rule, current);
  if (rule.frequency === Frequency.MONTHLY) return plusMonths(current, rule.interval);
  return plusYears(current, rule.interval);
}

function nextWeekly(rule, current) {
  if (rule.byDay.length === 0) return current + 7 * rule.interval;

  // Innerhalb derselben Woche den nächsten gewählten Wochentag suchen …
  const weekday = isoWeekday(current);
  const later = rule.byDay.find((day) => day > weekday);
  if (later !== undefined) return current + (later - weekday);

  // … sonst in die nächste zulässige Woche springen.
  return startOfWeek(current) + 7 * rule.interval + (rule.byDay[0] - 1);
}

/**
 * Rückt eine Serie auf den ersten Termin **nach** [today] vor.
 *
 * **Von einer wiederkehrenden Aufgabe existiert immer nur eine offene Instanz.** Verpasste
 * Termine werden übersprungen und gezählt, erzeugen aber keine zusätzliche Last — sonst
 * tötet eine tägliche Aufgabe den Begleiter, während man im Urlaub ist.
 */
export function advance(rule, currentDue, today) {
  const finished = (skipped) => ({ nextDue: null, skipped: Math.max(0, skipped), remainingRule: null, isFinished: true });

  let remaining = rule;
  let candidate = currentDue;
  let skipped = -1; // Der erste Schritt ist der reguläre, kein verpasster.

  for (let step = 0; step < MAX_STEPS; step++) {
    if (remaining === null) return finished(skipped);

    candidate = nextAfter(remaining, candidate);
    skipped++;

    if (remaining.until !== null && candidate > remaining.until) return finished(skipped);

    remaining = consumeOne(remaining);

    if (candidate > today) {
      return { nextDue: candidate, skipped: Math.max(0, skipped), remainingRule: remaining, isFinished: false };
    }
    if (remaining === null) return finished(skipped);
  }

  // Sollte nie eintreten; lieber die Serie beenden als endlos rechnen.
  return finished(skipped);
}
