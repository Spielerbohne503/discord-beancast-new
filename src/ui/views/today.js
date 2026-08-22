/**
 * Die Heute-Ansicht: Schnell-Eingabe, drei Blöcke, Erledigtes.
 *
 * Die Schnell-Eingabe wird **einmal** gebaut und danach nie ersetzt — ein neu gezeichnetes
 * Eingabefeld verliert den Schreibmarke-Fokus und den halb getippten Satz.
 */

import { groupToday } from "../../domain/tasks.js";
import { parseQuickAdd } from "../../domain/quickadd.js";
import { formatHhMm } from "../../domain/time.js";
import * as repo from "../../data/repo.js";
import { aktualisieren, reagieren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";
import { formatDay } from "../format.js";
import { taskRow } from "../components/taskrow.js";
import { petStrip } from "../components/petstrip.js";
import { auswahlleiste } from "../components/auswahlleiste.js";
import { SpeechCategory } from "../../domain/pet.js";
import { stempeln } from "../motion.js";

export function todayView() {
  const eingabe = h("input.schnell__eingabe", {
    type: "text",
    placeholder: S.quickadd_placeholder,
    autocomplete: "off",
    "aria-label": S.quickadd_placeholder,
    oninput: () => vorschauZeichnen(),
    onkeydown: (ereignis) => {
      if (ereignis.key === "Enter") {
        ereignis.preventDefault();
        void absenden();
      }
    },
  });

  const vorschau = h("span.schnell__vorschau");
  const listenwahl = h("select.schnell__auswahl", { "aria-label": S.quickadd_list });

  const schnell = h(
    "div.schnell",
    {},
    h(
      "div.schnell__zeile",
      {},
      eingabe,
      h(
        "button.knopf.knopf--haupt.knopf--rund",
        { onclick: () => void absenden(), "aria-label": S.quickadd_add },
        icon("plus"),
      ),
    ),
    h("div.schnell__fuss", {}, vorschau, listenwahl),
  );

  const streifen = h("div");
  const bloecke = h("div.abschnitt");
  const leiste = auswahlleiste();
  const element = h("div.abschnitt", {}, leiste.el, streifen, schnell, bloecke);

  function vorschauZeichnen() {
    const gelesen = parseQuickAdd(eingabe.value, state.now);
    const teile = [];
    if (gelesen.day !== null) teile.push(formatDay(gelesen.day, state.today));
    if (gelesen.minutes !== null) teile.push(formatHhMm(gelesen.minutes));

    // Ein erkanntes Datum wird ausgefüllt dargestellt: Man soll sehen, dass etwas gegriffen
    // hat, **bevor** man absendet — nicht erst danach an der fertigen Zeile.
    vorschau.classList.toggle("schnell__vorschau--treffer", teile.length > 0);
    fuellen(
      vorschau,
      teile.length === 0
        ? h("span", {}, eingabe.value.trim().length === 0 ? S.quickadd_beispiel : S.quickadd_hint)
        : [icon("uhr", 13), h("span", {}, teile.join(" · "))],
    );
  }

  async function absenden() {
    const roh = eingabe.value.trim();
    if (roh.length === 0) return;

    const gelesen = parseQuickAdd(roh, Date.now());
    if (gelesen.title.length === 0) return;

    eingabe.value = "";
    vorschauZeichnen();

    await repo.createTask({
      listId: listenwahl.value || state.lists[0]?.id,
      title: gelesen.title,
      day: gelesen.day,
      minutes: gelesen.minutes,
    });
    await aktualisieren();
    eingabe.focus();
  }

  function update() {
    if (!state.bereit) return;

    // Die Listenauswahl nur neu bauen, wenn sich die Listen geändert haben — sonst
    // springt sie beim Tippen auf den ersten Eintrag zurück.
    const kennung = state.lists.map((liste) => liste.id).join(",");
    if (listenwahl.dataset.kennung !== kennung) {
      const gewaehlt = listenwahl.value;
      listenwahl.dataset.kennung = kennung;
      fuellen(
        listenwahl,
        state.lists.map((liste) => h("option", { value: liste.id }, liste.name)),
      );
      listenwahl.value = state.lists.some((liste) => liste.id === gewaehlt)
        ? gewaehlt
        : (state.settings.defaultListId ?? state.lists[0]?.id ?? "");
    }

    if (vorschau.childElementCount === 0 && vorschau.textContent === "") vorschauZeichnen();

    leiste.update();
    fuellen(streifen, petStrip());

    const brett = groupToday(state.tasks, state.now);
    fuellen(
      bloecke,
      brett.isEmpty
        ? leer(brett)
        : [
            stempel(brett),
            block(S.today_overdue, brett.overdue, { dringend: true }),
            block(S.today_today, brett.today),
            block(S.today_later, brett.later),
            block(S.today_done_today, brett.doneToday),
            archiv(brett.doneEarlier),
          ],
    );
  }

  return { el: element, update, focus: () => eingabe.focus() };
}

function block(titel, aufgaben, { dringend = false } = {}) {
  if (aufgaben.length === 0) return null;
  return h(
    "section.abschnitt",
    {},
    // Die gestrichelte Hilfslinie mit Zählmarke — die Bauzeichnungs-Anmutung mit echter
    // Bedeutung: Die Zahl in Klammern ist die Anzahl der Zeilen darunter.
    h(
      "div.abschnitt__kopf.hilfslinie",
      {},
      h(`h2.abschnitt__titel${dringend ? ".abschnitt__titel--dringend" : ""}`, {}, titel),
      h("span.abschnitt__zahl", {}, `(${aufgaben.length})`),
    ),
    h(
      "ul.liste",
      {},
      aufgaben.map((task, index) =>
        taskRow(task, { verzoegerung: Math.min(index, 8) * 22 }),
      ),
    ),
  );
}

/**
 * Der Rückblick am Fuß der Liste.
 *
 * Was gestern geschafft wurde, ist Vergangenheit — es steht da, klein und eingeklappt,
 * aber es füllt die Liste nicht mehr.
 */
function archiv(aufgaben) {
  if (aufgaben.length === 0) return null;

  return h(
    "details.rueckblick",
    {},
    h(
      "summary.rueckblick__kopf",
      {},
      h("span", {}, S.today_done_earlier),
      h("span.abschnitt__zahl", {}, String(aufgaben.length)),
    ),
    h(
      "div.rueckblick__inhalt",
      {},
      aufgaben.map((task) => taskRow(task, { schlicht: true })),
    ),
  );
}

function leer(brett) {
  return h(
    "div.leer.laeuft",
    {},
    h("p.leer__titel", {}, S.today_empty_title),
    h("p", {}, S.today_empty_body),
    h(
      "button.knopf.knopf--still.knopf--klein",
      { onclick: () => reagieren(SpeechCategory.LIST_EMPTY) },
      S.nav_companion,
    ),
  );
}

/**
 * Der Stempel für einen abgeräumten Tag.
 *
 * Nur wenn nichts mehr offen ist **und** heute etwas geschafft wurde. Ohne die zweite
 * Bedingung stempelte eine frisch installierte App den ersten Tag ab, an dem man noch gar
 * nichts eingetragen hat — und der Stempel wäre nichts mehr wert.
 */
function stempel(brett) {
  if (brett.openCount > 0 || brett.doneToday.length === 0) return null;

  const element = h("div.stempel", {}, S.today_all_done);
  stempeln(element);
  return h("div.stempel-platz", {}, element);
}
