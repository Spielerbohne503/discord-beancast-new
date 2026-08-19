import { test } from "node:test";
import assert from "node:assert/strict";

import { Balance } from "../src/domain/balance.js";
import {
  HealthStage, INITIAL_VALUES, RewardType, SpeechCategory, averageOf, baseMultiplier,
  computePet, decay, elapsedHours, initialPetState, isRewardAllowed,
  isTaskCreationRewardAllowed, levelCost, levelForXp, levelProgress, loadContribution,
  moodCategory, moodMultiplier, overdueLoad, petValues, pickSpeech, remainingCooldownMs,
  rewardEvent, stageOf, stageOfAverage, startedOverdueWeeks, totalXpFor, xpToNextLevel,
} from "../src/domain/pet.js";

const STUNDE = 3_600_000;

test("werte_werden_gekappt_kein_weg_erzeugt_137_oder_minus_12", () => {
  assert.equal(petValues(137, -12, 50).energy, 100);
  assert.equal(petValues(137, -12, 50).satiety, 0);
});

test("stufen_greifen_an_den_festgelegten_schwellen", () => {
  assert.equal(stageOfAverage(100), HealthStage.HEALTHY);
  assert.equal(stageOfAverage(Balance.STAGE_HEALTHY_ABOVE), HealthStage.WEAKENED);
  assert.equal(stageOfAverage(Balance.STAGE_WEAKENED_ABOVE), HealthStage.WEAKENED);
  assert.equal(stageOfAverage(Balance.STAGE_WEAKENED_ABOVE - 0.1), HealthStage.SICK);
  assert.equal(stageOfAverage(Balance.STAGE_SICK_ABOVE), HealthStage.SICK);
  assert.equal(stageOfAverage(0), HealthStage.MISERABLE);
});

test("es_gibt_keine_stufe_unter_elend_kein_tod", () => {
  assert.equal(stageOf(petValues(0, 0, 0)), HealthStage.MISERABLE);
});

test("verfall_wird_nach_24_stunden_gedeckelt_urlaub_kostet_einen_tag", () => {
  assert.equal(elapsedHours(0, 14 * 24 * STUNDE), Balance.DECAY_MAX_ELAPSED_HOURS);

  const nachEinemTag = decay(INITIAL_VALUES, 0, 24 * STUNDE, 0);
  const nachZweiWochen = decay(INITIAL_VALUES, 0, 14 * 24 * STUNDE, 0);
  assert.deepEqual(nachZweiWochen, nachEinemTag);
});

test("rueckwaerts_laufende_uhr_laesst_die_werte_stehen", () => {
  assert.equal(elapsedHours(1000, 0), 0);
  assert.deepEqual(decay(INITIAL_VALUES, 1000, 0, 0), INITIAL_VALUES);
});

test("verfall_folgt_den_basisstunden_aus_balance", () => {
  const werte = decay(INITIAL_VALUES, 0, 12 * STUNDE, 0);
  assert.equal(werte.energy, 0);
  assert.equal(werte.satiety, 25);
  assert.equal(werte.mood, 50);
});

test("ueberfaelligkeitslast_ist_bei_zehn_gedeckelt", () => {
  const viele = Array.from({ length: 50 }, () => ({ priority: 3, overdueDays: 30 }));
  assert.equal(overdueLoad(viele), Balance.LOAD_CAP);
});

test("angefangene_woche_zaehlt_ab_dem_ersten_tag", () => {
  assert.equal(startedOverdueWeeks(0), 0);
  assert.equal(startedOverdueWeeks(1), 1);
  assert.equal(startedOverdueWeeks(7), 1);
  assert.equal(startedOverdueWeeks(8), 2);
});

test("prioritaet_gewichtet_den_beitrag", () => {
  assert.equal(loadContribution({ priority: 0, overdueDays: 0 }), 0.5);
  assert.equal(loadContribution({ priority: 3, overdueDays: 0 }), 2.0);
});

test("multiplikator_laeuft_von_eins_bis_drei", () => {
  assert.equal(baseMultiplier(0), 1);
  assert.equal(baseMultiplier(Balance.LOAD_CAP), 3);
  assert.equal(baseMultiplier(Balance.LOAD_CAP * 2), 3);
});

test("die_laune_faellt_schneller_wenn_die_anderen_werte_unten_sind", () => {
  assert.ok(moodMultiplier(0, 0, 0) > moodMultiplier(0, 100, 100));
  assert.equal(moodMultiplier(0, 100, 100), 1);
});

test("nur_computePet_veraendert_werte_ereignisse_kommen_nach_dem_verfall", () => {
  const vorher = initialPetState(0);
  const gefuettert = rewardEvent("e1", 6 * STUNDE, RewardType.FEED);
  const nachher = computePet(vorher, [gefuettert], 6 * STUNDE, 0);

  // Erst Verfall (Sättigung 100 → 62,5), dann +35 — gekappt bei 100.
  assert.equal(nachher.values.satiety, 97.5);
  assert.equal(nachher.xp, Balance.REWARD.FEED.xp);
  assert.equal(nachher.lastFedAt, 6 * STUNDE);
});

test("ereignisse_werden_in_zeitfolge_verrechnet_egal_wie_sie_hereinkommen", () => {
  const vorher = initialPetState(0);
  const spaet = rewardEvent("b", 5 * STUNDE, RewardType.PAT);
  const frueh = rewardEvent("a", 1 * STUNDE, RewardType.FEED);

  const einsRum = computePet(vorher, [spaet, frueh], 5 * STUNDE, 0);
  const andersRum = computePet(vorher, [frueh, spaet], 5 * STUNDE, 0);
  assert.deepEqual(einsRum, andersRum);
  assert.equal(einsRum.lastPattedAt, 5 * STUNDE);
});

test("xp_faellt_nie_unter_null", () => {
  const vorher = { ...initialPetState(0), xp: 2 };
  const abzug = { id: "x", at: 0, type: RewardType.PAT, dEnergy: 0, dSatiety: 0, dMood: 0, dXp: -50 };
  assert.equal(computePet(vorher, [abzug], 0, 0).xp, 0);
});

test("sperrzeit_verhindert_dass_man_sich_aus_der_krankheit_tippt", () => {
  const jetzt = 10 * 60_000;
  assert.ok(!isRewardAllowed(RewardType.PAT, 0, jetzt));
  assert.equal(remainingCooldownMs(RewardType.PAT, 0, jetzt), 20 * 60_000);
  assert.ok(isRewardAllowed(RewardType.PAT, 0, 30 * 60_000));
});

test("ohne_sperrzeit_ist_immer_erlaubt", () => {
  assert.ok(isRewardAllowed(RewardType.TASK_DONE, 0, 0));
  assert.equal(remainingCooldownMs(RewardType.TASK_DONE, 0, 0), 0);
});

test("aufraeumen_zahlt_wie_erledigen", () => {
  assert.ok(Balance.REWARD.TASK_CLEANED.xp > 0);
  assert.ok(Balance.REWARD.TASK_CLEANED.mood > 0);
});

test("erfassungs_xp_sind_am_tag_gedeckelt", () => {
  assert.ok(isTaskCreationRewardAllowed(Balance.REWARD_TASK_CREATED_MAX_PER_DAY - 1));
  assert.ok(!isTaskCreationRewardAllowed(Balance.REWARD_TASK_CREATED_MAX_PER_DAY));
});

test("levelkurve_erste_stufe_kostet_hundert_jede_weitere_das_anderthalbfache", () => {
  assert.equal(levelCost(1), 100);
  assert.equal(levelCost(2), 150);
  assert.equal(levelCost(3), 225);
  assert.equal(levelForXp(0), 1);
  assert.equal(levelForXp(99), 1);
  assert.equal(levelForXp(100), 2);
  assert.equal(levelForXp(249), 2);
  assert.equal(levelForXp(250), 3);
});

test("absurde_xp_zahlen_haengen_die_rechnung_nicht_auf", () => {
  // Die Stufen wachsen um das Anderthalbfache; schon vor Level 200 ist jede darstellbare
  // XP-Zahl aufgebraucht. Die Obergrenze steht trotzdem da — sie ist die Bremse für den
  // Fall, dass jemand `LEVEL_GROWTH` auf 1,0 stellt.
  const level = levelForXp(Number.MAX_SAFE_INTEGER);
  assert.ok(level > 1 && level <= Balance.LEVEL_MAX, String(level));
  assert.equal(levelForXp(-5), 1);
});

test("fortschritt_und_rest_passen_zueinander", () => {
  const xp = 180;
  assert.equal(totalXpFor(levelForXp(xp)), 100);
  assert.ok(levelProgress(xp) > 0 && levelProgress(xp) < 1);
  assert.equal(xpToNextLevel(xp), 70);
  assert.equal(levelProgress(0), 0);
});

test("beduerfnisse_gehen_stimmungen_vor", () => {
  assert.equal(moodCategory(petValues(100, 10, 100)), SpeechCategory.HUNGRY);
  assert.equal(moodCategory(petValues(10, 100, 100)), SpeechCategory.TIRED);
  assert.equal(moodCategory(petValues(35, 35, 35)), SpeechCategory.SICK);
  assert.equal(moodCategory(petValues(100, 100, 100)), SpeechCategory.HAPPY);
  assert.equal(moodCategory(petValues(60, 60, 60)), SpeechCategory.BORED);
});

test("hunger_geht_vor_muedigkeit", () => {
  assert.equal(moodCategory(petValues(10, 10, 100)), SpeechCategory.HUNGRY);
});

test("derselbe_satz_wiederholt_sich_nicht_ungebremst", () => {
  const texte = ["a", "b", "c"];
  // Der Würfel wählt immer „a“, „a“ stand schon da, der Wurf liegt über der Schwelle.
  assert.notEqual(pickSpeech(texte, "a", 0.9, () => 0), "a");
  // Unter der Schwelle darf wiederholt werden.
  assert.equal(pickSpeech(texte, "a", 0.1, () => 0), "a");
});

test("leere_kategorie_heisst_der_begleiter_sagt_nichts", () => {
  assert.equal(pickSpeech([], null, 0.5, () => 0), null);
  assert.equal(pickSpeech(["nur einer"], "nur einer", 0.9, () => 0), "nur einer");
});

test("durchschnitt_entscheidet_ueber_die_stufe", () => {
  assert.equal(averageOf(petValues(60, 60, 60)), 60);
});
