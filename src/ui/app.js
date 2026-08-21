/**
 * Das Gerüst: Kopf, Navigation, Ansichtswechsel.
 *
 * Eine Ansicht wird **einmal** gebaut und danach nur noch mit `update()` aufgefrischt.
 * Würde bei jeder Änderung alles neu gezeichnet, verlöre jedes Eingabefeld den Fokus und
 * jede halb getippte Zeile ihren Inhalt — der häufigste Fehler in selbstgebauten
 * Oberflächen ohne Rahmenwerk.
 */

import { Scope, filterTasks } from "../domain/filter.js";
import { groupToday } from "../domain/tasks.js";
import { levelForXp, stageOf } from "../domain/pet.js";
import * as repo from "../data/repo.js";
import { pruneRewardLog } from "../data/petstore.js";
import { abonnieren, aktualisieren, setzen, state, tickern } from "./store.js";
import { fuellen, h, on } from "./dom.js";
import { icon } from "./icons.js";
import { S, STAGE_NAMES } from "./strings.js";
import { formatLongDay } from "./format.js";
import { orb } from "./orb.js";
import { todayView } from "./views/today.js";
import { browseView } from "./views/browse.js";
import { focusView } from "./views/focus.js";
import { companionView } from "./views/companion.js";
import { habitsView } from "./views/habits.js";
import { statsView } from "./views/stats.js";
import { settingsView } from "./views/settings.js";
import { moreView } from "./views/more.js";

const ANSICHTEN = {
  today: { bauen: todayView, titel: () => S.nav_today },
  browse: { bauen: browseView, titel: () => S.nav_browse },
  focus: { bauen: focusView, titel: () => S.focus_title },
  companion: { bauen: companionView, titel: () => S.nav_companion },
  habits: { bauen: habitsView, titel: () => S.habits_title },
  stats: { bauen: statsView, titel: () => S.stats_title },
  settings: { bauen: settingsView, titel: () => S.settings_title },
  more: { bauen: moreView, titel: () => S.nav_more },
};

/** Die Leiste auf dem Telefon. Fünf Punkte — mehr trifft niemand mit dem Daumen. */
const LEISTE = [
  { route: "today", text: S.nav_today, symbol: "heute" },
  { route: "browse", text: S.nav_browse, symbol: "listen" },
  { route: "focus", text: S.nav_focus, symbol: "fokus" },
  { route: "habits", text: S.nav_habits, symbol: "gewohnheiten" },
  { route: "more", text: S.nav_more, symbol: "mehr" },
];

/** Die Seitenleiste am Schreibtisch — dieselben Ziele, nur alle gleichzeitig sichtbar. */
const SEITE = [
  { route: "today", text: S.nav_today, symbol: "heute" },
  { route: "focus", text: S.focus_title, symbol: "fokus" },
  { route: "habits", text: S.nav_habits, symbol: "gewohnheiten" },
  { route: "stats", text: S.nav_stats, symbol: "rueckblick" },
  { route: "settings", text: S.nav_settings, symbol: "einstellungen" },
];

const SCHLAUE_LISTEN = [
  { kind: Scope.ALL_OPEN, text: S.scope_all_open },
  { kind: Scope.NEXT_SEVEN_DAYS, text: S.scope_next_seven },
  { kind: Scope.COMPLETED, text: S.scope_completed },
];

const gebaute = new Map();

export async function starten(wurzel) {
  const kopfTitel = h("h1");
  const kopfDatum = h("span.kopf__datum");
  const kopf = h(
    "header.kopf",
    {},
    h(
      "div.kopf__zeile",
      {},
      h("div.kopf__titel", {}, kopfTitel, kopfDatum),
      h(
        "div.kopf__werkzeuge",
        {},
        h(
          "button.knopf.knopf--still.knopf--rund",
          { "aria-label": S.action_search, onclick: () => setzen({ route: "browse" }) },
          icon("suchen", 18),
        ),
      ),
    ),
  );

  const inhalt = h("main.inhalt", { id: "inhalt" });
  const leiste = h("nav.leiste", { "aria-label": S.nav_today });
  const seitenleiste = h("aside.seitenleiste");
  const nebenspalte = h("aside.nebenspalte");

  fuellen(wurzel, h("div.rahmen", {}, seitenleiste, kopf, inhalt, leiste, nebenspalte));

  function zeichnen() {
    const ansicht = ANSICHTEN[state.route] ?? ANSICHTEN.today;

    kopfTitel.textContent = ansicht.titel();
    kopfDatum.textContent = formatLongDay(state.today);

    if (!gebaute.has(state.route)) gebaute.set(state.route, ansicht.bauen());
    const gebaut = gebaute.get(state.route);

    if (inhalt.firstChild !== gebaut.el) fuellen(inhalt, gebaut.el);
    gebaut.update();

    leisteZeichnen(leiste);
    seitenleisteZeichnen(seitenleiste);
    nebenspalteZeichnen(nebenspalte);

    document.body.classList.toggle("ruhig", state.settings.reduceMotion === true);
  }

  abonnieren(zeichnen);

  await repo.seedIfEmpty(S);
  await aktualisieren({ neuerSatz: true });
  await pruneRewardLog(Date.now());

  // Ein Takt pro Sekunde reicht für die Uhr; alles andere hängt an Ereignissen.
  setInterval(tickern, 1000);

  // Zurück im Tab: Die Zeit ist weitergelaufen, der Begleiter ist verfallen.
  on(document, "visibilitychange", () => {
    if (document.visibilityState === "visible") void aktualisieren();
  });

  tastenkuerzel();
}

function leisteZeichnen(leiste) {
  fuellen(
    leiste,
    LEISTE.map((punkt) =>
      h(
        "button.leiste__knopf",
        {
          "aria-current": passt(punkt.route) ? "page" : null,
          onclick: () => setzen({ route: punkt.route }),
        },
        icon(punkt.symbol, 22),
        h("span", {}, punkt.text),
      ),
    ),
  );
}

/** „Mehr“ bleibt hervorgehoben, solange man in einer seiner Unteransichten steht. */
function passt(route) {
  if (route === "more") return ["more", "companion", "stats", "settings"].includes(state.route);
  return state.route === route;
}

function seitenleisteZeichnen(seitenleiste) {
  const brett = groupToday(state.tasks, state.now);

  fuellen(
    seitenleiste,
    h(
      "div.karte.marke",
      {},
      state.pet ? orb(state.pet, { groesse: 40, ring: false }) : null,
      h(
        "div",
        {},
        h("div.marke__name", {}, S.app_name),
        h("div.marke__satz", {}, S.today_open_count(brett.openCount)),
      ),
    ),
    h(
      "nav.karte.navi",
      { "aria-label": S.nav_today },
      SEITE.map((punkt) =>
        h(
          "button.navi__punkt",
          {
            "aria-current": state.route === punkt.route ? "page" : null,
            onclick: () => setzen({ route: punkt.route }),
          },
          icon(punkt.symbol, 18),
          h("span.navi__punkt-name", {}, punkt.text),
          punkt.route === "today" && brett.overdue.length > 0
            ? h("span.navi__zahl", {}, String(brett.overdue.length))
            : null,
        ),
      ),
    ),
    h(
      "nav.karte.navi",
      { "aria-label": S.lists_title },
      h("div.navi__gruppe", {}, S.lists_smart),
      SCHLAUE_LISTEN.map((eintrag) => bereichspunkt(eintrag)),
      h("div.navi__gruppe", {}, S.lists_own),
      state.lists.map((liste) => listenpunkt(liste)),
      neueListe(),
    ),
  );
}

function bereichspunkt(eintrag) {
  const aktiv = state.route === "browse" && state.scope.kind === eintrag.kind;
  const anzahl = filterTasks(state.tasks, { kind: eintrag.kind }, "", state.now).length;

  return h(
    "button.navi__punkt",
    {
      "aria-current": aktiv ? "page" : null,
      onclick: () => setzen({ route: "browse", scope: { kind: eintrag.kind }, query: "" }),
    },
    h("span.navi__punkt-name", {}, eintrag.text),
    h("span.navi__zahl", {}, String(anzahl)),
  );
}

function listenpunkt(liste) {
  const aktiv = state.route === "browse" && state.scope.listId === liste.id;
  const anzahl = state.tasks.filter(
    (task) => task.listId === liste.id && !task.completedAt && !task.deletedAt && !task.parentId,
  ).length;

  return h(
    "button.navi__punkt",
    {
      "aria-current": aktiv ? "page" : null,
      onclick: () =>
        setzen({ route: "browse", scope: { kind: Scope.LIST, listId: liste.id }, query: "" }),
    },
    h("span.navi__punkt-farbe"),
    h("span.navi__punkt-name", {}, liste.name),
    h("span.navi__zahl", {}, String(anzahl)),
  );
}

function neueListe() {
  const feld = h("input.eingabe", {
    type: "text",
    placeholder: S.list_new_placeholder,
    onkeydown: async (ereignis) => {
      if (ereignis.key !== "Enter") return;
      const name = feld.value.trim();
      if (name.length === 0) return;
      feld.value = "";
      const liste = await repo.createList(name);
      setzen({ route: "browse", scope: { kind: Scope.LIST, listId: liste.id } });
      await aktualisieren();
    },
  });

  return h("div", { style: { padding: "8px" } }, feld);
}

function nebenspalteZeichnen(nebenspalte) {
  if (state.pet === null) {
    fuellen(nebenspalte);
    return;
  }

  const brett = groupToday(state.tasks, state.now);

  fuellen(
    nebenspalte,
    h(
      "div.karte.begleiter",
      {},
      h(
        "div.begleiter__kopfzeile",
        {},
        h("span.begleiter__stufe", {}, STAGE_NAMES[stageOf(state.pet.values)]),
        h("span.begleiter__level", {}, S.pet_level(levelForXp(state.pet.xp))),
      ),
      orb(state.pet, { groesse: 132 }),
      state.speechText ? h("p.blase", {}, state.speechText) : null,
      h(
        "button.knopf.knopf--klein",
        { onclick: () => setzen({ route: "companion" }) },
        S.nav_companion,
      ),
    ),
    h(
      "div.karte.kachel",
      {},
      h("span.kachel__zahl", {}, String(brett.doneToday.length)),
      h("span.kachel__name", {}, S.today_done_today),
    ),
  );
}

/**
 * Tastenkürzel.
 *
 * Nur drei, und keins, das in einem Eingabefeld zuschnappt: Wer „n“ in eine Notiz tippt,
 * will kein neues Fenster.
 */
function tastenkuerzel() {
  on(document, "keydown", (ereignis) => {
    const imFeld = ["INPUT", "TEXTAREA", "SELECT"].includes(document.activeElement?.tagName);
    if (imFeld || ereignis.metaKey || ereignis.ctrlKey || ereignis.altKey) return;

    if (ereignis.key === "n") {
      ereignis.preventDefault();
      setzen({ route: "today" });
      gebaute.get("today")?.focus?.();
    } else if (ereignis.key === "/") {
      ereignis.preventDefault();
      setzen({ route: "browse" });
    } else if (ereignis.key === "f") {
      ereignis.preventDefault();
      setzen({ route: "focus" });
    }
  });
}
