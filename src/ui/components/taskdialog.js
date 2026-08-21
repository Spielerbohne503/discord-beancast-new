/**
 * Die Einzelheiten einer Aufgabe.
 *
 * Ein `<dialog>` statt eines eigenen Überlagerungsbaus: Der Browser bringt Fokusfalle,
 * Escape-Taste und Hintergrundabdunkelung schon mit — nachgebaut wird davon selten alles.
 *
 * Notiz und Titel werden beim Verlassen des Feldes gespeichert, nicht bei jedem Tastendruck:
 * Sonst schreibt jede Silbe eine Zeile und `updatedAt` wird wertlos.
 */

import { Priority } from "../../domain/balance.js";
import { Frequency, formatRule, parseRule } from "../../domain/recurrence.js";
import { dayOf, formatHhMm, isoDate, dayFromIso, minutesOfDay, parseHhMm } from "../../domain/time.js";
import { isCompleted, subtaskProgress } from "../../domain/tasks.js";
import * as repo from "../../data/repo.js";
import { aktualisieren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { PRIORITY_NAMES, S } from "../strings.js";
import { renderText } from "../format.js";
import { meldung } from "../toast.js";

const WIEDERHOLUNGEN = [
  { text: S.task_repeat_none, regel: null },
  { text: S.repeat_daily, regel: "FREQ=DAILY" },
  { text: S.repeat_weekly, regel: "FREQ=WEEKLY" },
  { text: S.repeat_weekdays, regel: "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" },
  { text: S.repeat_monthly, regel: "FREQ=MONTHLY" },
  { text: S.repeat_yearly, regel: "FREQ=YEARLY" },
];

export function taskDialog(taskId) {
  const aufgabe = state.tasks.find((task) => task.id === taskId);
  if (!aufgabe) return;

  const koerper = h("div.dialog__koerper");
  const dialog = h(
    "dialog.dialog",
    { onclose: () => dialog.remove() },
    h(
      "div.dialog__kopf",
      {},
      h("h2", {}, S.task_open_detail),
      h(
        "button.knopf.knopf--still.knopf--rund",
        { "aria-label": S.action_close, onclick: () => dialog.close() },
        icon("schliessen", 18),
      ),
    ),
    koerper,
    h(
      "div.dialog__fuss",
      {},
      h(
        "button.knopf.knopf--gefahr",
        {
          onclick: async () => {
            dialog.close();
            await repo.deleteTask(taskId);
            await aktualisieren();
            meldung(aufgabe.title, {
              rueckgaengig: async () => {
                await repo.restoreTask(taskId);
                await aktualisieren();
              },
            });
          },
        },
        icon("papierkorb", 16),
        S.task_delete,
      ),
      h("button.knopf.knopf--haupt", { onclick: () => dialog.close() }, S.action_close),
    ),
  );

  document.body.append(dialog);
  zeichnen();
  dialog.showModal();

  function aktuelle() {
    return state.tasks.find((task) => task.id === taskId) ?? aufgabe;
  }

  async function speichern(patch) {
    await repo.updateTask(taskId, patch);
    await aktualisieren();
    zeichnen();
  }

  function zeichnen() {
    const task = aktuelle();
    const tag = task.dueAt === null || task.dueAt === undefined ? null : dayOf(task.dueAt);
    const kinder = state.tasks.filter((anderer) => anderer.parentId === taskId);

    fuellen(
      koerper,
      titelfeld(task, speichern),
      notizfeld(task, speichern),
      faelligkeit(task, tag),
      prioritaet(task, speichern),
      wiederholung(task, speichern),
      unteraufgaben(task, kinder, zeichnen),
      liste(task, speichern),
    );
  }

  function faelligkeit(task, tag) {
    const datum = h("input.eingabe", {
      type: "date",
      value: tag === null ? "" : isoDate(tag),
      onchange: async () => {
        const gewaehlt = datum.value === "" ? null : dayFromIso(datum.value);
        const minuten = zeit.value === "" ? null : parseHhMm(zeit.value);
        await repo.setDue(taskId, gewaehlt, gewaehlt === null ? null : minuten);
        await aktualisieren();
        zeichnen();
      },
    });

    const zeit = h("input.eingabe", {
      type: "time",
      value: task.hasTime && task.dueAt ? formatHhMm(minutesOfDay(task.dueAt)) : "",
      onchange: async () => {
        if (datum.value === "") return;
        await repo.setDue(taskId, dayFromIso(datum.value), zeit.value === "" ? null : parseHhMm(zeit.value));
        await aktualisieren();
        zeichnen();
      },
    });

    return h(
      "div.feld",
      {},
      h("span.feld__beschriftung", {}, S.task_due),
      h("div.zahlenpaar", {}, datum, zeit),
    );
  }
}

function titelfeld(task, speichern) {
  const feld = h("input.eingabe", {
    type: "text",
    value: task.title,
    "aria-label": S.task_title,
    onblur: () => {
      const neu = feld.value.trim();
      if (neu.length > 0 && neu !== task.title) void speichern({ title: neu });
    },
  });
  return h("div.feld", {}, h("span.feld__beschriftung", {}, S.task_title), feld);
}

function notizfeld(task, speichern) {
  const feld = h("textarea.eingabe", {
    placeholder: S.task_note_placeholder,
    "aria-label": S.task_note,
    onblur: () => {
      const neu = feld.value.trim();
      if (neu !== (task.note ?? "")) void speichern({ note: neu.length === 0 ? null : neu });
    },
  });
  feld.value = task.note ?? "";

  // Die Vorschau zeigt, was aus den Verweisen wird — beim Schreiben sieht man die
  // Klammerschreibweise, in der Liste nur den Text.
  const vorschau = task.note
    ? h("div.feld__hinweis", {}, renderText(task.note))
    : null;

  return h("div.feld", {}, h("span.feld__beschriftung", {}, S.task_note), feld, vorschau);
}

function prioritaet(task, speichern) {
  return h(
    "div.feld",
    {},
    h("span.feld__beschriftung", {}, S.task_priority),
    h(
      "div.chips",
      {},
      PRIORITY_NAMES.map((name, stufe) =>
        h(
          "button.chip",
          {
            type: "button",
            "aria-pressed": String(task.priority === stufe),
            onclick: () => void speichern({ priority: Priority.coerce(stufe) }),
          },
          name,
        ),
      ),
    ),
  );
}

function wiederholung(task, speichern) {
  const aktuelle = parseRule(task.rrule);
  const alsText = aktuelle === null ? null : formatRule(aktuelle);

  return h(
    "div.feld",
    {},
    h("span.feld__beschriftung", {}, S.task_repeat),
    h(
      "div.chips",
      {},
      WIEDERHOLUNGEN.map((eintrag) =>
        h(
          "button.chip",
          {
            type: "button",
            "aria-pressed": String(alsText === eintrag.regel),
            onclick: () => void speichern({ rrule: eintrag.regel }),
          },
          eintrag.text,
        ),
      ),
    ),
    aktuelle !== null && task.dueAt === null
      ? h("span.feld__hinweis", {}, S.task_due_none)
      : null,
  );
}

function unteraufgaben(task, kinder, neuZeichnen) {
  const eingabe = h("input.eingabe", {
    type: "text",
    placeholder: S.task_subtask_add,
    onkeydown: async (ereignis) => {
      if (ereignis.key !== "Enter") return;
      const titel = eingabe.value.trim();
      if (titel.length === 0) return;
      eingabe.value = "";
      await repo.createTask({ listId: task.listId, parentId: task.id, title: titel });
      await aktualisieren();
      neuZeichnen();
    },
  });

  const fortschritt = subtaskProgress(kinder);

  return h(
    "div.feld",
    {},
    h(
      "span.feld__beschriftung",
      {},
      S.task_subtasks,
      fortschritt.hasSubtasks ? ` · ${fortschritt.done}/${fortschritt.total}` : "",
    ),
    kinder.length === 0
      ? null
      : h(
          "ul.liste",
          {},
          kinder.map((kind) =>
            h(
              "li.zeile",
              { dataset: { prio: String(kind.priority) } },
              h(
                "button.haken",
                {
                  type: "button",
                  "aria-pressed": String(isCompleted(kind)),
                  onclick: async () => {
                    if (isCompleted(kind)) await repo.uncompleteTask(kind.id);
                    else await repo.completeTask(kind.id);
                    await aktualisieren();
                    neuZeichnen();
                  },
                },
                icon("haken", 14),
              ),
              h("div.zeile__mitte", {}, renderText(kind.title, { klasse: "zeile__titel" })),
            ),
          ),
        ),
    eingabe,
  );
}

function liste(task, speichern) {
  const wahl = h(
    "select.eingabe",
    { onchange: () => void speichern({ listId: wahl.value }) },
    state.lists.map((eintrag) =>
      h("option", { value: eintrag.id, selected: eintrag.id === task.listId }, eintrag.name),
    ),
  );
  return h("div.feld", {}, h("span.feld__beschriftung", {}, S.quickadd_list), wahl);
}

export { Frequency };
