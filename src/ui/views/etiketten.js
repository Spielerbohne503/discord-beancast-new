/**
 * Etiketten.
 *
 * Die Tabelle gab es von Anfang an, die Oberfläche nicht — ein Feld, das niemand füllen
 * kann, ist totes Gewicht. Hier bekommt es einen Ort.
 *
 * Etiketten stehen **quer** zu Listen: Eine Aufgabe liegt in genau einer Liste, kann aber
 * beliebig viele Etiketten tragen. Deshalb sind Listen die Ordnung und Etiketten die
 * Suche.
 */

import * as repo from "../../data/repo.js";
import { Scope } from "../../domain/filter.js";
import { isOpen } from "../../domain/tasks.js";
import { aktualisieren, navigieren, setzen, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { meldung } from "../toast.js";

export function etikettenView() {
  const feld = h("input.eingabe", {
    type: "text",
    placeholder: S.tags_platzhalter,
    onkeydown: (ereignis) => {
      if (ereignis.key === "Enter") void anlegen();
    },
  });

  const neu = h(
    "div.karte.gruppe",
    {},
    h("h2.gruppe__titel", {}, S.tags_neu),
    h(
      "div.schnell__zeile",
      { style: { gap: "8px" } },
      feld,
      h(
        "button.knopf.knopf--haupt.knopf--rund",
        { onclick: () => void anlegen(), "aria-label": S.tags_neu },
        icon("plus"),
      ),
    ),
  );

  const liste = h("div.abschnitt");
  const element = h("div.abschnitt", {}, neu, liste);

  async function anlegen() {
    const name = feld.value.trim();
    if (name.length === 0) return;
    feld.value = "";
    await repo.createTag(name);
    await aktualisieren();
  }

  function update() {
    if (!state.bereit) return;

    const zaehlen = new Map();
    for (const [taskId, kennungen] of state.tagLinks) {
      const task = state.tasks.find((eintrag) => eintrag.id === taskId);
      if (!task || !isOpen(task)) continue;
      for (const id of kennungen) zaehlen.set(id, (zaehlen.get(id) ?? 0) + 1);
    }

    fuellen(
      liste,
      state.tags.length === 0
        ? h("div.leer", {}, h("p", {}, S.tags_keine))
        : h(
            "div.karte.navi",
            {},
            state.tags.map((tag) => zeile(tag, zaehlen.get(tag.id) ?? 0)),
          ),
    );
  }

  return { el: element, update };
}

function zeile(tag, anzahl) {
  return h(
    "div.navi__punkt",
    {},
    h(
      "button.etikett",
      {
        type: "button",
        // Ein Etikett anzutippen heißt: „zeig mir alles damit“. Gesucht wird über den
        // Namen mit Rautenzeichen — dieselbe Schreibweise, die man auch tippen würde.
        onclick: () => {
          setzen({ query: `#${tag.name}` });
          navigieren("browse", { kind: Scope.ALL_OPEN });
        },
      },
      `#${tag.name}`,
    ),
    h("span.navi__punkt-name", {}),
    h("span.navi__zahl", {}, String(anzahl)),
    h(
      "button.knopf.knopf--still.knopf--rund",
      {
        "aria-label": S.tags_loeschen,
        onclick: async () => {
          await repo.deleteTag(tag.id);
          await aktualisieren();
          meldung(`#${tag.name}`);
        },
      },
      icon("papierkorb", 16),
    ),
  );
}
