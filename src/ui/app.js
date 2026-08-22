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
import { FocusState, focusStateOf, formatRemaining } from "../domain/focus.js";
import * as repo from "../data/repo.js";
import { pruneRewardLog } from "../data/petstore.js";
import { abonnieren, aktualisieren, nachLaden, navigieren, setzen, state, tickern } from "./store.js";
import { fuellen, h, on } from "./dom.js";
import { icon } from "./icons.js";
import { ausHash } from "./router.js";
import { S, STAGE_NAMES } from "./strings.js";
import { formatLongDay } from "./format.js";
import { orb } from "./orb.js";
import { aufteilen } from "./motion.js";
import { todayView } from "./views/today.js";
import { browseView } from "./views/browse.js";
import { focusView } from "./views/focus.js";
import { companionView } from "./views/companion.js";
import { habitsView } from "./views/habits.js";
import { statsView } from "./views/stats.js";
import { settingsView } from "./views/settings.js";
import { moreView } from "./views/more.js";
import { erinnerungenStarten } from "./nag.js";
import { aktionenNachholen, inHuelle, zeitplanSenden } from "./bruecke.js";
import { geteiltesUebernehmen } from "./share.js";
import { willkommenZeigen } from "./onboarding.js";

/**
 * Die Ansichten.
 *
 * `taktet` heißt: Diese Ansicht muss auch dann neu gezeichnet werden, wenn sich nur die
 * Uhr weitergedreht hat — die Restzeit im Fokus, die ablaufende Sperre am Begleiter. Alle
 * anderen werden **nur bei echten Änderungen** gebaut. Ohne diese Unterscheidung fängt
 * jede Einlauf-Animation im Sekundentakt von vorn an.
 */
const ANSICHTEN = {
  today: { bauen: todayView, titel: () => S.nav_today },
  browse: { bauen: browseView, titel: () => S.nav_browse },
  focus: { bauen: focusView, titel: () => S.focus_title, taktet: true },
  companion: { bauen: companionView, titel: () => S.nav_companion, taktet: true },
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
  const kopfTitel = h("h1.display");
  const kopfDatum = h("span.kopf__datum");
  const laufendeRunde = h("div");
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
        laufendeRunde,
        h(
          "button.knopf.knopf--still.knopf--rund",
          { "aria-label": S.action_search, onclick: () => navigieren("browse") },
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

  function zeichnen(_zustand, grund = "daten") {
    const ansicht = ANSICHTEN[state.route] ?? ANSICHTEN.today;
    const nurTakt = grund === "takt";

    // Beim reinen Takt bewegt sich nur, was von der Uhr abhängt.
    if (nurTakt) {
      if (ansicht.taktet) gebaute.get(state.route)?.update();
      laufendeRundeZeichnen(laufendeRunde);
      // Die Seitenleiste zeigt die Restzeit — aber nur, wenn überhaupt etwas läuft.
      if (state.focusSession !== null && state.route !== "focus") seitenleisteZeichnen(seitenleiste);
      return;
    }

    // Die Überschrift läuft zeichenweise ein — aber nur, wenn sie sich geändert hat.
    // Bei jedem Takt neu zu starten wäre Zappeln, keine Bewegung.
    const titel = ansicht.titel();
    if (kopfTitel.dataset.titel !== titel) {
      kopfTitel.dataset.titel = titel;
      aufteilen(kopfTitel, titel);
    }
    kopfDatum.textContent = formatLongDay(state.today);

    if (!gebaute.has(state.route)) gebaute.set(state.route, ansicht.bauen());
    const gebaut = gebaute.get(state.route);

    if (inhalt.firstChild !== gebaut.el) fuellen(inhalt, gebaut.el);
    gebaut.update();

    laufendeRundeZeichnen(laufendeRunde);
    leisteZeichnen(leiste);
    seitenleisteZeichnen(seitenleiste);
    nebenspalteZeichnen(nebenspalte);

    document.body.classList.toggle("ruhig", state.settings.reduceMotion === true);
    fassungAnwenden(state.settings.fassung);
  }

  abonnieren(zeichnen);

  /** Die Adresse ist die Wahrheit über die Ansicht — auch beim allerersten Zeichnen. */
  function ausAdresseUebernehmen() {
    const ziel = ausHash(globalThis.location.hash);
    state.route = ziel.route;
    if (ziel.scope !== null) state.scope = ziel.scope;
  }

  on(globalThis, "hashchange", () => {
    ausAdresseUebernehmen();
    zeichnen(state, "daten");
  });
  ausAdresseUebernehmen();

  await repo.seedIfEmpty(S);

  // Was an einer Meldung angetippt wurde, während die Seite zu war, gilt rückwirkend zum
  // Zeitpunkt des Antippens — deshalb vor dem ersten Laden.
  await aktionenNachholen();

  // Der Wecker der Hülle wird nach **jedem** Laden neu gestellt: Eine abgehakte Aufgabe
  // darf nicht mehr klingeln, eine neue schon.
  if (inHuelle()) nachLaden(zeitplanSenden);

  await aktualisieren({ neuerSatz: true });
  await pruneRewardLog(Date.now());

  // Geteiltes kommt als Adressparameter herein und wird sofort zur Aufgabe.
  if (await geteiltesUebernehmen(state.settings)) await aktualisieren();

  if (state.settings.onboardingDone !== true) {
    willkommenZeigen(() => {
      void aktualisieren();
      gebaute.get("today")?.focus?.();
    });
  }

  erinnerungenStarten();

  // Ein Takt pro Sekunde reicht für die Uhr; alles andere hängt an Ereignissen.
  setInterval(tickern, 1000);

  // Zurück im Tab: Die Zeit ist weitergelaufen, der Begleiter ist verfallen.
  on(document, "visibilitychange", () => {
    if (document.visibilityState === "visible") void aktualisieren();
  });

  tastenkuerzel();
}

/**
 * Hell oder dunkel.
 *
 * Die Wahrheit steht in der Datenbank, aber die wird erst nach dem ersten Zeichnen gelesen.
 * Damit die Seite nicht kurz hell aufblitzt, liegt die Wahl zusätzlich im `localStorage`
 * und wird schon im `index.html` angewandt — der Speicher ist hier ein Zwischenspeicher,
 * keine zweite Wahrheit.
 */
function fassungAnwenden(fassung) {
  const wert = fassung === "dunkel" ? "dunkel" : "hell";
  if (document.documentElement.dataset.fassung === wert) return;

  document.documentElement.dataset.fassung = wert;
  document.querySelector('meta[name="theme-color"]')?.setAttribute(
    "content",
    wert === "dunkel" ? "#0d0c11" : "#edebe4",
  );

  try {
    localStorage.setItem("petodo.fassung", wert);
  } catch {
    /* Privates Fenster: Dann blitzt es beim Laden einmal. Kein Grund für einen Absturz. */
  }
}

/**
 * Eine laufende Fokusrunde, überall sichtbar.
 *
 * Ohne diese Anzeige läuft nach einem Neuladen eine Runde weiter, von der niemand mehr
 * etwas weiß — und man wundert sich, warum die Pause plötzlich anfängt.
 */
function laufendeRundeZeichnen(behaelter) {
  const zustand = focusStateOf(state.focusSession, state.now);
  const laeuft = zustand.state === FocusState.RUNNING || zustand.state === FocusState.PAUSED;

  if (!laeuft || state.route === "focus") {
    fuellen(behaelter);
    // Der Titel des Tabs zählt mit, solange eine Runde läuft.
    document.title = S.app_name;
    return;
  }

  const rest = formatRemaining(zustand.remaining);
  document.title = `${rest} · ${S.app_name}`;

  fuellen(
    behaelter,
    h(
      "button.knopf.knopf--klein",
      { onclick: () => navigieren("focus"), "aria-label": S.focus_title },
      icon(zustand.state === FocusState.PAUSED ? "pause" : "fokus", 14),
      h("span.laufende-runde", {}, rest),
    ),
  );
}

function leisteZeichnen(leiste) {
  fuellen(
    leiste,
    LEISTE.map((punkt) =>
      h(
        "button.leiste__knopf",
        {
          "aria-current": passt(punkt.route) ? "page" : null,
          onclick: () => navigieren(punkt.route),
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
      "div.marke-block",
      {},
      state.pet ? orb(state.pet, { groesse: 38, ring: false, bahn: false }) : null,
      h(
        "div",
        {},
        h("div.marke-block__name", {}, S.app_name),
        h("div.marke-block__satz", {}, S.today_open_count(brett.openCount)),
      ),
    ),
    h(
      "nav.karte.karte--erhoben.navi",
      { "aria-label": S.nav_today },
      SEITE.map((punkt) =>
        h(
          "button.navi__punkt",
          {
            "aria-current": state.route === punkt.route ? "page" : null,
            onclick: () => navigieren(punkt.route),
          },
          icon(punkt.symbol, 18),
          h("span.navi__punkt-name", {}, punkt.text),
          punkt.route === "today" && brett.overdue.length > 0
            ? h("span.navi__zahl", {}, String(brett.overdue.length))
            : null,
          punkt.route === "focus" ? fokusRest() : null,
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

/** Die Restzeit neben „Fokus“ in der Seitenleiste — `null`, wenn nichts läuft. */
function fokusRest() {
  const zustand = focusStateOf(state.focusSession, state.now);
  if (zustand.state !== FocusState.RUNNING && zustand.state !== FocusState.PAUSED) return null;
  return h("span.navi__zahl", {}, formatRemaining(zustand.remaining));
}

function bereichspunkt(eintrag) {
  const aktiv = state.route === "browse" && state.scope.kind === eintrag.kind;
  const anzahl = filterTasks(state.tasks, { kind: eintrag.kind }, "", state.now).length;

  return h(
    "button.navi__punkt",
    {
      "aria-current": aktiv ? "page" : null,
      onclick: () => {
      state.query = "";
      navigieren("browse", { kind: eintrag.kind });
    },
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
      onclick: () => {
        state.query = "";
        navigieren("browse", { kind: Scope.LIST, listId: liste.id });
      },
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
      await aktualisieren();
      navigieren("browse", { kind: Scope.LIST, listId: liste.id });
    },
  });

  return h("div.navi__neu", {}, feld);
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
      "div.karte.karte--erhoben.begleiter",
      {},
      h(
        "div.begleiter__kopfzeile",
        {},
        h("span.begleiter__stufe", {}, STAGE_NAMES[stageOf(state.pet.values)]),
        h("span.begleiter__level", {}, S.pet_level(levelForXp(state.pet.xp))),
      ),
      orb(state.pet, { groesse: 136 }),
      state.speechText ? h("p.blase", {}, state.speechText) : null,
      h(
        "button.knopf.knopf--klein",
        { onclick: () => navigieren("companion") },
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
      navigieren("today");
      gebaute.get("today")?.focus?.();
    } else if (ereignis.key === "/") {
      ereignis.preventDefault();
      navigieren("browse");
    } else if (ereignis.key === "f") {
      ereignis.preventDefault();
      navigieren("focus");
    }
  });
}
