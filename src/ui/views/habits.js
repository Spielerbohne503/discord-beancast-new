/**
 * Gewohnheiten.
 *
 * Sieben Punkte je Woche, ein Haken pro Termin. Was nicht ansteht, ist ausgegraut und
 * nicht anklickbar — eine Gewohnheit, die man an einem Tag ohne Termin abhaken kann, ist
 * keine Gewohnheit mehr, sondern eine Aufgabe.
 */

import { Schedule, besteSerie, darfAbhaken, fortschritt, hatZiel, serie } from "../../domain/habits.js";
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
      { style: { gap: "8px" } },
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
    await repo.createHabit(name, Schedule.DAILY, null);
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
  const laufend = serie(habit, haken, state.today);
  const beste = besteSerie(habit, haken);
  const woche = fortschritt(habit, haken, state.today);
  const montag = startOfWeek(state.today);
  const ziel = hatZiel(habit);

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
      h(
        `span.abzeichen${laufend > 0 ? ".abzeichen--gut" : ""}`,
        {},
        laufend === 0
          ? S.habits_streak_none
          : ziel
            ? S.habits_wochen_serie(laufend)
            : S.habits_streak(laufend),
      ),
      h("span.abzeichen", {}, S.habits_week(woche.done, woche.due)),
      beste > 0 ? h("span.abzeichen", {}, S.habits_longest(beste)) : null,
    ),
    rhythmus(habit),
  );
}

/**
 * Zwei Arten von Gewohnheit, ein Umschalter.
 *
 * **Feste Tage** beantworten „habe ich meinen Termin gehalten?“, ein **Wochenziel**
 * beantwortet „habe ich die Woche geschafft?“. Wer dreimal die Woche laufen will, hat am
 * Dienstag nichts versäumt — er hat nur noch nicht angefangen. Diese beiden Fragen in eine
 * Darstellung zu pressen, macht beide unscharf.
 */
function rhythmus(habit) {
  const ziel = hatZiel(habit);

  return h(
    "div.feld",
    {},
    h("span.feld__beschriftung", {}, S.habits_art),
    h(
      "div.chips",
      {},
      h(
        "button.chip",
        {
          type: "button",
          "aria-pressed": String(!ziel),
          onclick: async () => {
            await repo.updateHabit(habit.id, {
              target: null,
              // Ein leerer Zeitplan nach dem Umschalten hieße: steht nie an. Täglich ist
              // die Fassung, aus der man am schnellsten das gewünschte macht.
              schedule: Schedule.isEmpty(habit.schedule) ? Schedule.DAILY : habit.schedule,
            });
            await aktualisieren();
          },
        },
        S.habits_art_tage,
      ),
      h(
        "button.chip",
        {
          type: "button",
          "aria-pressed": String(ziel),
          onclick: async () => {
            await repo.updateHabit(habit.id, { target: Math.max(1, woechentlich(habit)) });
            await aktualisieren();
          },
        },
        S.habits_art_ziel,
      ),
    ),
    ziel ? zielwahl(habit) : zeitplan(habit),
  );
}

/** Beim Umschalten übernimmt das Ziel die bisherige Anzahl der Termine. */
function woechentlich(habit) {
  return Schedule.timesPerWeek(habit.schedule) || 3;
}

function zielwahl(habit) {
  return h(
    "div.chips",
    {},
    [1, 2, 3, 4, 5, 6, 7].map((anzahl) =>
      h(
        "button.chip",
        {
          type: "button",
          "aria-pressed": String(Number(habit.target) === anzahl),
          onclick: async () => {
            await repo.updateHabit(habit.id, { target: anzahl });
            await aktualisieren();
          },
        },
        S.habits_ziel(anzahl),
      ),
    ),
  );
}

function tag(habit, day, haken) {
  const steht = darfAbhaken(habit, day);
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
