/**
 * Die Leiste für mehrere Aufgaben auf einmal.
 *
 * Erscheint, sobald etwas ausgewählt ist, und verschwindet, sobald nichts mehr ausgewählt
 * ist — es gibt keinen „Auswahlmodus“ als eigenen Zustand, den man von Hand ein- und
 * ausschalten müsste. Weniger Schalter, weniger Wege, in einem Zustand steckenzubleiben.
 *
 * Jede Handlung hier ist eine Schleife über dieselben Funktionen, die auch eine einzelne
 * Zeile benutzt. Ein zweiter Weg, zehn Aufgaben abzuhaken, wäre ein zweiter Weg, es falsch
 * zu machen — und derjenige, der die Belohnung vergisst.
 */

import { postponeToTomorrow } from "../../domain/nag.js";
import { dayOf, minutesOfDay } from "../../domain/time.js";
import * as repo from "../../data/repo.js";
import { aktualisieren, auswahlLeeren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { meldung } from "../toast.js";

export function auswahlleiste() {
  const behaelter = h("div");

  function zeichnen() {
    const anzahl = state.auswahl.size;
    if (anzahl === 0) {
      fuellen(behaelter);
      return behaelter;
    }

    const listenwahl = h(
      "select.eingabe.auswahl__liste",
      {
        onchange: async () => {
          if (listenwahl.value === "") return;
          await fuerAlle((id) => repo.updateTask(id, { listId: listenwahl.value }));
        },
      },
      h("option", { value: "" }, S.auswahl_verschieben),
      state.lists.map((liste) => h("option", { value: liste.id }, liste.name)),
    );

    fuellen(
      behaelter,
      h(
        "div.auswahl",
        { role: "toolbar", "aria-label": S.auswahl_titel(anzahl) },
        h("span.auswahl__zahl", {}, S.auswahl_titel(anzahl)),
        h(
          "div.auswahl__knoepfe",
          {},
          h(
            "button.knopf.knopf--haupt.knopf--klein",
            { onclick: () => void abhaken() },
            icon("haken", 14),
            S.auswahl_abhaken,
          ),
          h(
            "button.knopf.knopf--klein",
            { onclick: () => void aufMorgen() },
            icon("pfeil_rechts", 14),
            S.auswahl_morgen,
          ),
          listenwahl,
          h(
            "button.knopf.knopf--gefahr.knopf--klein",
            { onclick: () => void loeschen() },
            icon("papierkorb", 14),
            S.auswahl_loeschen,
          ),
          h(
            "button.knopf.knopf--still.knopf--klein",
            { onclick: () => auswahlLeeren() },
            S.auswahl_abbrechen,
          ),
        ),
      ),
    );
    return behaelter;
  }

  /**
   * Nacheinander, nicht gleichzeitig.
   *
   * Jede dieser Funktionen liest den Stand, rechnet und schreibt zurück — parallel
   * ausgeführt überschrieben sie sich gegenseitig den Zwischenstand des Begleiters.
   */
  async function fuerAlle(tun) {
    const kennungen = [...state.auswahl];
    for (const id of kennungen) await tun(id);

    auswahlLeeren();
    await aktualisieren();
    return kennungen;
  }

  async function abhaken() {
    const kennungen = await fuerAlle((id) => repo.completeTask(id));
    meldung(S.auswahl_titel(kennungen.length), {
      rueckgaengig: async () => {
        for (const id of kennungen) await repo.uncompleteTask(id);
        await aktualisieren();
      },
    });
  }

  async function aufMorgen() {
    const vorher = new Map(
      [...state.auswahl].map((id) => {
        const task = state.tasks.find((eintrag) => eintrag.id === id);
        return [id, { dueAt: task?.dueAt ?? null, hasTime: task?.hasTime ?? false, dueTimeLocal: task?.dueTimeLocal ?? null }];
      }),
    );

    await fuerAlle(async (id) => {
      const task = state.tasks.find((eintrag) => eintrag.id === id);
      if (!task) return;
      const ziel = postponeToTomorrow(task, Date.now());
      await repo.setDue(id, dayOf(ziel), task.hasTime ? minutesOfDay(ziel) : null);
    });

    meldung(S.task_due_tomorrow, {
      rueckgaengig: async () => {
        for (const [id, stand] of vorher) await repo.updateTask(id, stand);
        await aktualisieren();
      },
    });
  }

  async function loeschen() {
    const kennungen = await fuerAlle((id) => repo.deleteTask(id));
    meldung(S.auswahl_titel(kennungen.length), {
      rueckgaengig: async () => {
        for (const id of kennungen) await repo.restoreTask(id);
        await aktualisieren();
      },
    });
  }

  return { el: behaelter, update: zeichnen };
}
