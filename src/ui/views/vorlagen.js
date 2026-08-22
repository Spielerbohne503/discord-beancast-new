/**
 * Vorlagen.
 *
 * Ein Name und ein paar Punkte: „Wocheneinkauf“ mit fünf Zeilen, per Knopf neu angelegt.
 *
 * Übernommen wird als **eine** Aufgabe mit Unteraufgaben, nicht als fünf gleichrangige
 * Zeilen. Der Wocheneinkauf ist ein Vorhaben; was darin steht, sind seine Schritte. Fünf
 * Zeilen in der Heute-Liste wären fünfmal so viel Rauschen für dieselbe Sache.
 */

import * as repo from "../../data/repo.js";
import { aktualisieren, navigieren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { meldung } from "../toast.js";

export function vorlagenView() {
  const name = h("input.eingabe", { type: "text", placeholder: S.vorlagen_name });
  const punkte = h("textarea.eingabe", { placeholder: S.vorlagen_punkte, rows: 5 });

  const neu = h(
    "div.karte.gruppe",
    {},
    h("h2.gruppe__titel", {}, S.vorlagen_neu),
    name,
    punkte,
    h(
      "button.knopf.knopf--haupt",
      { onclick: () => void anlegen() },
      icon("plus", 16),
      S.vorlagen_anlegen,
    ),
  );

  const liste = h("div.abschnitt");
  const element = h("div.abschnitt", {}, neu, liste);

  async function anlegen() {
    const titel = name.value.trim();
    if (titel.length === 0) return;

    // Leere Zeilen fallen weg: Wer eine Liste tippt, hinterlässt am Ende fast immer eine.
    const zeilen = punkte.value
      .split("\n")
      .map((zeile) => zeile.replace(/^[-*\s]+/, "").trim())
      .filter(Boolean);

    name.value = "";
    punkte.value = "";
    await repo.createTemplate(titel, zeilen);
    await aktualisieren();
  }

  function update() {
    if (!state.bereit) return;

    fuellen(
      liste,
      state.templates.length === 0
        ? h("div.leer", {}, h("p", {}, S.vorlagen_keine))
        : state.templates.map((vorlage) => karte(vorlage)),
    );
  }

  return { el: element, update };
}

function karte(vorlage) {
  const listenwahl = h(
    "select.eingabe",
    { "aria-label": S.quickadd_list },
    state.lists.map((eintrag) =>
      h("option", { value: eintrag.id, selected: eintrag.id === vorlage.listId }, eintrag.name),
    ),
  );

  return h(
    "article.karte.karte--erhoben.karte--greifbar.gruppe",
    {},
    h(
      "div.gewohnheit__kopf",
      {},
      h("h3.gewohnheit__name", {}, vorlage.name),
      h(
        "button.knopf.knopf--still.knopf--rund",
        {
          "aria-label": S.vorlagen_loeschen,
          onclick: async () => {
            await repo.deleteTemplate(vorlage.id);
            await aktualisieren();
            meldung(vorlage.name, {
              rueckgaengig: async () => {
                await repo.updateTemplate(vorlage.id, { deletedAt: null });
                await aktualisieren();
              },
            });
          },
        },
        icon("papierkorb", 16),
      ),
    ),
    vorlage.punkte.length === 0
      ? null
      : h(
          "ul.vorlage__punkte",
          {},
          vorlage.punkte.map((punkt) => h("li", {}, punkt.title)),
        ),
    h(
      "div.vorlage__fuss",
      {},
      h("span.abzeichen", {}, S.vorlagen_punkte_zahl(vorlage.punkte.length)),
      listenwahl,
      h(
        "button.knopf.knopf--haupt.knopf--klein",
        {
          onclick: async () => {
            await repo.ausVorlage(vorlage.id, listenwahl.value);
            await aktualisieren();
            meldung(S.vorlagen_uebernommen(vorlage.name));
            navigieren("today");
          },
        },
        icon("plus", 14),
        S.vorlagen_benutzen,
      ),
    ),
  );
}
