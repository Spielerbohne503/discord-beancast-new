/**
 * Wie Werte auf dem Bildschirm aussehen.
 *
 * Datumsformate und Verweise gehören nicht in `domain/` — die entscheidet, was gilt, nicht
 * wie es heißt. Hier steht das Wie, an einer Stelle.
 */

import { parseLinks } from "../domain/links.js";
import { dayOf, formatHhMm, fromEpochDay, isoWeekday, minutesOfDay } from "../domain/time.js";
import { S } from "./strings.js";
import { h } from "./dom.js";
import { icon } from "./icons.js";

const TAG_KURZ = new Intl.DateTimeFormat("de-DE", { weekday: "short", day: "numeric", month: "short" });
const TAG_LANG = new Intl.DateTimeFormat("de-DE", { weekday: "long", day: "numeric", month: "long" });
const TAG_MIT_JAHR = new Intl.DateTimeFormat("de-DE", { day: "numeric", month: "short", year: "numeric" });

export function formatDay(day, heute) {
  if (day === heute) return S.task_due_today;
  if (day === heute + 1) return S.task_due_tomorrow;
  if (day === heute - 1) return S.task_due_yesterday;

  const datum = fromEpochDay(day);
  // Innerhalb einer Woche reicht der Wochentag; darüber hinaus braucht es das Datum, und
  // jenseits des Jahreswechsels auch die Jahreszahl.
  if (day > heute && day < heute + 7) return S.weekdays_long[isoWeekday(day) - 1];
  if (datum.getFullYear() !== fromEpochDay(heute).getFullYear()) return TAG_MIT_JAHR.format(datum);
  return TAG_KURZ.format(datum);
}

export function formatLongDay(day) {
  return TAG_LANG.format(fromEpochDay(day));
}

/** „Morgen, 09:00“ — ohne Uhrzeit nur der Tag. */
export function formatDue(task, now) {
  if (task.dueAt === null || task.dueAt === undefined) return null;
  const tag = formatDay(dayOf(task.dueAt), dayOf(now));
  return task.hasTime ? `${tag}, ${formatHhMm(minutesOfDay(task.dueAt))}` : tag;
}

/**
 * Ein Text mit Verweisen, als Elemente.
 *
 * Aus `[Reel](…)` wird ein anklickbarer Verweis mit der Beschriftung; aus einer nackten
 * Adresse einer mit der Kurzform. Ohne `rel` würde die Zielseite über `window.opener` an
 * diesen Tab herankommen.
 */
export function renderText(text, { klasse = null } = {}) {
  const behaelter = h("span", klasse ? { class: klasse } : {});

  for (const stueck of parseLinks(text)) {
    if (stueck.kind === "text") {
      behaelter.append(document.createTextNode(stueck.text));
      continue;
    }
    behaelter.append(
      h(
        "a",
        {
          href: stueck.url,
          target: "_blank",
          rel: "noopener noreferrer",
          title: stueck.url,
          onclick: (ereignis) => ereignis.stopPropagation(),
        },
        stueck.label,
      ),
    );
  }
  return behaelter;
}

/** „25:00“ aus Millisekunden, aber als Wortmarke für Sperrzeiten: „1 h 12 min“. */
export function formatDuration(millis) {
  const minuten = Math.ceil(millis / 60_000);
  if (minuten <= 0) return "0 min";
  if (minuten < 60) return `${minuten} min`;
  const stunden = Math.floor(minuten / 60);
  const rest = minuten % 60;
  return rest === 0 ? `${stunden} h` : `${stunden} h ${rest} min`;
}

/** Ein kleines Abzeichen mit Symbol. */
export function badge(text, { art = null, symbol = null } = {}) {
  return h(
    `span.abzeichen${art ? `.abzeichen--${art}` : ""}`,
    {},
    symbol ? icon(symbol, 13) : null,
    text,
  );
}
