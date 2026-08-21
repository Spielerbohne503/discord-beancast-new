import { test } from "node:test";
import assert from "node:assert/strict";

import { BACKUP_TABLES } from "../src/domain/backup.js";
import { STORES } from "../src/data/schema.js";

/**
 * Der Wächter gegen die stille Lücke: Eine neue Tabelle, die niemand in die Sicherung
 * aufnimmt, fällt erst auf, wenn jemand seine Daten schon verloren hat.
 */

/** Nicht gesichert wird nur, was fachlich nichts trägt. */
const ABSICHTLICH_DRAUSSEN = new Set([
  // Einstellungen sind gerätebezogen, und das Dateiformat ist Fassung 1 — die
  // Android-Sicherung kennt sie nicht, und sie soll hier einlesbar bleiben.
  "settings",
]);

test("jede_tabelle_steht_entweder_in_der_sicherung_oder_bewusst_nicht_drin", () => {
  for (const tabelle of Object.keys(STORES)) {
    const gesichert = BACKUP_TABLES.includes(tabelle);
    assert.ok(
      gesichert || ABSICHTLICH_DRAUSSEN.has(tabelle),
      `Tabelle ${tabelle} wird nicht gesichert und steht nicht auf der Ausnahmeliste`,
    );
  }
});

test("die_sicherung_nennt_keine_tabelle_die_es_nicht_gibt", () => {
  for (const tabelle of BACKUP_TABLES) {
    assert.ok(tabelle in STORES, `Sicherung nennt ${tabelle}, das Schema kennt es nicht`);
  }
});

test("ausnahmeliste_nennt_nur_tabellen_die_es_gibt", () => {
  for (const tabelle of ABSICHTLICH_DRAUSSEN) {
    assert.ok(tabelle in STORES, `Ausnahmeliste nennt ${tabelle}, das Schema kennt es nicht`);
  }
});
