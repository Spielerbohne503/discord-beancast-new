/**
 * Gewohnheiten.
 *
 * Sieben Punkte je Woche, ein Haken pro Termin. Was nicht ansteht, ist ausgegraut und
 * nicht anklickbar — eine Gewohnheit, die man an einem Tag ohne Termin abhaken kann, ist
 * keine Gewohnheit mehr, sondern eine Aufgabe.
 */

import { Schedule, currentStreak, longestStreak, weekProgress } from "../../domain/habits.js";
import { SpeechCategory } from "../../domain/pet.js";
import { startOfWeek } from "../../domain/time.js";
import * as repo from "../../data/repo.js";
import { aktualisieren, reagieren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { meldung } from "../toast.js";

export function habitsView() {
  const eingabe = h("input.eingabe", {
    type: "text",
    placeholder: S.habits_new_placeholder,
    onkeydown: (ereignis) => {
      if (ereignis.key === "Enter") void anlegen();
    },
  });

  const neu = h(
    "div.karte.gruppe",
    {},
    h("h2.gruppe__titel", {}, S.habits_new),
    h(
      "div.schnell__zeile",
      {},
      eingabe,
      h(
        "button.knopf.knopf--haupt.knopf--rund",
        { onclick: () => void anlegen(), "aria-label": S.habits_new },
        icon("plus"),
      ),
    ),
    h("span.feld__hinweis", {}, S.habits_never_overdue),
  );

  const liste = h("div.abschnitt");
  // Das Anlegen steht oben: Bei leerer Liste ist es das Einzige, was zählt, und bei
  // voller Liste tippt man es nicht am Ende der Seite.
  const element = h("div.abschnitt", {}, neu, liste);

  async function anlegen() {
    const name = eingabe.value.trim();
    if (name.length === 0) return;
    eingabe.value = "";
    await repo.createHabit(name, Schedule.DAILY);
    await aktualisieren();
  }

  function update() {
    if (!state.bereit) return;

    fuellen(
      liste,
      state.habits.length === 0
        ? h("div.leer", {}, h("p", {}, S.habits_empty))
        : state.habits.map((habit) => karte(habit)),
    );
  }

  return { el: element, update };
}

function karte(habit) {
  const haken = state.checkins.get(habit.id) ?? new Set();
  const serie = currentStreak(haken, habit.schedule, state.today);
  const beste = longestStreak(haken, habit.schedule);
  const woche = weekProgress(haken, habit.schedule, state.today);
  const montag = startOfWeek(state.today);

  return h(
    "article.karte.gewohnheit",
    {},
    h(
      "div.gewohnheit__kopf",
      {},
      h("h3.gewohnheit__name", {}, habit.name),
      h(
        "button.knopf.knopf--still.knopf--rund",
        {
          "aria-label": S.habits_delete,
          onclick: async () => {
            await repo.deleteHabit(habit.id);
            await aktualisieren();
            meldung(habit.name, {
              rueckgaengig: async () => {
                await repo.updateHabit(habit.id, { deletedAt: null });
                await aktualisieren();
              },
            });
          },
        },
        icon("papierkorb", 16),
      ),
    ),
    h(
      "div.woche",
      {},
      Array.from({ length: 7 }, (_, versatz) => tag(habit, montag + versatz, haken)),
    ),
    h(
      "div.gewohnheit__unten",
      {},
      // Grün nur, wenn tatsächlich eine Serie läuft — „0 Termine in Folge“ in der
      // Erfolgsfarbe wäre ein Widerspruch in sich.
      h(`span.abzeichen${serie > 0 ? ".abzeichen--gut" : ""}`, {}, serie === 0 ? S.habits_streak_none : S.habits_streak(serie)),
      h("span.abzeichen", {}, S.habits_week(woche.done, woche.due)),
      beste > 0 ? h("span.abzeichen", {}, S.habits_longest(beste)) : null,
    ),
    zeitplan(habit),
  );
}

function tag(habit, day, haken) {
  const steht = Schedule.isDueOn(habit.schedule, day);
  const gesetzt = haken.has(day);
  const wochentag = day - startOfWeek(day);

  return h(
    "button.woche__tag",
    {
      type: "button",
      disabled: !steht,
      "aria-pressed": String(gesetzt),
      dataset: { heute: String(day === state.today) },
      "aria-label": `${S.weekdays_long[wochentag]} — ${habit.name}`,
      onclick: async () => {
        const gesetztJetzt = await repo.toggleCheckin(habit.id, day);
        await aktualisieren();
        if (gesetztJetzt) reagieren(SpeechCategory.TASK_DONE);
      },
    },
    h("span", {}, S.weekdays_short[wochentag]),
    h("span.woche__punkt"),
  );
}

/** Der Zeitplan als sieben Umschalter — dieselbe Reihenfolge wie die Wochenübersicht. */
function zeitplan(habit) {
  return h(
    "div.feld",
    {},
    h("span.feld__beschriftung", {}, S.habits_schedule),
    h(
      "div.chips",
      {},
      S.weekdays_short.map((name, index) =>
        h(
          "button.chip",
          {
            type: "button",
            "aria-pressed": String(Schedule.isDueOn(habit.schedule, startOfWeek(state.today) + index)),
            onclick: async () => {
              await repo.updateHabit(habit.id, {
                schedule: Schedule.toggle(habit.schedule, index + 1),
              });
              await aktualisieren();
            },
          },
          name,
        ),
      ),
    ),
  );
}
