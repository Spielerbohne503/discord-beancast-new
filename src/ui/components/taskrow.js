/**
 * Eine Aufgabenzeile.
 *
 * Vier Wege, etwas mit ihr zu tun, und jeder hat seinen Grund:
 *
 * - **Haken antippen** — abhaken. Der eine offensichtliche Weg.
 * - **Zeile antippen** — Einzelheiten öffnen.
 * - **Wischen** — nach rechts abhaken, nach links auf morgen. Spart bei jeder Zeile zwei
 *   Antipper; genau dafür ist eine Aufgabenliste am Telefon da.
 * - **Lange drücken** — auswählen, dann mehrere auf einmal.
 *
 * Gelöscht wird mit „Rückgängig“ statt mit Rückfrage — ein Dialog, der jedes Mal fragt,
 * wird nach drei Tagen blind weggeklickt.
 */

import { RewardType, SpeechCategory } from "../../domain/pet.js";
import { isCompleted, isOverdue, overdueDays, subtaskProgress } from "../../domain/tasks.js";
import { parseRule } from "../../domain/recurrence.js";
import { postponeToTomorrow } from "../../domain/nag.js";
import { dayOf, minutesOfDay } from "../../domain/time.js";
import * as repo from "../../data/repo.js";
import { aktualisieren, auswahlUmschalten, reagieren, state } from "../store.js";
import { h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { badge, formatDue, formatDuration, renderText } from "../format.js";
import { meldung } from "../toast.js";
import { funken } from "../motion.js";
import { langDruecken, wischen } from "../gesten.js";
import { taskDialog } from "./taskdialog.js";

export function taskRow(task, { schlicht = false, verzoegerung = 0, ziehbar = false } = {}) {
  const erledigt = isCompleted(task);
  const ueberfaellig = isOverdue(task, state.now);
  const gewaehlt = state.auswahl.has(task.id);
  const imAuswahlmodus = state.auswahl.size > 0;

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
    `li.zeile${erledigt ? ".zeile--erledigt" : ""}${ueberfaellig ? ".zeile--ueberfaellig" : ""}${gewaehlt ? ".zeile--gewaehlt" : ""}`,
    {
      dataset: { prio: String(task.priority), id: task.id },
      style: verzoegerung ? { "animation-delay": `${verzoegerung}ms` } : null,
      draggable: ziehbar ? "true" : null,
      onclick: () => {
        // Im Auswahlmodus wählt ein Antippen aus, statt zu öffnen — sonst käme man aus
        // dem Modus nur wieder heraus, indem man ihn abbricht.
        if (state.auswahl.size > 0) auswahlUmschalten(task.id);
        else taskDialog(task.id);
      },
    },
    ziehbar ? h("span.zeile__griff", { "aria-hidden": "true" }, icon("griff", 16)) : null,
    haken,
    h(
      "div.zeile__mitte",
      {},
      renderText(task.title, { klasse: "zeile__titel", aufAufgabe: aufgabeAuflesen }),
      task.note && !schlicht ? renderText(task.note, { klasse: "zeile__notiz", aufAufgabe: aufgabeAuflesen }) : null,
      schlicht ? null : unterzeile(task, ueberfaellig),
    ),
    schlicht ? null : werkzeuge(task, ueberfaellig),
  );

  if (!schlicht && !imAuswahlmodus) {
    langDruecken(zeile, () => auswahlUmschalten(task.id));

    if (!erledigt) {
      wischen(zeile, {
        rechts: { text: S.task_swipe_done, art: "erledigt", tun: umschalten },
        links: { text: S.task_swipe_tomorrow, art: "morgen", tun: aufMorgenSchieben },
      });
    }
  }

  async function umschalten() {
    if (erledigt) {
      await repo.uncompleteTask(task.id);
      await aktualisieren();
      return;
    }

    // Ein Funke am Haken, dann fliegt die Zeile weg — sonst springt sie hart um.
    funken(haken);
    zeile.classList.add("zeile--verschwindet");
    await repo.completeTask(task.id);
    await aktualisieren();
    reagieren(SpeechCategory.TASK_DONE);
  }

  async function aufMorgenSchieben() {
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
  }

  return zeile;
}

/** Verweise der Form `[[Titel]]` zeigen auf eine offene Aufgabe mit genau diesem Titel. */
function aufgabeAuflesen(titel) {
  const gesucht = titel.trim().toLowerCase();
  const ziel = state.tasks.find(
    (task) => !task.deletedAt && task.title.trim().toLowerCase() === gesucht,
  );
  return ziel ? { id: ziel.id, oeffnen: () => taskDialog(ziel.id) } : null;
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

  // Was der Fokus an dieser Aufgabe verbracht hat — gerechnet aus abgeschlossenen Runden,
  // nicht aus einer zweiten Stoppuhr.
  const zeit = state.fokuszeit.get(task.id);
  if (zeit) teile.push(badge(S.task_focus_time(formatDuration(zeit.millis)), { symbol: "fokus" }));

  for (const etikett of etikettenVon(task.id)) {
    teile.push(h("span.etikett", { dataset: { farbe: etikett.color ?? "" } }, `#${etikett.name}`));
  }

  if (state.lists.length > 1) {
    const liste = state.lists.find((eintrag) => eintrag.id === task.listId);
    if (liste) teile.push(badge(liste.name));
  }

  return teile.length === 0 ? null : h("div.zeile__unten", {}, teile);
}

export function etikettenVon(taskId) {
  const kennungen = state.tagLinks.get(taskId) ?? [];
  return kennungen.map((id) => state.tags.find((tag) => tag.id === id)).filter(Boolean);
}

function werkzeuge(task, ueberfaellig) {
  return h(
    "div.zeile__werkzeuge",
    {},
    // „Morgen“ steht nur an überfälligen Zeilen. An einer Aufgabe, die noch gar nicht dran
    // ist, wäre es eine Einladung, gar nicht erst anzufangen. Gewischt wird trotzdem
    // überall — dort ist es eine bewusste Bewegung, kein Knopf, der herumsteht.
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
