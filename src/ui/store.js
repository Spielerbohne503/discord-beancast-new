/**
 * Der Zustand der Oberfläche und der Weg, ihn zu ändern.
 *
 * Ein Ort, an dem alles steht, und **eine** Art, es zu ändern: `aktualisieren()` lädt aus
 * der Datenbank neu und meldet allen Ansichten Bescheid. Kein Ausrechnen an zwei Stellen,
 * kein Zustand in einer Ansicht, den eine andere nicht kennt.
 */

import { Balance } from "../domain/balance.js";
import { Scope, scope } from "../domain/filter.js";
import { moodCategory, pickSpeech } from "../domain/pet.js";
import { dayOf } from "../domain/time.js";
import * as repo from "../data/repo.js";
import { focusRoundsToday, recomputePet } from "../data/petstore.js";
import { hashFuer } from "./router.js";
import { S } from "./strings.js";

const hoerer = new Set();

export const state = {
  route: "today",
  scope: scope(Scope.ALL_OPEN),
  query: "",
  now: Date.now(),
  today: dayOf(Date.now()),

  lists: [],
  tasks: [],
  tags: [],
  habits: [],
  checkins: new Map(),
  focusSession: null,
  focusRounds: 0,
  settings: { ...repo.SETTING_DEFAULTS },

  pet: null,
  load: 0,

  speechCategory: null,
  speechText: null,

  bereit: false,
};

export function abonnieren(rueckruf) {
  hoerer.add(rueckruf);
  return () => hoerer.delete(rueckruf);
}

function melden() {
  for (const rueckruf of hoerer) rueckruf(state);
}

/** Lädt alles neu und rechnet den Begleiter fort. */
export async function aktualisieren({ neuerSatz = false } = {}) {
  state.now = Date.now();
  state.today = dayOf(state.now);

  state.settings = await repo.loadSettings();
  state.lists = await repo.loadLists();
  state.tasks = await repo.loadTasks();
  state.tags = await repo.loadTags();
  state.habits = await repo.loadHabits();
  state.checkins = await repo.loadCheckins(state.today);
  state.focusSession = await repo.loadCurrentFocus();
  state.focusRounds = await focusRoundsToday(state.now);

  const { state: pet, load } = await recomputePet(state.now, state.tasks);
  state.pet = pet;
  state.load = load;

  if (neuerSatz || state.speechText === null) satzWaehlen();

  state.bereit = true;
  melden();
}

/** Nur die Uhr weiterstellen — für den Sekundentakt im Fokus, ohne Datenbankzugriff. */
export function tickern() {
  state.now = Date.now();
  const heute = dayOf(state.now);
  if (heute !== state.today) {
    // Mitternacht: Was heute geschafft wurde, ist ab jetzt „früher erledigt“.
    state.today = heute;
    void aktualisieren();
    return;
  }
  melden();
}

export function setzen(aenderungen) {
  Object.assign(state, aenderungen);
  melden();
}

/**
 * Ansicht wechseln — über die Adresse, nicht am Zustand vorbei.
 *
 * Der Zustand ändert sich erst, wenn der Browser den Wechsel meldet. Damit stimmen
 * Adresse, Verlauf und Bildschirm immer überein, und der Zurück-Knopf tut, was er soll.
 */
export function navigieren(route, scope = null) {
  const ziel = hashFuer(route, scope ?? (route === "browse" ? state.scope : null));
  if (globalThis.location.hash === ziel) {
    // Derselbe Ort: Es kommt keine Meldung vom Browser, also selbst zeichnen.
    melden();
    return;
  }
  globalThis.location.hash = ziel;
}

/**
 * Wählt einen Satz für die Sprechblase.
 *
 * `domain/` entscheidet die Kategorie, hier wird der Text gezogen. Dass der Zufall von
 * außen kommt, ist der Grund, warum die Auswahl prüfbar bleibt.
 */
export function satzWaehlen(kategorie = null) {
  if (state.pet === null) return;

  const gewaehlt = kategorie ?? moodCategory(state.pet.values);
  const texte = S.speech[gewaehlt] ?? [];
  state.speechCategory = gewaehlt;
  state.speechText = pickSpeech(texte, state.speechText, Math.random(), (n) =>
    Math.floor(Math.random() * n),
  );
}

/** Eine Reaktion zeigen und danach wieder auf die Grundstimmung zurückfallen. */
let rueckfall = null;

export function reagieren(kategorie) {
  satzWaehlen(kategorie);
  melden();

  clearTimeout(rueckfall);
  rueckfall = setTimeout(() => {
    satzWaehlen();
    melden();
  }, 6000);
}

export const ARCHIV_TAGE = Balance.ARCHIVE_DAYS;
