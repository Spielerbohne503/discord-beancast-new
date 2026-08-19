import { test } from "node:test";
import assert from "node:assert/strict";

import { longestStreak, perDay, statsOf, streak } from "../src/domain/stats.js";
import { tag } from "./helpers.mjs";

const HEUTE = tag("2026-08-19");

test("der_heutige_tag_zaehlt_nie_gegen_einen", () => {
  // Gestern und vorgestern geschafft, heute morgen noch nichts: Die Serie steht.
  assert.equal(streak(new Set([HEUTE - 1, HEUTE - 2]), HEUTE), 2);
});

test("zwei_tage_pause_beenden_die_serie", () => {
  assert.equal(streak(new Set([HEUTE - 2, HEUTE - 3]), HEUTE), 0);
});

test("heute_geschafftes_verlaengert_die_serie", () => {
  assert.equal(streak(new Set([HEUTE, HEUTE - 1]), HEUTE), 2);
});

test("ohne_einen_einzigen_tag_gibt_es_keine_serie", () => {
  assert.equal(streak(new Set(), HEUTE), 0);
  assert.equal(longestStreak(new Set()), 0);
});

test("laengste_serie_findet_den_besten_abschnitt", () => {
  const tage = new Set([HEUTE - 10, HEUTE - 5, HEUTE - 4, HEUTE - 3, HEUTE - 1]);
  assert.equal(longestStreak(tage), 3);
});

test("leere_tage_bleiben_als_stummel_im_diagramm_stehen", () => {
  const balken = perDay([HEUTE, HEUTE], HEUTE - 2, HEUTE);
  assert.deepEqual(balken, [
    { day: HEUTE - 2, count: 0 },
    { day: HEUTE - 1, count: 0 },
    { day: HEUTE, count: 2 },
  ]);
});

test("verdrehter_zeitraum_ergibt_kein_diagramm_statt_einer_endlosschleife", () => {
  assert.deepEqual(perDay([], HEUTE, HEUTE - 1), []);
});

test("rueckblick_zaehlt_jede_erledigte_aufgabe_die_serie_aber_je_tag_einmal", () => {
  const rueckblick = statsOf([HEUTE - 1, HEUTE - 1, HEUTE - 2], HEUTE, 7);
  assert.equal(rueckblick.totalCompleted, 3);
  assert.equal(rueckblick.streak, 2);
  assert.equal(rueckblick.days.length, 7);
  assert.equal(rueckblick.periodCompleted, 3);
  assert.deepEqual(rueckblick.busiestDay, { day: HEUTE - 1, count: 2 });
});

test("ohne_erledigtes_gibt_es_keinen_besten_tag", () => {
  assert.equal(statsOf([], HEUTE, 7).busiestDay, null);
});
