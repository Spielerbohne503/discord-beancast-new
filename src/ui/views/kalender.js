/**
 * Der Monat auf einen Blick.
 *
 * Die Frage, die eine Liste nicht beantwortet: **Wie voll ist die nächste Woche?** Dafür
 * braucht es eine Fläche, keine Reihenfolge.
 *
 * Gezeigt wird pro Tag nur, **wie viel** ansteht — nicht was. Titel in Kalenderkästchen
 * sind auf einem Telefon nicht lesbar und auf einem großen Bildschirm eine Wand aus
 * Text. Wer wissen will, was an einem Tag ansteht, tippt ihn an.
 */

import { dueDay, isOpen, isOverdue } from "../../domain/tasks.js";
import { dayOf, isoDate, monatVerschieben, monatsName, monatsRaster } from "../../domain/time.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { formatLongDay } from "../format.js";
import { state } from "../store.js";
import { taskRow } from "../components/taskrow.js";

export function kalenderView() {
  // Der gezeigte Monat lebt in der Ansicht, nicht im Zustand: Er ist keine Eigenschaft
  // der Daten, sondern davon, wohin gerade geblättert wurde.
  let monat = null;
  let gewaehlt = null;

  const kopf = h("div.kalender__kopf");
  const raster = h("div.kalender");
  const tagesliste = h("div.abschnitt");
  const element = h("div.abschnitt", {}, kopf, raster, tagesliste);

  function update() {
    if (!state.bereit) return;
    if (monat === null) monat = state.today;
    if (gewaehlt === null) gewaehlt = state.today;

    const nachTag = aufgabenNachTag();

    fuellen(
      kopf,
      h(
        "button.knopf.knopf--rund",
        { onclick: () => blaettern(-1), "aria-label": S.kalender_zurueck },
        icon("pfeil_links", 18),
      ),
      h("h2.kalender__monat.display", {}, monatsName(monat)),
      h(
        "button.knopf.knopf--klein",
        { onclick: () => { monat = state.today; gewaehlt = state.today; update(); } },
        S.kalender_heute,
      ),
      h(
        "button.knopf.knopf--rund",
        { onclick: () => blaettern(1), "aria-label": S.kalender_vor },
        icon("pfeil_rechts", 18),
      ),
    );

    fuellen(
      raster,
      S.weekdays_short.map((name) => h("div.kalender__wochentag", {}, name)),
      monatsRaster(monat).flat().map((eintrag) => kasten(eintrag, nachTag)),
    );

    tagZeigen(nachTag);
  }

  function blaettern(schritte) {
    monat = monatVerschieben(monat, schritte);
    update();
  }

  function kasten(eintrag, nachTag) {
    const dort = nachTag.get(eintrag.day) ?? [];
    const ueberfaellig = dort.some((task) => isOverdue(task, state.now));

    return h(
      "button.kalender__tag",
      {
        type: "button",
        dataset: {
          fremd: String(!eintrag.imMonat),
          heute: String(eintrag.day === state.today),
          gewaehlt: String(eintrag.day === gewaehlt),
          dringend: String(ueberfaellig),
        },
        "aria-label": `${formatLongDay(eintrag.day)} — ${dort.length}`,
        onclick: () => {
          gewaehlt = eintrag.day;
          update();
        },
      },
      h("span.kalender__zahl", {}, String(Number(isoDate(eintrag.day).slice(8)))),
      // Punkte statt Zahlen: Man liest die Dichte, nicht den Zähler. Ab fünf wird es eine
      // Zahl, weil zwölf Punkte niemand mehr zählt.
      dort.length === 0
        ? null
        : dort.length > 4
          ? h("span.kalender__zahl-klein", {}, String(dort.length))
          : h(
              "span.kalender__punkte",
              {},
              dort.slice(0, 4).map(() => h("i")),
            ),
    );
  }

  function tagZeigen(nachTag) {
    const dort = (nachTag.get(gewaehlt) ?? []).sort((a, b) => (a.dueAt ?? 0) - (b.dueAt ?? 0));

    fuellen(
      tagesliste,
      h(
        "div.abschnitt__kopf.hilfslinie",
        {},
        h("h2.abschnitt__titel", {}, formatLongDay(gewaehlt)),
        h("span.abschnitt__zahl", {}, `(${dort.length})`),
      ),
      dort.length === 0
        ? h("div.leer", {}, h("p", {}, S.kalender_leer))
        : h("ul.liste", {}, dort.map((task, index) => taskRow(task, { verzoegerung: index * 18 }))),
    );
  }

  /** Nur offene Aufgaben mit Fälligkeit — Erledigtes gehört in den Rückblick, nicht hierher. */
  function aufgabenNachTag() {
    const nachTag = new Map();
    for (const task of state.tasks) {
      if (!isOpen(task) || task.parentId) continue;
      const tag = dueDay(task);
      if (tag === null) continue;

      if (!nachTag.has(tag)) nachTag.set(tag, []);
      nachTag.get(tag).push(task);
    }
    return nachTag;
  }

  return { el: element, update };
}

export { dayOf };
