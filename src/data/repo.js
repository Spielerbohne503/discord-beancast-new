/**
 * Die Ablage: Listen, Aufgaben, Etiketten, Gewohnheiten, Fokus, Einstellungen.
 *
 * Hier steht, **wann** etwas geschrieben wird — nie, **was** fachlich gilt. Jede Regel
 * kommt aus `domain/`. Zwei Dinge macht diese Schicht immer selbst:
 *
 * - Sie liest die Uhr (`domain/` darf das nicht) und reicht `now` hinein.
 * - Sie setzt `updatedAt` bei jeder Änderung und `deletedAt` statt zu löschen.
 */

import { Balance, Priority } from "../domain/balance.js";
import { FractionalIndex, keyForMove, uuid } from "../domain/ids.js";
import { RewardType } from "../domain/pet.js";
import { advance, parseRule } from "../domain/recurrence.js";
import { anchorMinutes } from "../domain/nag.js";
import { isCompleted, isDeleted, isOpen, isOverdue, makeTask } from "../domain/tasks.js";
import { atTime, dayOf, minutesOfDay, startOfDay } from "../domain/time.js";
import { getAll, getOne, put, putAll, transaction, request } from "./db.js";
import { SEED_LISTS } from "./schema.js";
import { award } from "./petstore.js";

export const now = () => Date.now();

const lebend = (rows) => rows.filter((row) => row.deletedAt === null || row.deletedAt === undefined);

// ------------------------------------------------------------------------- Listen

export async function loadLists() {
  const rows = lebend(await getAll("task_lists"));
  return rows.sort((a, b) => (a.sortKey < b.sortKey ? -1 : a.sortKey > b.sortKey ? 1 : 0));
}

export async function createList(name, excludeFromNag = false, at = now()) {
  const listen = await loadLists();
  const liste = {
    id: uuid(),
    name: name.trim(),
    color: null,
    excludeFromNag,
    sortKey: FractionalIndex.afterOrInitial(listen.at(-1)?.sortKey ?? null),
    createdAt: at,
    updatedAt: at,
    deletedAt: null,
  };
  await put("task_lists", liste);
  return liste;
}

export async function updateList(id, patch, at = now()) {
  const liste = await getOne("task_lists", id);
  if (!liste) return null;
  const neu = { ...liste, ...patch, updatedAt: at };
  await put("task_lists", neu);
  return neu;
}

/**
 * Eine Liste löschen nimmt ihre Aufgaben mit — als Tombstones, nicht als Loch.
 *
 * Die letzte Liste bleibt stehen: Ohne eine einzige Liste gibt es keinen Ort für eine
 * neue Aufgabe, und die Schnell-Eingabe hätte kein Ziel mehr.
 */
export async function deleteList(id, at = now()) {
  const listen = await loadLists();
  if (listen.length <= 1) return false;

  const aufgaben = (await getAll("tasks", "listId", IDBKeyRange.only(id))).filter(
    (task) => !isDeleted(task),
  );
  await putAll("tasks", aufgaben.map((task) => ({ ...task, deletedAt: at, updatedAt: at })));
  await updateList(id, { deletedAt: at }, at);
  return true;
}

// ----------------------------------------------------------------------- Aufgaben

export async function loadTasks() {
  return lebend(await getAll("tasks"));
}

export async function loadTask(id) {
  return getOne("tasks", id);
}

export async function loadSubtasks(parentId) {
  return lebend(await getAll("tasks", "parentId", IDBKeyRange.only(parentId)));
}

/**
 * Legt eine Aufgabe an.
 *
 * `day` und `minutes` kommen aus der Schnell-Eingabe. Eine Aufgabe **ohne** Uhrzeit
 * bekommt einen Zeitpunkt am Tagesbeginn und `hasTime = false` — sie wird damit erst
 * überfällig, wenn der Tag vorbei ist.
 */
export async function createTask(fields, at = now()) {
  const geschwister = (await loadTasks()).filter(
    (task) => task.listId === fields.listId && (task.parentId ?? null) === (fields.parentId ?? null),
  );
  const letzter = geschwister.map((task) => task.sortKey).sort().at(-1) ?? null;

  const hatUhrzeit = fields.minutes !== null && fields.minutes !== undefined;
  const dueAt =
    fields.day === null || fields.day === undefined
      ? null
      : hatUhrzeit
        ? atTime(fields.day, 0, fields.minutes)
        : startOfDay(fields.day);

  const task = makeTask({
    ...fields,
    id: uuid(),
    dueAt,
    hasTime: hatUhrzeit,
    dueTimeLocal: hatUhrzeit ? fields.minutes : null,
    sortKey: FractionalIndex.afterOrInitial(letzter),
    createdAt: at,
    updatedAt: at,
  });

  await put("tasks", task);
  await award(RewardType.TASK_CREATED, at, await loadTasks(), task.id);
  return task;
}

export async function updateTask(id, patch, at = now()) {
  const task = await getOne("tasks", id);
  if (!task) return null;

  const neu = { ...task, ...patch, updatedAt: at };
  // Eine neue Fälligkeit setzt die Erinnerungskette zurück — sonst mahnt die Aufgabe
  // sofort wieder auf der Stufe von vorher.
  if ("dueAt" in patch && patch.dueAt !== task.dueAt) {
    neu.nagCount = 0;
    neu.nagLastAt = null;
    neu.snoozedUntil = null;
  }
  await put("tasks", neu);
  return neu;
}

/** Fälligkeit setzen — der eine Weg, auf dem `hasTime` und `dueTimeLocal` mitgeführt werden. */
export async function setDue(id, day, minutes = null, at = now()) {
  if (day === null) {
    return updateTask(id, { dueAt: null, hasTime: false, dueTimeLocal: null }, at);
  }
  const hatUhrzeit = minutes !== null;
  return updateTask(
    id,
    {
      dueAt: hatUhrzeit ? atTime(day, 0, minutes) : startOfDay(day),
      hasTime: hatUhrzeit,
      dueTimeLocal: hatUhrzeit ? minutes : null,
    },
    at,
  );
}

/**
 * Abhaken.
 *
 * Eine wiederkehrende Aufgabe wird **nicht** erledigt, sondern rückt vor: Von ihr gibt es
 * immer nur eine offene Instanz. Verpasste Termine zählen in `missedCount` und erzeugen
 * keine zusätzliche Last — sonst tötet eine tägliche Aufgabe den Begleiter, während man im
 * Urlaub ist.
 */
export async function completeTask(id, at = now()) {
  const task = await getOne("tasks", id);
  if (!task || isDeleted(task) || isCompleted(task)) return null;

  const regel = parseRule(task.rrule);
  const warUeberfaellig = isOverdue(task, at);

  if (regel !== null && task.dueAt !== null && task.dueAt !== undefined) {
    const vorgerueckt = advance(regel, dayOf(task.dueAt), dayOf(at));
    if (vorgerueckt.nextDue !== null) {
      await updateTask(
        id,
        {
          dueAt: task.hasTime
            ? atTime(vorgerueckt.nextDue, 0, minutesOfDay(task.dueAt))
            : startOfDay(vorgerueckt.nextDue),
          rrule: vorgerueckt.remainingRule === null ? null : task.rrule,
          missedCount: task.missedCount + vorgerueckt.skipped,
        },
        at,
      );
    } else {
      await updateTask(id, { completedAt: at, rrule: null }, at);
    }
  } else {
    await updateTask(id, { completedAt: at }, at);
  }

  const alle = await loadTasks();
  // Aufgeräumt zählt wie erledigt: Wer eine überfällige Aufgabe endlich abhakt, hat mehr
  // getan als jemand, der eine frische abhakt.
  await award(warUeberfaellig ? RewardType.TASK_CLEANED : RewardType.TASK_DONE, at, alle, id);
  return getOne("tasks", id);
}

/** Haken zurücknehmen. Gibt keine Belohnung zurück — das Log ist angehängt, nicht bearbeitbar. */
export async function uncompleteTask(id, at = now()) {
  return updateTask(id, { completedAt: null }, at);
}

/**
 * Löschen setzt einen Tombstone.
 *
 * War die Aufgabe überfällig, zählt das als Aufräumen. Dass Löschen dasselbe gibt wie
 * Erledigen, ist kein Versehen: Ohne diese Regel bestraft man Ehrlichkeit und belohnt
 * heimliches Löschen.
 */
export async function deleteTask(id, at = now()) {
  const task = await getOne("tasks", id);
  if (!task || isDeleted(task)) return null;

  const warUeberfaellig = isOverdue(task, at);
  const kinder = await loadSubtasks(id);
  await putAll("tasks", kinder.map((kind) => ({ ...kind, deletedAt: at, updatedAt: at })));
  await updateTask(id, { deletedAt: at }, at);

  if (warUeberfaellig) await award(RewardType.TASK_CLEANED, at, await loadTasks(), id);
  return true;
}

export async function restoreTask(id, at = now()) {
  return updateTask(id, { deletedAt: null }, at);
}

/** Umsortieren von Hand: `keys` ist die sichtbare Reihenfolge, `from`/`to` sind Plätze darin. */
export async function reorder(ids, keys, from, to, at = now()) {
  const schluessel = keyForMove(keys, from, to);
  if (schluessel === null) return null;
  return updateTask(ids[from], { sortKey: schluessel }, at);
}

// ---------------------------------------------------------------------- Etiketten

export async function loadTags() {
  return lebend(await getAll("tags"));
}

export async function createTag(name, color = null, at = now()) {
  const sauber = name.trim().replace(/^#/, "");
  const vorhanden = (await loadTags()).find((tag) => tag.name.toLowerCase() === sauber.toLowerCase());
  if (vorhanden) return vorhanden;

  const tag = { id: uuid(), name: sauber, color, createdAt: at, updatedAt: at, deletedAt: null };
  await put("tags", tag);
  return tag;
}

export async function tagsOfTask(taskId) {
  const verknuepfungen = await getAll("task_tags", "taskId", IDBKeyRange.only(taskId));
  const alle = new Map((await loadTags()).map((tag) => [tag.id, tag]));
  return verknuepfungen.map((row) => alle.get(row.tagId)).filter(Boolean);
}

export async function setTaskTags(taskId, tagIds, at = now()) {
  await transaction(["task_tags"], "readwrite", async (tx) => {
    const store = tx.objectStore("task_tags");
    const alte = await request(store.index("taskId").getAllKeys(IDBKeyRange.only(taskId)));
    for (const key of alte) store.delete(key);
    for (const tagId of tagIds) store.put({ taskId, tagId, updatedAt: at, deletedAt: null });
  });
}

// -------------------------------------------------------------------- Gewohnheiten

export async function loadHabits() {
  const rows = lebend(await getAll("habits"));
  return rows.sort((a, b) => (a.sortKey < b.sortKey ? -1 : a.sortKey > b.sortKey ? 1 : 0));
}

export async function createHabit(name, schedule, at = now()) {
  const vorhandene = await loadHabits();
  const habit = {
    id: uuid(),
    name: name.trim(),
    schedule,
    color: null,
    sortKey: FractionalIndex.afterOrInitial(vorhandene.at(-1)?.sortKey ?? null),
    createdAt: at,
    updatedAt: at,
    deletedAt: null,
  };
  await put("habits", habit);
  return habit;
}

export async function updateHabit(id, patch, at = now()) {
  const habit = await getOne("habits", id);
  if (!habit) return null;
  const neu = { ...habit, ...patch, updatedAt: at };
  await put("habits", neu);
  return neu;
}

export async function deleteHabit(id, at = now()) {
  return updateHabit(id, { deletedAt: at }, at);
}

/** Alle Haken der letzten `HABIT_HISTORY_DAYS` Tage, als Menge je Gewohnheit. */
export async function loadCheckins(today) {
  const von = today - Balance.HABIT_HISTORY_DAYS;
  const rows = lebend(await getAll("habit_checkins", "day", IDBKeyRange.lowerBound(von)));

  const nachGewohnheit = new Map();
  for (const row of rows) {
    if (!nachGewohnheit.has(row.habitId)) nachGewohnheit.set(row.habitId, new Set());
    nachGewohnheit.get(row.habitId).add(row.day);
  }
  return nachGewohnheit;
}

/**
 * Haken setzen oder wegnehmen.
 *
 * Die Sperre ist der Kalender: Ein Tag lässt sich nur einmal abhaken. Deshalb braucht die
 * Belohnung hier keine Sperrzeit.
 */
export async function toggleCheckin(habitId, day, at = now()) {
  const vorhanden = await getOne("habit_checkins", [habitId, day]);
  const gesetzt = vorhanden && !isDeleted(vorhanden);

  await put("habit_checkins", {
    habitId,
    day,
    at,
    updatedAt: at,
    deletedAt: gesetzt ? at : null,
  });

  if (!gesetzt) await award(RewardType.HABIT_DONE, at, await loadTasks(), habitId);
  return !gesetzt;
}

// --------------------------------------------------------------------------- Fokus

export async function loadCurrentFocus() {
  const rows = await getAll("focus_sessions", "startedAt");
  return rows.filter((row) => !row.completedAt && !row.abortedAt && !isDeleted(row)).at(-1) ?? null;
}

export async function saveFocus(session, at = now()) {
  await put("focus_sessions", { ...session, updatedAt: at, deletedAt: null });
  return session;
}

export async function loadFocusSessions() {
  return lebend(await getAll("focus_sessions", "startedAt"));
}

// -------------------------------------------------------------------- Einstellungen

const EINSTELLUNGEN = {
  /** „hell“ oder „dunkel“ — dieselbe Gestaltung, getauschte Rollen. */
  fassung: "hell",
  quietHoursEnabled: true,
  quietHoursStart: Balance.QUIET_HOURS_DEFAULT_START,
  quietHoursEnd: Balance.QUIET_HOURS_DEFAULT_END,
  focusMinutes: Balance.FOCUS_MINUTES,
  shortBreakMinutes: Balance.SHORT_BREAK_MINUTES,
  longBreakMinutes: Balance.LONG_BREAK_MINUTES,
  roundsBeforeLongBreak: Balance.ROUNDS_BEFORE_LONG_BREAK,
  notifications: false,
  reduceMotion: false,
  onboardingDone: false,
  defaultListId: null,
};

export async function loadSettings() {
  const rows = await getAll("settings");
  const gespeichert = Object.fromEntries(rows.map((row) => [row.key, row.value]));
  return { ...EINSTELLUNGEN, ...gespeichert };
}

export async function saveSetting(key, value) {
  if (!(key in EINSTELLUNGEN)) throw new Error(`Unbekannte Einstellung: ${key}`);
  await put("settings", { key, value });
}

export const SETTING_DEFAULTS = Object.freeze({ ...EINSTELLUNGEN });

// -------------------------------------------------------------------------- Start

/**
 * Legt beim allerersten Start die zwei Listen an.
 *
 * Eine leere App ohne einen einzigen Ort für die erste Aufgabe ist keine leere App,
 * sondern eine kaputte.
 */
export async function seedIfEmpty(namen, at = now()) {
  const vorhandene = await getAll("task_lists");
  if (vorhandene.length > 0) return await loadLists();

  const listen = [];
  let schluessel = FractionalIndex.initial();
  for (const vorlage of SEED_LISTS) {
    listen.push({
      id: uuid(),
      name: namen[vorlage.stringKey],
      color: null,
      excludeFromNag: vorlage.excludeFromNag,
      sortKey: schluessel,
      createdAt: at,
      updatedAt: at,
      deletedAt: null,
    });
    schluessel = FractionalIndex.after(schluessel);
  }
  await putAll("task_lists", listen);
  return listen;
}

export { Priority, isOpen, isCompleted, isDeleted };
