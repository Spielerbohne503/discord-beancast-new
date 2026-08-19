import { test } from "node:test";
import assert from "node:assert/strict";

import { Balance } from "../src/domain/balance.js";
import { groupToday, isOverdue, overdueBurden, overdueDays, subtaskProgress } from "../src/domain/tasks.js";
import { aufgabe, tag, um } from "./helpers.mjs";

const HEUTE = "2026-08-19";
const JETZT = um(HEUTE, 14);

test("tagestermin_ist_nicht_um_null_uhr_eins_ueberfaellig", () => {
  const heutigerTagestermin = aufgabe({ dueAt: um(HEUTE, 0), hasTime: false });
  assert.ok(!isOverdue(heutigerTagestermin, um(HEUTE, 0, 1)));
  assert.ok(!isOverdue(heutigerTagestermin, um(HEUTE, 23, 59)));
  assert.ok(isOverdue(heutigerTagestermin, um("2026-08-20", 0, 1)));
});

test("termin_mit_uhrzeit_ist_ueberfaellig_sobald_der_zeitpunkt_vorbei_ist", () => {
  const mitUhrzeit = aufgabe({ dueAt: um(HEUTE, 9), hasTime: true });
  assert.ok(!isOverdue(mitUhrzeit, um(HEUTE, 8, 59)));
  assert.ok(isOverdue(mitUhrzeit, um(HEUTE, 9, 1)));
});

test("erledigtes_und_geloeschtes_ist_nie_ueberfaellig", () => {
  const alt = { dueAt: um("2026-01-01", 9), hasTime: true };
  assert.ok(!isOverdue(aufgabe({ ...alt, completedAt: JETZT }), JETZT));
  assert.ok(!isOverdue(aufgabe({ ...alt, deletedAt: JETZT }), JETZT));
});

test("aufgabe_ohne_faelligkeit_wird_nie_ueberfaellig", () => {
  assert.ok(!isOverdue(aufgabe({}), JETZT));
  assert.deepEqual(overdueBurden([aufgabe({})], JETZT), []);
});

test("ueberfaelligkeit_zaehlt_kalendertage_nicht_24_stunden_bloecke", () => {
  const gestern = aufgabe({ dueAt: um("2026-08-18", 23, 30), hasTime: true });
  assert.equal(overdueDays(gestern, um(HEUTE, 0, 30)), 1);
});

test("listen_ohne_mahnung_gehen_nicht_in_die_last_ein", () => {
  const task = aufgabe({ listId: "irgendwann", dueAt: um("2026-08-01", 9), hasTime: true });
  assert.equal(overdueBurden([task], JETZT).length, 1);
  assert.equal(overdueBurden([task], JETZT, new Set(["irgendwann"])).length, 0);
});

test("heute_abgehaktes_bleibt_den_ganzen_tag_stehen", () => {
  const brett = groupToday([aufgabe({ id: "a", completedAt: um(HEUTE, 8) })], um(HEUTE, 23, 59));
  assert.deepEqual(brett.doneToday.map((t) => t.id), ["a"]);
  assert.equal(brett.doneEarlier.length, 0);
});

test("gestern_abgehaktes_rutscht_in_den_rueckblick", () => {
  const brett = groupToday([aufgabe({ id: "a", completedAt: um("2026-08-18", 8) })], JETZT);
  assert.equal(brett.doneToday.length, 0);
  assert.deepEqual(brett.doneEarlier.map((t) => t.id), ["a"]);
});

test("nach_archive_days_faellt_erledigtes_aus_dem_rueckblick", () => {
  const gerade = tag(HEUTE) - Balance.ARCHIVE_DAYS + 1;
  const zuAlt = tag(HEUTE) - Balance.ARCHIVE_DAYS;
  const brett = groupToday(
    [
      aufgabe({ id: "drin", completedAt: um("2026-08-19", 8) - (tag(HEUTE) - gerade) * 86_400_000 }),
      aufgabe({ id: "raus", completedAt: um("2026-08-19", 8) - (tag(HEUTE) - zuAlt) * 86_400_000 }),
    ],
    JETZT,
  );
  assert.deepEqual(brett.doneEarlier.map((t) => t.id), ["drin"]);
});

test("erledigtes_aus_der_zukunft_verstellte_uhr_taucht_nirgends_auf", () => {
  const brett = groupToday([aufgabe({ id: "a", completedAt: um("2026-09-01", 8) })], JETZT);
  assert.ok(brett.isEmpty);
});

test("geloeschtes_und_unteraufgaben_erscheinen_nicht_als_eigene_zeile", () => {
  const brett = groupToday(
    [aufgabe({ id: "weg", deletedAt: JETZT }), aufgabe({ id: "kind", parentId: "eltern" })],
    JETZT,
  );
  assert.ok(brett.isEmpty);
});

test("aufgabe_ohne_faelligkeit_landet_unter_spaeter_und_nicht_im_nichts", () => {
  const brett = groupToday([aufgabe({ id: "a" })], JETZT);
  assert.deepEqual(brett.later.map((t) => t.id), ["a"]);
  assert.equal(brett.openCount, 1);
});

test("innerhalb_eines_blocks_steht_die_fruehere_faelligkeit_oben", () => {
  const brett = groupToday(
    [
      aufgabe({ id: "spaet", dueAt: um(HEUTE, 18), hasTime: true, sortKey: "a" }),
      aufgabe({ id: "frueh", dueAt: um(HEUTE, 16), hasTime: true, sortKey: "z" }),
    ],
    JETZT,
  );
  assert.deepEqual(brett.today.map((t) => t.id), ["frueh", "spaet"]);
});

test("geloeschte_unteraufgaben_zaehlen_nicht_im_fortschritt", () => {
  const fortschritt = subtaskProgress([
    aufgabe({ id: "1", completedAt: JETZT }),
    aufgabe({ id: "2" }),
    aufgabe({ id: "3", deletedAt: JETZT }),
  ]);
  assert.equal(fortschritt.done, 1);
  assert.equal(fortschritt.total, 2);
  assert.ok(fortschritt.hasSubtasks);
  assert.ok(!fortschritt.allDone);
});
