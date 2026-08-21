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
        kachel(S.stats_total, String(rueckblick.totalCompleted)),
        kachel(S.stats_focus_rounds, String(rueckblick.focusRounds)),
      ),
      h(
        "section.karte.abschnitt",
        {},
        h(
          "div.abschnitt__kopf",
          { style: { padding: "16px 16px 0" } },
          h("h2.abschnitt__titel", {}, S.stats_period(Balance.STATS_WINDOW_DAYS)),
          h("span.abschnitt__zahl", {}, String(rueckblick.periodCompleted)),
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
          "div.diagramm",
          { style: { height: "auto", "padding-top": "0" } },
          rueckblick.days.map((eintrag) =>
            h(
              "span.abschnitt__zahl",
              { style: { flex: "1", "text-align": "center", "font-size": "0.62rem" } },
              S.weekdays_short[isoWeekday(eintrag.day) - 1],
            ),
          ),
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

function kachel(name, zahl) {
  return h("div.karte.kachel", {}, h("span.kachel__zahl", {}, zahl), h("span.kachel__name", {}, name));
}
