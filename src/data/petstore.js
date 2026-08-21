/**
 * Der gespeicherte Stand des Begleiters.
 *
 * Die Wahrheit ist das **angehängte Belohnungs-Log plus die verstrichene Zeit**; die Zeile
 * in `pet_state` ist nur ein Zwischenstand, der das Nachrechnen von vorne spart. Gerechnet
 * wird ausschließlich in `domain/pet.js` — hier steht kein einziger Wert.
 */

import { Balance } from "../domain/balance.js";
import { uuid } from "../domain/ids.js";
import {
  computePet, initialPetState, isRewardAllowed, isTaskCreationRewardAllowed, lastActionAt,
  overdueLoad, rewardEvent, RewardType,
} from "../domain/pet.js";
import { overdueBurden } from "../domain/tasks.js";
import { dayOf } from "../domain/time.js";
import { getAll, getOne, put, transaction, request } from "./db.js";
import { PET_STATE_ID } from "./schema.js";

function toRow(state) {
  return {
    id: PET_STATE_ID,
    energy: state.values.energy,
    satiety: state.values.satiety,
    mood: state.values.mood,
    xp: state.xp,
    lastComputedAt: state.lastComputedAt,
    lastFedAt: state.lastFedAt,
    lastPlayedAt: state.lastPlayedAt,
    lastPattedAt: state.lastPattedAt,
    updatedAt: state.lastComputedAt,
    deletedAt: null,
  };
}

function fromRow(row) {
  return {
    values: { energy: row.energy, satiety: row.satiety, mood: row.mood },
    xp: row.xp,
    lastComputedAt: row.lastComputedAt,
    lastFedAt: row.lastFedAt ?? null,
    lastPlayedAt: row.lastPlayedAt ?? null,
    lastPattedAt: row.lastPattedAt ?? null,
  };
}

export async function loadPetState(now) {
  const row = await getOne("pet_state", PET_STATE_ID);
  return row ? fromRow(row) : initialPetState(now);
}

/** Alle Ereignisse seit dem letzten Zwischenstand — mehr braucht die Rechnung nicht. */
async function eventsSince(from) {
  const alle = await getAll("reward_events", "at", IDBKeyRange.lowerBound(from, true));
  return alle.filter((row) => row.deletedAt === null || row.deletedAt === undefined);
}

/**
 * Rechnet den Stand auf `now` fort und schreibt ihn zurück.
 *
 * Es tickt nichts: Diese Funktion läuft beim Öffnen der Seite, beim Zurückkehren in den
 * Tab und nach jedem Ereignis — sonst nie.
 */
export async function recomputePet(now, tasks) {
  const previous = await loadPetState(now);
  const events = await eventsSince(previous.lastComputedAt);
  const load = overdueLoad(overdueBurden(tasks, now, await excludedListIds()));

  const state = computePet(previous, events, now, load);
  await put("pet_state", toRow(state));
  return { state, load };
}

async function excludedListIds() {
  const listen = await getAll("task_lists");
  return new Set(listen.filter((liste) => liste.excludeFromNag).map((liste) => liste.id));
}

/**
 * Hängt ein Ereignis an und rechnet neu.
 *
 * Gibt `null` zurück, wenn die Handlung gesperrt ist — die Sperre ist der Grund, warum
 * Füttern etwas wert ist. Ohne sie tippt man sich aus jeder Krankheit heraus.
 */
export async function award(type, now, tasks, refId = null) {
  const previous = await loadPetState(now);
  if (!isRewardAllowed(type, lastActionAt(previous, type), now)) return null;

  if (type === RewardType.TASK_CREATED && !isTaskCreationRewardAllowed(await createdRewardsToday(now))) {
    return recomputePet(now, tasks);
  }

  const event = rewardEvent(uuid(), now, type, refId);
  await put("reward_events", { ...event, updatedAt: now, deletedAt: null });
  return recomputePet(now, tasks);
}

async function createdRewardsToday(now) {
  const heute = dayOf(now);
  const alle = await getAll("reward_events", "type", IDBKeyRange.only(RewardType.TASK_CREATED));
  return alle.filter((row) => dayOf(row.at) === heute).length;
}

/** Wie viele Fokusrunden heute fertig wurden — für die lange Pause und den Rückblick. */
export async function focusRoundsToday(now) {
  const heute = dayOf(now);
  const alle = await getAll("reward_events", "type", IDBKeyRange.only(RewardType.FOCUS_DONE));
  return alle.filter((row) => dayOf(row.at) === heute).length;
}

/**
 * Räumt uralte Ereignisse weg.
 *
 * Das Log wächst sonst ohne Grenze. Alles vor dem letzten Zwischenstand ist bereits
 * eingerechnet und wird nicht mehr gebraucht — nur die jüngere Vergangenheit bleibt für
 * den Rückblick stehen.
 */
export async function pruneRewardLog(now) {
  const grenze = now - Balance.HABIT_HISTORY_DAYS * 86_400_000;
  return transaction(["reward_events"], "readwrite", async (tx) => {
    const store = tx.objectStore("reward_events");
    const alte = await request(store.index("at").getAllKeys(IDBKeyRange.upperBound(grenze)));
    for (const key of alte) store.delete(key);
    return alte.length;
  });
}
