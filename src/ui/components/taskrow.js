/**
 * Eine Aufgabenzeile.
 *
 * Der Haken ist der einzige Weg zum Abhaken, das Antippen der Zeile öffnet die
 * Einzelheiten. Gelöscht wird mit „Rückgängig“ statt mit Rückfrage — ein Dialog, der jedes
 * Mal fragt, wird nach drei Tagen blind weggeklickt.
 */

import { RewardType, SpeechCategory } from "../../domain/pet.js";
import { isCompleted, isOverdue, overdueDays, subtaskProgress } from "../../domain/tasks.js";
import { parseRule } from "../../domain/recurrence.js";
import { postponeToTomorrow } from "../../domain/nag.js";
import { dayOf, minutesOfDay } from "../../domain/time.js";
import * as repo from "../../data/repo.js";
import { aktualisieren, reagieren, state } from "../store.js";
import { h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { badge, formatDue, renderText } from "../format.js";
import { meldung } from "../toast.js";
import { taskDialog } from "../components/taskdialog.js";

export function taskRow(task, { schlicht = false, verzoegerung = 0, ziehbar = false } = {}) {
  const erledigt = isCompleted(task);
  const ueberfaellig = isOverdue(task, state.now);

  const haken = h(
    "button.haken",
    {
      type: "button",
      "aria-pressed": String(erledigt),
      "aria-label": erledigt ? S.task_undone : S.task_done,
      onclick: (ereignis) => {
        ereignis.stopPropagation();
        void umschalten();
      },
    },
    icon("haken", 14),
  );

  const zeile = h(
    `li.zeile${erledigt ? ".zeile--erledigt" : ""}${ueberfaellig ? ".zeile--ueberfaellig" : ""}`,
    {
      dataset: { prio: String(task.priority), id: task.id },
      style: verzoegerung ? { "animation-delay": `${verzoegerung}ms` } : null,
      draggable: ziehbar ? "true" : null,
      onclick: () => taskDialog(task.id),
    },
    ziehbar ? h("span.zeile__griff", { "aria-hidden": "true" }, icon("griff", 16)) : null,
    haken,
    h(
      "div.zeile__mitte",
      {},
      renderText(task.title, { klasse: "zeile__titel" }),
      task.note && !schlicht ? renderText(task.note, { klasse: "zeile__notiz" }) : null,
      schlicht ? null : unterzeile(task, ueberfaellig),
    ),
    schlicht ? null : werkzeuge(task, ueberfaellig),
  );

  async function umschalten() {
    if (erledigt) {
      await repo.uncompleteTask(task.id);
      await aktualisieren();
      return;
    }

    // Die Zeile fliegt weg, bevor neu gezeichnet wird — sonst springt sie hart um.
    zeile.classList.add("zeile--verschwindet");
    await repo.completeTask(task.id);
    await aktualisieren();
    reagieren(SpeechCategory.TASK_DONE);
  }

  return zeile;
}

function unterzeile(task, ueberfaellig) {
  const teile = [];

  const faellig = formatDue(task, state.now);
  if (faellig) {
    teile.push(
      badge(faellig, { art: ueberfaellig ? "dringend" : null, symbol: task.hasTime ? "uhr" : "heute" }),
    );
  }

  if (ueberfaellig) {
    const tage = overdueDays(task, state.now);
    if (tage > 0) teile.push(badge(S.task_overdue_days(tage), { art: "dringend" }));
  }

  if (parseRule(task.rrule) !== null) teile.push(badge("", { symbol: "wiederholen" }));
  if (task.missedCount > 0) teile.push(badge(S.task_missed(task.missedCount), { art: "warm" }));

  const kinder = state.tasks.filter((anderer) => anderer.parentId === task.id);
  if (kinder.length > 0) {
    const fortschritt = subtaskProgress(kinder);
    teile.push(badge(`${fortschritt.done}/${fortschritt.total}`, { symbol: "listen" }));
  }

  if (state.lists.length > 1) {
    const liste = state.lists.find((eintrag) => eintrag.id === task.listId);
    if (liste) teile.push(badge(liste.name));
  }

  return teile.length === 0 ? null : h("div.zeile__unten", {}, teile);
}

function werkzeuge(task, ueberfaellig) {
  return h(
    "div.zeile__werkzeuge",
    {},
    // „Morgen“ steht nur an überfälligen Zeilen. An einer Aufgabe, die noch gar nicht
    // dran ist, wäre Verschieben eine Einladung, gar nicht erst anzufangen.
    ueberfaellig ? aufMorgen(task) : null,
    h(
      "button.knopf.knopf--still.knopf--rund",
      {
        type: "button",
        "aria-label": S.task_delete,
        onclick: async (ereignis) => {
          ereignis.stopPropagation();
          await repo.deleteTask(task.id);
          await aktualisieren();
          meldung(task.title, {
            rueckgaengig: async () => {
              await repo.restoreTask(task.id);
              await aktualisieren();
            },
          });
        },
      },
      icon("papierkorb", 16),
    ),
  );
}

/**
 * Einen Kalendertag weiter, Uhrzeit bleibt.
 *
 * Gerechnet wird über Tag + Uhrzeit, nie über `+ 86.400.000 ms` — sonst wandert die
 * 8-Uhr-Erinnerung bei jeder Zeitumstellung.
 */
function aufMorgen(task) {
  return h(
    "button.knopf.knopf--still.knopf--rund",
    {
      type: "button",
      "aria-label": S.task_tomorrow,
      title: S.task_tomorrow,
      onclick: async (ereignis) => {
        ereignis.stopPropagation();
        const vorher = { dueAt: task.dueAt, hasTime: task.hasTime, dueTimeLocal: task.dueTimeLocal };
        const ziel = postponeToTomorrow(task, Date.now());

        await repo.setDue(task.id, dayOf(ziel), task.hasTime ? minutesOfDay(ziel) : null);
        await aktualisieren();

        meldung(`${task.title} — ${S.task_due_tomorrow}`, {
          rueckgaengig: async () => {
            await repo.updateTask(task.id, vorher);
            await aktualisieren();
          },
        });
      },
    },
    icon("pfeil_rechts", 16),
  );
}

export { RewardType };
