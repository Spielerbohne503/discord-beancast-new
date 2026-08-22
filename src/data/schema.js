/**
 * Das Schema der lokalen Datenbank.
 *
 * Die Tabellen heißen genauso wie in der Android-Fassung und tragen dieselben Spalten —
 * deshalb lässt sich eine dort erzeugte Sicherung hier einlesen.
 *
 * Drei Festlegungen ziehen sich durch jede Tabelle und sind später nicht mehr zu ändern:
 *
 * 1. **Schlüssel sind UUIDs**, keine fortlaufenden Zahlen. Zwei Geräte können damit ohne
 *    Absprache anlegen.
 * 2. **Jede Zeile trägt `updatedAt`**, und Löschen setzt `deletedAt` statt die Zeile zu
 *    entfernen (Tombstone). Ohne Tombstones lässt sich ein Löschen nie abgleichen — die
 *    Zeile käme beim nächsten Zusammenführen einfach zurück.
 * 3. **Reihenfolge von Hand steckt in `sortKey`** als Bruchindex: Ein Verschieben schreibt
 *    genau eine Zeile.
 */

export const DB_NAME = "petodo";
export const DB_VERSION = 2;

/** Die Zeile im Belohnungs-Log ist unveränderlich — angehängt wird, nie geändert. */
export const STORES = Object.freeze({
  task_lists: { keyPath: "id", indexes: { updatedAt: "updatedAt", sortKey: "sortKey" } },

  tasks: {
    keyPath: "id",
    indexes: {
      listId: "listId",
      parentId: "parentId",
      dueAt: "dueAt",
      completedAt: "completedAt",
      updatedAt: "updatedAt",
    },
  },

  tags: { keyPath: "id", indexes: { updatedAt: "updatedAt", name: "name" } },

  task_tags: { keyPath: ["taskId", "tagId"], indexes: { taskId: "taskId", tagId: "tagId" } },

  reminders: { keyPath: "id", indexes: { taskId: "taskId", at: "at", updatedAt: "updatedAt" } },

  reward_events: { keyPath: "id", indexes: { at: "at", type: "type", updatedAt: "updatedAt" } },

  /** Genau eine Zeile: der zuletzt berechnete Zwischenstand des Begleiters. */
  pet_state: { keyPath: "id", indexes: { updatedAt: "updatedAt" } },

  focus_sessions: {
    keyPath: "id",
    indexes: { startedAt: "startedAt", taskId: "taskId", updatedAt: "updatedAt" },
  },

  habits: { keyPath: "id", indexes: { updatedAt: "updatedAt", sortKey: "sortKey" } },

  /** Ein Haken gehört zu einem Kalendertag, nicht zu einem Zeitpunkt. */
  habit_checkins: {
    keyPath: ["habitId", "day"],
    indexes: { habitId: "habitId", day: "day", updatedAt: "updatedAt" },
  },

  /**
   * Vorlagen: ein Name und eine Liste von Punkten.
   *
   * „Wocheneinkauf“ mit fünf Unteraufgaben, per Knopf neu angelegt. Bewusst **keine**
   * Aufgabe mit einem Merkmal „ist Vorlage“ — dann tauchte sie in jeder Liste, jedem
   * Filter und jeder Zählung auf, und man müsste sie überall wieder herausrechnen.
   */
  templates: { keyPath: "id", indexes: { updatedAt: "updatedAt", sortKey: "sortKey" } },

  template_items: {
    keyPath: "id",
    indexes: { templateId: "templateId", sortKey: "sortKey", updatedAt: "updatedAt" },
  },

  /** Einstellungen. Steht bewusst **nicht** in der Sicherung — die ist gerätebezogen. */
  settings: { keyPath: "key", indexes: {} },
});

export const PET_STATE_ID = "singleton";

/** Die Listen, die es beim ersten Start gibt. */
export const SEED_LISTS = Object.freeze([
  { key: "inbox", stringKey: "list_inbox", excludeFromNag: false },
  // „Irgendwann“ mahnt nie und geht nicht in die Überfälligkeitslast ein. Ohne so eine
  // Liste wandert alles Vage in den Posteingang und macht den Begleiter krank.
  { key: "somedays", stringKey: "list_someday", excludeFromNag: true },
]);
