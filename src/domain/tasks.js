/**
 * Aufgaben: Zustand, Überfälligkeit, die Blöcke der Heute-Ansicht.
 *
 * Fälligkeit ist bewusst zweiteilig (Einbahnstraße):
 *  - `dueAt` als absoluter Zeitpunkt in Millisekunden
 *  - `dueTimeLocal` als lokale Uhrzeit in Minuten seit Mitternacht, an der die Erinnerung
 *    hängt
 *
 * Ein Erinnerungstermin darf nie stumpf um 86.400.000 ms fortgeschrieben werden, sonst
 * wandert die 8-Uhr-Erinnerung bei jeder Zeitumstellung.
 */

import { Balance, Priority } from "./balance.js";
import { dayOf } from "./time.js";

/** Ein leeres Aufgabengerüst — die Felder, die es überall gibt. */
export function makeTask(fields) {
  return {
    id: fields.id,
    listId: fields.listId,
    parentId: fields.parentId ?? null,
    title: fields.title ?? "",
    note: fields.note ?? null,
    dueAt: fields.dueAt ?? null,
    dueTimeLocal: fields.dueTimeLocal ?? null,
    hasTime: fields.hasTime ?? false,
    priority: Priority.coerce(fields.priority ?? Priority.DEFAULT),
    rrule: fields.rrule ?? null,
    completedAt: fields.completedAt ?? null,
    sortKey: fields.sortKey,
    missedCount: fields.missedCount ?? 0,
    nagCount: fields.nagCount ?? 0,
    nagLastAt: fields.nagLastAt ?? null,
    snoozedUntil: fields.snoozedUntil ?? null,
    createdAt: fields.createdAt,
    updatedAt: fields.updatedAt ?? fields.createdAt,
    deletedAt: fields.deletedAt ?? null,
  };
}

export const isDeleted = (task) => task.deletedAt !== null && task.deletedAt !== undefined;
export const isCompleted = (task) => task.completedAt !== null && task.completedAt !== undefined;
export const isSubtask = (task) => task.parentId !== null && task.parentId !== undefined;
export const isOpen = (task) => !isCompleted(task) && !isDeleted(task);

/** Der Kalendertag der Fälligkeit, oder `null`. */
export function dueDay(task) {
  return task.dueAt === null || task.dueAt === undefined ? null : dayOf(task.dueAt);
}

/**
 * Überfällig heißt:
 *  - mit Uhrzeit: der Zeitpunkt ist vorbei
 *  - ohne Uhrzeit: der **Tag** ist vorbei — ein Tagestermin ist nicht um 00:01 überfällig
 *
 * Erledigte oder gelöschte Aufgaben sind nie überfällig.
 */
export function isOverdue(task, now) {
  if (!isOpen(task)) return false;
  if (task.dueAt === null || task.dueAt === undefined) return false;
  return task.hasTime ? task.dueAt < now : dayOf(task.dueAt) < dayOf(now);
}

/**
 * Volle Tage überfällig, gemessen in **Kalendertagen** und nicht in 24-Stunden-Blöcken.
 * 0, wenn nicht überfällig.
 */
export function overdueDays(task, now) {
  if (!isOverdue(task, now)) return 0;
  return Math.max(0, dayOf(now) - dayOf(task.dueAt));
}

/** Was die Überfälligkeitslast des Begleiters braucht — sonst nichts. */
export function overdueBurden(tasks, now, excludedListIds = new Set()) {
  return tasks
    .filter((task) => isOverdue(task, now) && !excludedListIds.has(task.listId))
    .map((task) => ({ priority: task.priority, overdueDays: overdueDays(task, now) }));
}

/**
 * Fortschritt einer Aufgabe mit Unteraufgaben („2/5“).
 *
 * Gelöschte Unteraufgaben zählen nicht mit — sonst steht dort für immer eine Zahl, die
 * niemand mehr erreichen kann.
 */
export function subtaskProgress(subtasks) {
  const relevant = subtasks.filter((task) => !isDeleted(task));
  return {
    done: relevant.filter(isCompleted).length,
    total: relevant.length,
    get hasSubtasks() {
      return this.total > 0;
    },
    get allDone() {
      return this.total > 0 && this.done === this.total;
    },
  };
}

/** Früheste Fälligkeit zuerst, Aufgaben ohne Fälligkeit ans Ende, dann Bruchindex. */
function byDueThenSortKey(a, b) {
  const aHasDue = a.dueAt !== null && a.dueAt !== undefined;
  const bHasDue = b.dueAt !== null && b.dueAt !== undefined;
  if (aHasDue !== bHasDue) return aHasDue ? -1 : 1;
  if (aHasDue && a.dueAt !== b.dueAt) return a.dueAt - b.dueAt;
  return a.sortKey < b.sortKey ? -1 : a.sortKey > b.sortKey ? 1 : 0;
}

const byCompletedDesc = (a, b) => b.completedAt - a.completedAt;

/**
 * Sortiert Aufgaben in überfällig / heute / später — plus Erledigtes.
 *
 * - Gelöschte Aufgaben (Tombstone) tauchen nirgends auf.
 * - Unteraufgaben erscheinen unter ihrer Aufgabe, nicht als eigene Zeile.
 * - **Heute Erledigtes bleibt den ganzen Tag stehen.** Sonst verschwindet die gerade
 *   abgehakte Zeile sofort und man kann sie nicht zurückholen.
 * - Früher Erledigtes rutscht in den eingeklappten Rückblick und fällt nach
 *   `Balance.ARCHIVE_DAYS` Tagen auch daraus heraus.
 * - Aufgaben ohne Fälligkeit landen unter „später“; sonst wären sie unsichtbar.
 */
export function groupToday(tasks, now) {
  const today = dayOf(now);
  const archiveFrom = today - Balance.ARCHIVE_DAYS;

  const overdue = [];
  const dueToday = [];
  const later = [];
  const doneToday = [];
  const doneEarlier = [];

  for (const task of tasks) {
    if (isDeleted(task) || isSubtask(task)) continue;

    if (isCompleted(task)) {
      const completedDay = dayOf(task.completedAt);
      if (completedDay === today) doneToday.push(task);
      // Nicht aus der Zukunft (verstellte Uhr) und nicht zu alt.
      else if (completedDay < today && completedDay > archiveFrom) doneEarlier.push(task);
      continue;
    }

    if (isOverdue(task, now)) overdue.push(task);
    else if (dueDay(task) === today) dueToday.push(task);
    else later.push(task);
  }

  const board = {
    overdue: overdue.sort(byDueThenSortKey),
    today: dueToday.sort(byDueThenSortKey),
    later: later.sort(byDueThenSortKey),
    doneToday: doneToday.sort(byCompletedDesc),
    doneEarlier: doneEarlier.sort(byCompletedDesc).slice(0, Balance.ARCHIVE_MAX_ROWS),
  };
  board.openCount = board.overdue.length + board.today.length + board.later.length;
  board.isEmpty = board.openCount === 0 && board.doneToday.length === 0 && board.doneEarlier.length === 0;
  return board;
}
