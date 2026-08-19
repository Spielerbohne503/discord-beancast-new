/**
 * Der Begleiter: Werte, Krankheitsstufen, Belohnungen, Level, Sprechblasen.
 *
 * Zwei Regeln stehen über allem:
 *
 * 1. **Genau eine Funktion berechnet den Wertestand** — [computePet]. Wer an einer
 *    zweiten Stelle nachrechnet, hat den Fehler eingebaut, den diese Regel verhindert.
 * 2. **Kein Tod, kein Punkt ohne Wiederkehr.** Unter „elend“ gibt es nichts.
 *
 * Zeitpunkte sind Millisekunden seit 1970. Die Uhr wird nie hier gelesen, sondern
 * hereingereicht.
 */

import { Balance, Priority } from "./balance.js";

const MS_PER_MINUTE = 60_000;
const MS_PER_HOUR = 3_600_000;

// --------------------------------------------------------------------------- Werte

const clamp = (value, low, high) => Math.min(high, Math.max(low, value));

/**
 * Die drei Bedürfniswerte, immer zwischen 0 und 100.
 *
 * Gekappt wird **hier**, nicht an den Aufrufstellen: So kann kein Weg durch den Code
 * einen Wert von 137 oder −12 erzeugen, auch kein künftiger.
 */
export function petValues(energy, satiety, mood) {
  return Object.freeze({
    energy: clamp(Number(energy) || 0, Balance.VALUE_MIN, Balance.VALUE_MAX),
    satiety: clamp(Number(satiety) || 0, Balance.VALUE_MIN, Balance.VALUE_MAX),
    mood: clamp(Number(mood) || 0, Balance.VALUE_MIN, Balance.VALUE_MAX),
  });
}

/** Ein frisch geschlüpfter Begleiter startet satt und zufrieden. */
export const INITIAL_VALUES = petValues(Balance.VALUE_MAX, Balance.VALUE_MAX, Balance.VALUE_MAX);

/** Der Durchschnitt der drei Werte entscheidet über die Krankheitsstufe. */
export function averageOf(values) {
  return (values.energy + values.satiety + values.mood) / 3;
}

export function plusValues(values, dEnergy, dSatiety, dMood) {
  return petValues(values.energy + dEnergy, values.satiety + dSatiety, values.mood + dMood);
}

// ------------------------------------------------------------------ Krankheitsstufen

/**
 * Die vier Stufen, von oben nach unten.
 *
 * Die unterste ist ein teilnahmsloser Begleiter — eine Leiche im Startbildschirm ist kein
 * Ansporn, sondern ein Deinstallationsgrund.
 */
export const HealthStage = Object.freeze({
  HEALTHY: "healthy",
  WEAKENED: "weakened",
  SICK: "sick",
  MISERABLE: "miserable",
});

const STAGE_ORDER = [HealthStage.MISERABLE, HealthStage.SICK, HealthStage.WEAKENED, HealthStage.HEALTHY];

/** Für Vergleiche „um mindestens eine Stufe besser“. */
export function stageRank(stage) {
  return STAGE_ORDER.indexOf(stage);
}

export function stageOfAverage(average) {
  if (average > Balance.STAGE_HEALTHY_ABOVE) return HealthStage.HEALTHY;
  if (average >= Balance.STAGE_WEAKENED_ABOVE) return HealthStage.WEAKENED;
  if (average >= Balance.STAGE_SICK_ABOVE) return HealthStage.SICK;
  return HealthStage.MISERABLE;
}

export function stageOf(values) {
  return stageOfAverage(averageOf(values));
}

// -------------------------------------------------------------- Überfälligkeitslast

/**
 * ```
 * L = Σ über offene, überfällige Aufgaben:
 *       prioFaktor (0,5 · 1,0 · 1,5 · 2,0)
 *     + 0,2 × angefangene Überfälligkeitswochen
 * ```
 *
 * Gedeckelt bei 10. **Aufgaben ohne Fälligkeit zählen nie** — Krankheit hängt an
 * überfälligen, nie an offenen Aufgaben. Sonst wird Erfassen bestraft, und wer nichts
 * mehr einträgt, benutzt die App nicht mehr.
 */
export function overdueLoad(overdueTasks) {
  const sum = overdueTasks.reduce((total, task) => total + loadContribution(task), 0);
  return clamp(sum, 0, Balance.LOAD_CAP);
}

/** Beitrag einer einzelnen Aufgabe — ohne Deckelung. */
export function loadContribution({ priority, overdueDays }) {
  const factor = Balance.LOAD_PRIORITY_FACTOR[Priority.coerce(priority)];
  return factor + Balance.LOAD_PER_STARTED_WEEK * startedOverdueWeeks(overdueDays);
}

/** Angefangene Wochen: Ein Tag überfällig ist bereits die erste Woche. */
export function startedOverdueWeeks(overdueDays) {
  if (!(overdueDays > 0)) return 0;
  return Math.ceil(overdueDays / 7);
}

/** mBasis = 1 + (L / 10) × 2 → 1,0 bis 3,0 */
export function baseMultiplier(load) {
  return 1 + (clamp(load, 0, Balance.LOAD_CAP) / Balance.LOAD_CAP) * Balance.MULTIPLIER_LOAD_SPAN;
}

/**
 * mLaune = mBasis × (1 + 3 × (1 − (energie + sättigung) / 200))
 *
 * Die Laune hängt an den beiden anderen Werten: Versäumnisse verstärken sich gegenseitig,
 * statt dass jeder Wert für sich vor sich hin driftet.
 */
export function moodMultiplier(load, energy, satiety) {
  const wellFed = clamp((energy + satiety) / (2 * Balance.VALUE_MAX), 0, 1);
  return baseMultiplier(load) * (1 + Balance.MOOD_COUPLING * (1 - wellFed));
}

// ------------------------------------------------------------------- Belohnungen

/** Die Anlässe. Die Zahlen dazu stehen in `Balance.REWARD`. */
export const RewardType = Object.freeze(
  Object.fromEntries(Object.keys(Balance.REWARD).map((name) => [name, name])),
);

export function rewardOf(type) {
  return Balance.REWARD[type] ?? null;
}

/** Ein Eintrag für das Append-only-Log. */
export function rewardEvent(id, at, type, refId = null) {
  const reward = rewardOf(type);
  if (!reward) throw new Error(`Unbekannte Belohnung: ${type}`);
  return Object.freeze({
    id,
    at,
    type,
    dEnergy: reward.energy,
    dSatiety: reward.satiety,
    dMood: reward.mood,
    dXp: reward.xp,
    refId,
  });
}

/**
 * Ob eine Handlung gerade erlaubt ist.
 *
 * Ohne Sperrzeiten tippt man sich aus jeder Krankheit heraus, und die Kopplung an die
 * Arbeit ist wertlos. Der Knopf verschwindet dabei nie — er sagt, wie lange noch.
 */
export function isRewardAllowed(type, lastAt, now) {
  return remainingCooldownMs(type, lastAt, now) <= 0;
}

/** Wie lange die Sperre noch läuft, in Millisekunden. */
export function remainingCooldownMs(type, lastAt, now) {
  const minutes = rewardOf(type)?.cooldownMinutes ?? null;
  if (minutes === null || lastAt === null || lastAt === undefined) return 0;
  return Math.max(0, lastAt + minutes * MS_PER_MINUTE - now);
}

/** Höchstens zehn Erfassungs-XP pro Tag — sonst legt man Aufgaben an, statt sie zu tun. */
export function isTaskCreationRewardAllowed(alreadyToday) {
  return alreadyToday < Balance.REWARD_TASK_CREATED_MAX_PER_DAY;
}

// --------------------------------------------------------------------- Simulation

/** Der zuletzt gespeicherte Zwischenstand. */
export function initialPetState(at) {
  return Object.freeze({
    values: INITIAL_VALUES,
    xp: 0,
    lastComputedAt: at,
    lastFedAt: null,
    lastPlayedAt: null,
    lastPattedAt: null,
  });
}

export function lastActionAt(state, type) {
  if (type === RewardType.FEED) return state.lastFedAt;
  if (type === RewardType.PLAY) return state.lastPlayedAt;
  if (type === RewardType.PAT) return state.lastPattedAt;
  return null;
}

/** Verstrichene Stunden, gedeckelt und nie negativ. */
export function elapsedHours(from, to) {
  const hours = (to - from) / MS_PER_HOUR;
  if (!(hours > 0)) return 0;
  return Math.min(hours, Balance.DECAY_MAX_ELAPSED_HOURS);
}

/**
 * Verfall über die verstrichene Zeit.
 *
 * ```
 * neuerWert = alterWert − (100 / basisStunden) × verstricheneStunden × multiplikator
 * ```
 *
 * Die verstrichene Zeit wird auf 24 Stunden gedeckelt: Nach zwei Wochen Urlaub ist der
 * Begleiter nicht schlechter dran als nach einem Tag. Ohne diesen Deckel kommt man von
 * einer Reise zurück, findet ein elendes Pet vor und löscht die App.
 */
export function decay(values, from, to, load) {
  const hours = elapsedHours(from, to);
  if (hours <= 0) return values;

  const base = baseMultiplier(load);
  const mood = moodMultiplier(load, values.energy, values.satiety);
  const rate = (baseHours) => Balance.VALUE_MAX / baseHours;

  return petValues(
    values.energy - rate(Balance.DECAY_HOURS_ENERGY) * hours * base,
    values.satiety - rate(Balance.DECAY_HOURS_SATIETY) * hours * base,
    values.mood - rate(Balance.DECAY_HOURS_MOOD) * hours * mood,
  );
}

/**
 * **Die eine Funktion, die den Wertestand berechnet.**
 *
 * Es tickt nichts: Beim Öffnen der App und bei jedem Ereignis wird einmal gerechnet —
 * erst der Verfall über die verstrichene Zeit, dann die Ereignisse in Zeitfolge.
 *
 * @param previous der zuletzt gespeicherte Zwischenstand
 * @param events Ereignisse seit `previous.lastComputedAt`, Reihenfolge egal
 * @param now Zeitpunkt, für den gerechnet wird
 * @param load die Überfälligkeitslast
 */
export function computePet(previous, events, now, load) {
  let values = decay(previous.values, previous.lastComputedAt, now, load);
  let xp = previous.xp;
  let lastFedAt = previous.lastFedAt;
  let lastPlayedAt = previous.lastPlayedAt;
  let lastPattedAt = previous.lastPattedAt;

  for (const event of [...events].sort((a, b) => a.at - b.at)) {
    values = plusValues(values, event.dEnergy, event.dSatiety, event.dMood);
    xp += event.dXp;
    if (event.type === RewardType.FEED) lastFedAt = event.at;
    else if (event.type === RewardType.PLAY) lastPlayedAt = event.at;
    else if (event.type === RewardType.PAT) lastPattedAt = event.at;
  }

  return Object.freeze({
    values,
    xp: Math.max(0, xp),
    lastComputedAt: now,
    lastFedAt,
    lastPlayedAt,
    lastPattedAt,
  });
}

// -------------------------------------------------------------------------- Level

/** Was die Stufe von [level] auf [level] + 1 kostet. */
export function levelCost(level) {
  let cost = Balance.LEVEL_FIRST_STEP_XP;
  for (let step = 1; step < Math.max(1, level); step++) cost *= Balance.LEVEL_GROWTH;
  return Math.round(cost);
}

/** Gesamte XP, die man für [level] gebraucht hat. */
export function totalXpFor(level) {
  let total = 0;
  for (let step = 1; step < Math.max(1, level); step++) total += levelCost(step);
  return total;
}

/** Das Level zu einem XP-Stand. Level 1 ab 0 XP. */
export function levelForXp(xp) {
  let level = 1;
  let remaining = Math.max(0, xp);
  while (level < Balance.LEVEL_MAX) {
    const cost = levelCost(level);
    if (remaining < cost) break;
    remaining -= cost;
    level++;
  }
  return level;
}

/** Fortschritt innerhalb des aktuellen Levels, 0 bis 1. */
export function levelProgress(xp) {
  const level = levelForXp(xp);
  const into = Math.max(0, xp - totalXpFor(level));
  return clamp(into / levelCost(level), 0, 1);
}

export function xpToNextLevel(xp) {
  return Math.max(0, totalXpFor(levelForXp(xp)) + levelCost(levelForXp(xp)) - xp);
}

// -------------------------------------------------------------------- Sprechblasen

/**
 * Die Kategorien. `domain/` kennt keine Texte — es entscheidet nur, **welche** Kategorie
 * dran ist; die Texte liegen in `ui/strings.js`.
 */
export const SpeechCategory = Object.freeze({
  START: "start",
  FOCUS_BEGINS: "focus_begins",
  FOCUS_ENDS: "focus_ends",
  BREAK_OVER: "break_over",
  TASK_DONE: "task_done",
  LIST_EMPTY: "list_empty",
  TASK_DUE: "task_due",
  FED: "fed",
  PLAYED: "played",
  PATTED: "patted",
  HUNGRY: "hungry",
  TIRED: "tired",
  SICK: "sick",
  HAPPY: "happy",
  BORED: "bored",
});

/**
 * Welche getaktete Bemerkung gerade passt.
 *
 * **Bedürfnisse gehen Stimmungen vor**: Ein hungriger Begleiter erzählt nicht, wie gut
 * gelaunt er ist. Reihenfolge: hungrig → müde → krank → gut gelaunt → gelangweilt.
 */
export function moodCategory(values) {
  if (values.satiety < Balance.BUBBLE_HUNGRY_BELOW) return SpeechCategory.HUNGRY;
  if (values.energy < Balance.BUBBLE_TIRED_BELOW) return SpeechCategory.TIRED;

  const stage = stageOf(values);
  if (stage === HealthStage.SICK || stage === HealthStage.MISERABLE) return SpeechCategory.SICK;
  if (values.mood > Balance.BUBBLE_HAPPY_ABOVE) return SpeechCategory.HAPPY;
  return SpeechCategory.BORED;
}

/**
 * Wählt einen Text aus einer Kategorie.
 *
 * Derselbe Gedanke wiederholt sich unmittelbar nur mit 35 % Wahrscheinlichkeit — sonst
 * klingt der Begleiter wie eine kaputte Schallplatte. Eine leere Kategorie heißt: Er sagt
 * dazu nichts.
 *
 * `roll` und `chooser` kommen von außen, damit die Wahl im Test bestimmbar ist.
 */
export function pickSpeech(texts, previous, roll, chooser) {
  if (texts.length === 0) return null;
  if (texts.length === 1) return texts[0];

  const candidate = texts[clamp(chooser(texts.length), 0, texts.length - 1)];
  if (candidate !== previous) return candidate;
  if (roll < Balance.BUBBLE_REPEAT_CHANCE) return candidate;

  const alternatives = texts.filter((text) => text !== previous);
  // Stehen in einer Kategorie nur gleiche Texte, gibt es nichts zu wechseln — dann lieber
  // wiederholen, als aus einer leeren Liste zu würfeln.
  if (alternatives.length === 0) return candidate;

  return alternatives[clamp(chooser(alternatives.length), 0, alternatives.length - 1)];
}
