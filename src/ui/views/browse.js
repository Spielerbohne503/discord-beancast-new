/**
 * Listen und Suche.
 *
 * Die schlauen Listen sind Filter über denselben Bestand, keine eigenen Tabellen — eine
 * neue Ansicht kostet deshalb keine Schemaänderung. Von Hand umsortieren geht nur dort,
 * wo es einen Sinn ergibt: in einer echten Liste ohne Suchtext.
 */

import { Scope, filterTasks, isManuallyOrdered } from "../../domain/filter.js";
import * as repo from "../../data/repo.js";
import { aktualisieren, navigieren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { taskRow } from "../components/taskrow.js";
import { meldung } from "../toast.js";

export function browseView() {
  const suche = h("input.eingabe", {
    type: "search",
    placeholder: S.search_placeholder,
    "aria-label": S.action_search,
    oninput: () => {
      state.query = suche.value;
      inhaltZeichnen();
    },
  });

  const bereiche = h("div.bereiche");
  const kopf = h("div.abschnitt__kopf");
  const inhalt = h("div.abschnitt");
  const element = h(
    "div.abschnitt",
    {},
    bereiche,
    h("div.suchzeile", {}, icon("suchen", 18), suche),
    kopf,
    inhalt,
  );

  function titel() {
    if (state.scope.kind === Scope.LIST) {
      return state.lists.find((liste) => liste.id === state.scope.listId)?.name ?? S.lists_title;
    }
    if (state.scope.kind === Scope.NEXT_SEVEN_DAYS) return S.scope_next_seven;
    if (state.scope.kind === Scope.COMPLETED) return S.scope_completed;
    return S.scope_all_open;
  }

  /**
   * Die Bereichswahl — auf dem Telefon der **einzige** Weg zwischen den Listen.
   *
   * Am Schreibtisch steht dasselbe in der Seitenleiste; dort wird diese Zeile
   * ausgeblendet, statt sie doppelt zu zeigen.
   */
  function bereicheZeichnen() {
    const eintraege = [
      { kind: Scope.ALL_OPEN, text: S.scope_all_open },
      { kind: Scope.NEXT_SEVEN_DAYS, text: S.scope_next_seven },
      { kind: Scope.COMPLETED, text: S.scope_completed },
      ...state.lists.map((liste) => ({ kind: Scope.LIST, listId: liste.id, text: liste.name })),
    ];

    fuellen(
      bereiche,
      eintraege.map((eintrag) =>
        h(
          "button.chip",
          {
            type: "button",
            "aria-pressed": String(
              state.scope.kind === eintrag.kind &&
                (eintrag.kind !== Scope.LIST || state.scope.listId === eintrag.listId),
            ),
            onclick: () =>
              navigieren(
                "browse",
                eintrag.kind === Scope.LIST
                  ? { kind: Scope.LIST, listId: eintrag.listId }
                  : { kind: eintrag.kind },
              ),
          },
          eintrag.text,
        ),
      ),
      h(
        "button.chip",
        {
          type: "button",
          onclick: async () => {
            const name = globalThis.prompt(S.list_new_placeholder)?.trim();
            if (!name) return;
            const liste = await repo.createList(name);
            await aktualisieren();
            navigieren("browse", { kind: Scope.LIST, listId: liste.id });
          },
        },
        S.list_new,
      ),
    );
  }

  function inhaltZeichnen() {
    const aufgaben = filterTasks(state.tasks, state.scope, state.query, state.now);
    const ziehbar = isManuallyOrdered(state.scope, state.query);

    kopf.className = "abschnitt__kopf hilfslinie";
    fuellen(
      kopf,
      h("h2.abschnitt__titel", {}, titel()),
      h("span.abschnitt__zahl", {}, `(${aufgaben.length})`),
    );

    fuellen(
      inhalt,
      aufgaben.length === 0
        ? h("div.leer", {}, h("p", {}, state.query ? S.search_empty : S.today_empty_body))
        : ziehen(
            h(
              "ul.liste",
              {},
              aufgaben.map((task, index) =>
                taskRow(task, { ziehbar, verzoegerung: Math.min(index, 10) * 20 }),
              ),
            ),
            aufgaben,
            ziehbar,
          ),
      state.scope.kind === Scope.LIST ? listenwerkzeuge() : null,
    );
  }

  function listenwerkzeuge() {
    const liste = state.lists.find((eintrag) => eintrag.id === state.scope.listId);
    if (!liste) return null;

    const schalter = h("input", {
      type: "checkbox",
      checked: liste.excludeFromNag,
      onchange: async (ereignis) => {
        await repo.updateList(liste.id, { excludeFromNag: ereignis.target.checked });
        await aktualisieren();
      },
    });

    return h(
      "div.karte.gruppe",
      {},
      h(
        "label.schalter",
        {},
        h("span", {}, S.list_no_nag),
        schalter,
        h("span.schalter__gleis"),
      ),
      h("span.feld__hinweis", {}, S.list_no_nag_hint),
      h(
        "button.knopf.knopf--gefahr",
        {
          onclick: async () => {
            const geloescht = await repo.deleteList(liste.id);
            if (!geloescht) {
              meldung(S.list_delete_last);
              return;
            }
            await aktualisieren();
            navigieren("browse", { kind: Scope.ALL_OPEN });
          },
        },
        icon("papierkorb", 16),
        S.list_delete,
      ),
    );
  }

  function update() {
    if (!state.bereit) return;
    if (suche.value !== state.query) suche.value = state.query;
    bereicheZeichnen();
    inhaltZeichnen();
  }

  return { el: element, update };
}

/**
 * Umsortieren mit der Maus.
 *
 * Beim Loslassen wird **ein** Schlüssel geschrieben, nicht die halbe Liste — das ist der
 * ganze Grund für den Bruchindex.
 */
function ziehen(liste, aufgaben, erlaubt) {
  if (!erlaubt) return liste;

  let quelle = null;

  liste.addEventListener("dragstart", (ereignis) => {
    const zeile = ereignis.target.closest(".zeile");
    if (!zeile) return;
    quelle = zeile;
    zeile.classList.add("zeile--gezogen");
    ereignis.dataTransfer.effectAllowed = "move";
  });

  liste.addEventListener("dragover", (ereignis) => {
    ereignis.preventDefault();
    const ziel = ereignis.target.closest(".zeile");
    if (!ziel || ziel === quelle) return;
    for (const zeile of liste.children) zeile.classList.toggle("zeile--ziel", zeile === ziel);
  });

  liste.addEventListener("dragend", () => {
    quelle?.classList.remove("zeile--gezogen");
    for (const zeile of liste.children) zeile.classList.remove("zeile--ziel");
    quelle = null;
  });

  liste.addEventListener("drop", async (ereignis) => {
    ereignis.preventDefault();
    const ziel = ereignis.target.closest(".zeile");
    if (!quelle || !ziel || ziel === quelle) return;

    const reihenfolge = [...liste.children];
    const von = reihenfolge.indexOf(quelle);
    const nach = reihenfolge.indexOf(ziel);

    await repo.reorder(
      aufgaben.map((task) => task.id),
      aufgaben.map((task) => task.sortKey),
      von,
      nach,
    );
    await aktualisieren();
  });

  return liste;
}
