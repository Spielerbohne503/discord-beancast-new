/**
 * Der Rückblick.
 *
 * Zahlen ohne Belehrung: keine Bewertung, kein „nur 3 von 7“. Die Lücken im Diagramm
 * bleiben stehen — ein Diagramm, das nur die guten Tage zeigt, ist eine Werbebroschüre.
 */

import { Balance } from "../../domain/balance.js";
import { statsOf } from "../../domain/stats.js";
import { isCompleted, isDeleted } from "../../domain/tasks.js";
import { dayOf, isoWeekday } from "../../domain/time.js";
import { fuellen, h } from "../dom.js";
import { state } from "../store.js";
import { S } from "../strings.js";
import { formatDay } from "../format.js";
import { hochzaehlen } from "../motion.js";

export function statsView() {
  const element = h("div.abschnitt");

  function update() {
    if (!state.bereit) return;

    const tage = state.tasks
      .filter((task) => isCompleted(task) && !isDeleted(task))
      .map((task) => dayOf(task.completedAt));

    const rueckblick = statsOf(tage, state.today, Balance.STATS_WINDOW_DAYS, state.focusRounds);
    const hoechster = Math.max(1, ...rueckblick.days.map((eintrag) => eintrag.count));

    fuellen(
      element,
      h(
        "div.kacheln",
        {},
        kachel(S.stats_streak, S.stats_streak_days(rueckblick.streak)),
        kachel(S.stats_longest, S.stats_streak_days(rueckblick.longestStreak)),
        kachel(S.stats_total, String(rueckblick.totalCompleted), rueckblick.totalCompleted),
        kachel(S.stats_focus_rounds, String(rueckblick.focusRounds), rueckblick.focusRounds),
      ),
      h(
        "section.karte.abschnitt",
        {},
        h(
          "div.abschnitt__kopf.hilfslinie",
          { style: { padding: "16px 16px 0" } },
          h("h2.abschnitt__titel", {}, S.stats_period(Balance.STATS_WINDOW_DAYS)),
          h("span.abschnitt__zahl", {}, `(${rueckblick.periodCompleted})`),
        ),
        h(
          "div.diagramm",
          {},
          rueckblick.days.map((eintrag, index) =>
            h("div.diagramm__balken", {
              dataset: { leer: String(eintrag.count === 0) },
              style: {
                height: `${Math.max(3, (eintrag.count / hoechster) * 100)}%`,
                "animation-delay": `${index * 26}ms`,
              },
              title: `${formatDay(eintrag.day, state.today)}: ${eintrag.count}`,
            }),
          ),
        ),
        h(
          "div.diagramm__achse",
          {},
          rueckblick.days.map((eintrag) => h("span", {}, S.weekdays_short[isoWeekday(eintrag.day) - 1])),
        ),
      ),
      rueckblick.busiestDay
        ? h(
            "p.feld__hinweis",
            {},
            `${S.stats_busiest}: ${formatDay(rueckblick.busiestDay.day, state.today)} · ${rueckblick.busiestDay.count}`,
          )
        : null,
      h("p.feld__hinweis", {}, S.stats_today_counts_never_against_you),
    );
  }

  return { el: element, update };
}

/**
 * Eine Kennzahl.
 *
 * Zahlen zählen hoch statt zu erscheinen (Vorlage: „Count Up“) — man sieht dann, dass sie
 * gerechnet wurden. Alles, was keine reine Zahl ist („3 Tage“), bleibt stehen: Ein
 * hochzählendes Wort wäre Unfug.
 */
function kachel(name, zahl, roh = null) {
  const wert = h("span.kachel__zahl", {}, zahl);
  if (roh !== null) hochzaehlen(wert, roh, { formatieren: (n) => String(n) });

  return h("div.karte.kachel", {}, wert, h("span.kachel__name", {}, name));
}
