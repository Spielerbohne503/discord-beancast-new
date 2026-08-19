import { test } from "node:test";
import assert from "node:assert/strict";

import { Schedule, currentStreak, longestStreak, weekProgress } from "../src/domain/habits.js";
import { tag } from "./helpers.mjs";

const MO = tag("2026-08-17");
const MWF = Schedule.toggle(Schedule.toggle(Schedule.toggle(Schedule.NONE, 1), 3), 5);

test("serie_bricht_nicht_an_einem_tag_an_dem_nichts_anstand", () => {
  // Montag und Mittwoch abgehakt, Donnerstag ist Stichtag: Dienstag zählt nicht dagegen.
  assert.equal(currentStreak(new Set([MO, MO + 2]), MWF, MO + 3), 2);
});

test("der_heutige_tag_zaehlt_nie_gegen_einen", () => {
  // Freitag steht an und ist noch offen — die Serie aus Mo/Mi läuft weiter.
  assert.equal(currentStreak(new Set([MO, MO + 2]), MWF, MO + 4), 2);
  // Abgehakt wird sie länger.
  assert.equal(currentStreak(new Set([MO, MO + 2, MO + 4]), MWF, MO + 4), 3);
});

test("verpasster_termin_beendet_die_serie", () => {
  // Mittwoch fehlt, Freitag ist abgehakt: nur der Freitag zählt.
  assert.equal(currentStreak(new Set([MO, MO + 4]), MWF, MO + 4), 1);
});

test("leerer_zeitplan_hat_keine_serie", () => {
  assert.equal(currentStreak(new Set([MO]), Schedule.NONE, MO), 0);
  assert.equal(longestStreak(new Set([MO]), Schedule.NONE), 0);
});

test("laengste_serie_zaehlt_nur_termine", () => {
  const haken = new Set([MO, MO + 2, MO + 4, MO + 9, MO + 11]);
  assert.equal(longestStreak(haken, MWF), 3);
});

test("wochenziel_zaehlt_die_anstehenden_termine_der_woche", () => {
  assert.deepEqual(weekProgress(new Set([MO, MO + 2]), MWF, MO + 3), { done: 2, due: 3 });
  assert.deepEqual(weekProgress(new Set(), Schedule.DAILY, MO), { done: 0, due: 7 });
});

test("kaputte_maske_wird_beschnitten_statt_zu_haengen", () => {
  assert.equal(Schedule.of(0b1111_1111_1111), Schedule.DAILY);
  assert.equal(Schedule.timesPerWeek(0xffff), 7);
});

test("wochentagsmasken_stimmen_mit_iso_ueberein", () => {
  assert.deepEqual(Schedule.weekdays(Schedule.WEEKDAYS), [1, 2, 3, 4, 5]);
  assert.deepEqual(Schedule.weekdays(Schedule.WEEKEND), [6, 7]);
  assert.ok(Schedule.isDueOn(Schedule.WEEKDAYS, MO));
  assert.ok(!Schedule.isDueOn(Schedule.WEEKDAYS, MO + 5));
});

test("umschalten_ist_umkehrbar", () => {
  assert.equal(Schedule.toggle(Schedule.toggle(MWF, 2), 2), MWF);
});
