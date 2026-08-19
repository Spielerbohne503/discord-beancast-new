/** Gemeinsame Hilfen für die Tests. Keine Bibliothek, nur ein paar Kürzel. */

import { atTime, dayFromIso } from "../src/domain/time.js";
import { makeTask } from "../src/domain/tasks.js";

/** Ein Kalendertag aus `YYYY-MM-DD`. */
export const tag = dayFromIso;

/** Ein Zeitpunkt an einem Tag. */
export const um = (iso, hour = 0, minute = 0) => atTime(dayFromIso(iso), hour, minute);

/** Eine Aufgabe mit brauchbaren Vorgaben — der Test nennt nur, worauf es ankommt. */
export function aufgabe(fields = {}) {
  return makeTask({
    id: fields.id ?? "t1",
    listId: fields.listId ?? "inbox",
    title: fields.title ?? "Aufgabe",
    sortKey: fields.sortKey ?? "m",
    createdAt: fields.createdAt ?? 0,
    ...fields,
  });
}
