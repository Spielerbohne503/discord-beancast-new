/**
 * Welche Aufgaben eine Ansicht zeigt.
 *
 * Reine Auswahl, keine Speicherung: Die schlauen Listen sind Filter über denselben
 * Bestand, keine eigenen Tabellen. Deshalb kostet eine neue Ansicht später keine
 * Schemaänderung.
 */

import { dayOf } from "./time.js";
import { dueDay, isCompleted, isDeleted, isOpen, isOverdue, isSubtask } from "./tasks.js";

export const Scope = Object.freeze({
  ALL_OPEN: "all_open",
  NEXT_SEVEN_DAYS: "next_seven_days",
  COMPLETED: "completed",
  /** Eine konkrete Liste: `{ kind: "list", listId }` */
  LIST: "list",
});

export const SEVEN_DAYS = 7;

export function listScope(listId) {
  return { kind: Scope.LIST, listId };
}

export function scope(kind) {
  return { kind };
}

function matchesScope(task, current, now, today) {
  if (current.kind === Scope.COMPLETED) return isCompleted(task);
  if (current.kind === Scope.ALL_OPEN) return isOpen(task);
  if (current.kind === Scope.LIST) return isOpen(task) && task.listId === current.listId;

  if (!isOpen(task)) return false;
  const due = dueDay(task);
  if (due === null) return false;
  // Überfälliges gehört dazu — es ist das Dringendste, was es gibt.
  if (isOverdue(task, now)) return true;
  return due <= today + SEVEN_DAYS;
}

function matchesQuery(task, query) {
  const needle = query.toLowerCase();
  return (
    task.title.toLowerCase().includes(needle) ||
    (task.note ?? "").toLowerCase().includes(needle)
  );
}

/**
 * Eine konkrete Liste behält die Reihenfolge von Hand (Bruchindex) — nur dort ergibt
 * Ziehen einen Sinn. Die schlauen Listen sortieren nach Fälligkeit, weil sie Aufgaben aus
 * mehreren Listen zusammenwürfeln. Erledigte: das zuletzt Erledigte oben.
 */
export function comparatorFor(current) {
  if (current.kind === Scope.COMPLETED) {
    return (a, b) => (b.completedAt ?? 0) - (a.completedAt ?? 0) || compareKeys(a, b);
  }
  if (current.kind === Scope.LIST) return compareKeys;

  return (a, b) => {
    const aHasDue = a.dueAt !== null && a.dueAt !== undefined;
    const bHasDue = b.dueAt !== null && b.dueAt !== undefined;
    if (aHasDue !== bHasDue) return aHasDue ? -1 : 1;
    if (aHasDue && a.dueAt !== b.dueAt) return a.dueAt - b.dueAt;
    if (a.priority !== b.priority) return b.priority - a.priority;
    return compareKeys(a, b);
  };
}

function compareKeys(a, b) {
  return a.sortKey < b.sortKey ? -1 : a.sortKey > b.sortKey ? 1 : 0;
}

/**
 * Wendet Bereich und Suchtext an.
 *
 * Gesucht wird in Titel und Notiz, ohne Rücksicht auf Groß- und Kleinschreibung.
 * Unteraufgaben tauchen nur auf, wenn tatsächlich gesucht wird — sonst stünde eine
 * Unteraufgabe zusammenhanglos zwischen den Aufgaben.
 */
export function filterTasks(tasks, current, query, now) {
  const needle = String(query ?? "").trim();
  const searching = needle.length > 0;
  const today = dayOf(now);

  return tasks
    .filter((task) => !isDeleted(task))
    .filter((task) => searching || !isSubtask(task))
    .filter((task) => matchesScope(task, current, now, today))
    .filter((task) => !searching || matchesQuery(task, needle))
    .sort(comparatorFor(current));
}

/** Ob in dieser Ansicht von Hand umsortiert werden darf. */
export function isManuallyOrdered(current, query) {
  return current.kind === Scope.LIST && String(query ?? "").trim().length === 0;
}
