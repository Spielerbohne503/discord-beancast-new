import { test } from "node:test";
import assert from "node:assert/strict";

import {
  SYNC_VERSION, TOMBSTONE_TAGE, ohneAlteTombstones, unterscheidetSich, zeilenSchluessel,
  zusammenfuehren,
} from "../src/domain/sync.js";

const aufgabe = (id, updatedAt, extra = {}) => ({ id, title: id, updatedAt, deletedAt: null, ...extra });

test("je_zeile_gewinnt_der_juengere_stand_nicht_je_datei", () => {
  // Auf dem Telefon eine Aufgabe abgehakt, am Rechner eine andere angelegt: beides bleibt.
  const telefon = { tasks: [aufgabe("a", 200, { completedAt: 200 }), aufgabe("b", 100)] };
  const rechner = { tasks: [aufgabe("a", 100), aufgabe("b", 100), aufgabe("c", 150)] };

  const { tabellen } = zusammenfuehren(rechner, telefon);
  const nach = new Map(tabellen.tasks.map((zeile) => [zeile.id, zeile]));

  assert.equal(nach.size, 3);
  assert.equal(nach.get("a").completedAt, 200);
  assert.ok(nach.has("c"));
});

test("das_ergebnis_haengt_nicht_davon_ab_wer_zuerst_kommt", () => {
  const eins = { tasks: [aufgabe("a", 200), aufgabe("b", 100)] };
  const zwei = { tasks: [aufgabe("a", 100), aufgabe("c", 300)] };

  const sortiert = (zeilen) => [...zeilen].sort((a, b) => (a.id < b.id ? -1 : 1));
  assert.deepEqual(
    sortiert(zusammenfuehren(eins, zwei).tabellen.tasks),
    sortiert(zusammenfuehren(zwei, eins).tabellen.tasks),
  );
});

test("ein_geloeschtes_kommt_nicht_zurueck", () => {
  // Der Fehler, an dem selbstgebauter Abgleich fast immer scheitert.
  const { tabellen } = zusammenfuehren(
    { tasks: [aufgabe("a", 100)] },
    { tasks: [aufgabe("a", 300, { deletedAt: 300 })] },
  );
  assert.equal(tabellen.tasks.length, 1);
  assert.equal(tabellen.tasks[0].deletedAt, 300);
});

test("ein_juengeres_zurueckholen_schlaegt_den_grabstein", () => {
  const { tabellen } = zusammenfuehren(
    { tasks: [aufgabe("a", 300, { deletedAt: 300 })] },
    { tasks: [aufgabe("a", 400)] },
  );
  assert.equal(tabellen.tasks[0].deletedAt, null);
});

test("gleichstand_behaelt_den_eigenen_stand", () => {
  const { tabellen, bericht } = zusammenfuehren(
    { tasks: [aufgabe("a", 100, { title: "meins" })] },
    { tasks: [aufgabe("a", 100, { title: "fremd" })] },
  );
  assert.equal(tabellen.tasks[0].title, "meins");
  assert.equal(bericht.uebernommen, 0);
  assert.equal(bericht.behalten, 1);
});

test("zusammengesetzte_schluessel_loeschen_sich_nicht_gegenseitig", () => {
  // Ohne Sonderbehandlung gälten alle Haken einer Woche als dieselbe Zeile.
  const lokal = {
    habit_checkins: [
      { habitId: "h", day: 1, updatedAt: 10, deletedAt: null },
      { habitId: "h", day: 2, updatedAt: 10, deletedAt: null },
    ],
  };
  const fremd = {
    habit_checkins: [
      { habitId: "h", day: 3, updatedAt: 10, deletedAt: null },
      { habitId: "g", day: 1, updatedAt: 10, deletedAt: null },
    ],
  };

  assert.equal(zusammenfuehren(lokal, fremd).tabellen.habit_checkins.length, 4);
  assert.notEqual(
    zeilenSchluessel("habit_checkins", { habitId: "h", day: 1 }),
    zeilenSchluessel("habit_checkins", { habitId: "h", day: 2 }),
  );
  assert.notEqual(
    zeilenSchluessel("task_tags", { taskId: "a", tagId: "x" }),
    zeilenSchluessel("task_tags", { taskId: "a", tagId: "y" }),
  );
});

test("eine_zeile_ohne_schluessel_wird_uebergangen_statt_zu_werfen", () => {
  const { tabellen } = zusammenfuehren({ tasks: [] }, { tasks: [{ updatedAt: 1 }, aufgabe("a", 1)] });
  assert.equal(tabellen.tasks.length, 1);
});

test("jede_tabelle_taucht_im_ergebnis_auf_auch_wenn_sie_leer_ist", () => {
  const { tabellen } = zusammenfuehren({}, {});
  assert.ok(Object.keys(tabellen).length >= 10);
  assert.ok(Object.values(tabellen).every((zeilen) => Array.isArray(zeilen)));
});

test("gleiche_staende_erkennen_sich_und_ersparen_das_hochladen", () => {
  const stand = { tasks: [aufgabe("a", 100), aufgabe("b", 200)] };
  assert.ok(!unterscheidetSich(stand, { tasks: [aufgabe("b", 200), aufgabe("a", 100)] }));
  assert.ok(unterscheidetSich(stand, { tasks: [aufgabe("a", 101), aufgabe("b", 200)] }));
  assert.ok(unterscheidetSich(stand, { tasks: [aufgabe("a", 100)] }));
  assert.ok(unterscheidetSich(stand, { tasks: [aufgabe("a", 100), aufgabe("c", 200)] }));
});

test("alte_grabsteine_fallen_weg_neue_bleiben_stehen", () => {
  const jetzt = 1_800_000_000_000;
  const tabellen = ohneAlteTombstones(
    {
      tasks: [
        aufgabe("alt", 1, { deletedAt: jetzt - (TOMBSTONE_TAGE + 1) * 86_400_000 }),
        aufgabe("frisch", 1, { deletedAt: jetzt - 86_400_000 }),
        aufgabe("lebt", 1),
      ],
    },
    jetzt,
  );

  assert.deepEqual(tabellen.tasks.map((zeile) => zeile.id), ["frisch", "lebt"]);
});

test("die_fassung_des_formats_wird_nicht_geraten", () => {
  assert.equal(SYNC_VERSION, 1);
});
