/**
 * Der Rückblick.
 *
 * Gerechnet wird über **Kalendertage**, nicht über Zeitpunkte — die Umrechnung in Ortszeit
 * passiert vorher, sonst zerrisse eine Reise nach Osten die Serie.
 *
 * Die eine Regel, die zählt: **Der heutige Tag zählt nie gegen einen.** Wer gestern etwas
 * geschafft hat und heute früh in die App schaut, hat seine Serie noch. Sonst stünde jeden
 * Morgen eine Null da — und eine App, die einen jeden Morgen bei null anfangen lässt,
 * macht keine Lust.
 */

/**
 * Die laufende Serie in Tagen.
 *
 * @param days Menge von Epochentagen mit mindestens einer erledigten Aufgabe
 */
export function streak(days, today) {
  let day;
  if (days.has(today)) day = today;
  else if (days.has(today - 1)) day = today - 1;
  else return 0;

  let length = 0;
  while (days.has(day)) {
    length++;
    day--;
  }
  return length;
}

/** Die längste Serie, die je zustande kam. */
export function longestStreak(days) {
  if (days.size === 0) return 0;

  let best = 0;
  let running = 0;
  let previous = null;

  for (const day of [...days].sort((a, b) => a - b)) {
    running = previous !== null && previous + 1 === day ? running + 1 : 1;
    if (running > best) best = running;
    previous = day;
  }
  return best;
}

/**
 * Ein Balken je Tag im Zeitraum — auch für Tage ohne Eintrag.
 *
 * Die Lücken gehören dazu: Ein Diagramm, das nur die guten Tage zeigt, ist kein Rückblick,
 * sondern eine Werbebroschüre.
 */
export function perDay(completedDays, from, to) {
  if (from > to) return [];

  const counted = new Map();
  for (const day of completedDays) counted.set(day, (counted.get(day) ?? 0) + 1);

  const result = [];
  for (let day = from; day <= to; day++) result.push({ day, count: counted.get(day) ?? 0 });
  return result;
}

/**
 * Alles zusammen.
 *
 * @param completedDays ein Eintrag je erledigter Aufgabe, als Epochentag in Ortszeit
 * @param windowDays wie viele Tage das Diagramm zeigt, heute eingeschlossen
 */
export function statsOf(completedDays, today, windowDays, focusRounds = 0) {
  const unique = new Set(completedDays);
  const days = perDay(completedDays, today - (windowDays - 1), today);
  const busiest = days.reduce((best, entry) => (entry.count > (best?.count ?? 0) ? entry : best), null);

  return {
    streak: streak(unique, today),
    longestStreak: longestStreak(unique),
    days,
    totalCompleted: completedDays.length,
    focusRounds,
    periodCompleted: days.reduce((total, entry) => total + entry.count, 0),
    busiestDay: busiest && busiest.count > 0 ? busiest : null,
  };
}
