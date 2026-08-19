import { test } from "node:test";
import assert from "node:assert/strict";

import {
  NagOutcome, NagStage, QUIET_HOURS_DEFAULT, QUIET_HOURS_OFF, anchorMinutes, decideNag,
  firstNagAt, isInQuietHours, nagStageFor, nextNagAt, postponeToTomorrow, quietHours,
  shiftOutOfQuietHours, shouldGroup, stageTraits,
} from "../src/domain/nag.js";
import { Balance } from "../src/domain/balance.js";
import { minutesOfDay, parseHhMm } from "../src/domain/time.js";
import { aufgabe, um } from "./helpers.mjs";

const HEUTE = "2026-08-19";

test("erinnerung_eskaliert_statt_sich_zu_wiederholen", () => {
  assert.equal(nagStageFor(0), NagStage.FIRST);
  assert.equal(nagStageFor(1), NagStage.AGAIN);
  assert.equal(nagStageFor(Balance.NAG_DAY_LOUD - 1), NagStage.LOUD);
  assert.equal(nagStageFor(Balance.NAG_DAY_CLEANUP - 1), NagStage.CLEANUP);

  assert.ok(!stageTraits(NagStage.FIRST).makesSound);
  assert.ok(stageTraits(NagStage.LOUD).makesSound);
  assert.ok(stageTraits(NagStage.CLEANUP).asksCleanupQuestion);
});

test("ruhezeit_ueber_mitternacht_faengt_beide_seiten", () => {
  assert.ok(isInQuietHours(QUIET_HOURS_DEFAULT, parseHhMm("23:30")));
  assert.ok(isInQuietHours(QUIET_HOURS_DEFAULT, parseHhMm("02:00")));
  assert.ok(!isInQuietHours(QUIET_HOURS_DEFAULT, parseHhMm("12:00")));
  assert.ok(!isInQuietHours(QUIET_HOURS_DEFAULT, parseHhMm("08:00")));
});

test("ruhezeit_am_tag_faengt_nur_das_fenster", () => {
  const mittagsruhe = quietHours(parseHhMm("13:00"), parseHhMm("14:00"));
  assert.ok(isInQuietHours(mittagsruhe, parseHhMm("13:30")));
  assert.ok(!isInQuietHours(mittagsruhe, parseHhMm("14:00")));
  assert.ok(!isInQuietHours(mittagsruhe, parseHhMm("02:00")));
});

test("abgeschaltete_ruhezeit_faengt_nichts", () => {
  assert.ok(!isInQuietHours(QUIET_HOURS_OFF, parseHhMm("02:00")));
  assert.equal(shiftOutOfQuietHours(um(HEUTE, 2), QUIET_HOURS_OFF), um(HEUTE, 2));
});

test("erinnerung_in_der_ruhezeit_wird_verschoben_nie_verworfen", () => {
  assert.equal(shiftOutOfQuietHours(um(HEUTE, 23, 30), QUIET_HOURS_DEFAULT), um("2026-08-20", 8));
  assert.equal(shiftOutOfQuietHours(um(HEUTE, 2), QUIET_HOURS_DEFAULT), um(HEUTE, 8));
  assert.equal(shiftOutOfQuietHours(um(HEUTE, 12), QUIET_HOURS_DEFAULT), um(HEUTE, 12));
});

test("ankeruhrzeit_kommt_zuerst_aus_dueTimeLocal", () => {
  assert.equal(anchorMinutes(aufgabe({ dueTimeLocal: 8 * 60, dueAt: um(HEUTE, 15), hasTime: true })), 480);
  assert.equal(anchorMinutes(aufgabe({ dueAt: um(HEUTE, 15), hasTime: true })), 15 * 60);
  assert.equal(anchorMinutes(aufgabe({ dueAt: um(HEUTE, 0) })), Balance.DEFAULT_DUE_HOUR * 60);
});

test("naechster_termin_behaelt_die_uhrzeit_statt_86400000_ms_zu_addieren", () => {
  const anker = parseHhMm("08:00");
  const naechster = nextNagAt(um(HEUTE, 8), anker);
  assert.equal(minutesOfDay(naechster), anker);
  assert.equal(naechster, um("2026-08-20", 8));
});

test("ohne_faelligkeit_gibt_es_keine_erinnerung", () => {
  assert.equal(firstNagAt(aufgabe({})), null);
  const ergebnis = decideNag(aufgabe({}), false, um(HEUTE, 12), QUIET_HOURS_DEFAULT);
  assert.equal(ergebnis.outcome, NagOutcome.CANCEL);
  assert.equal(ergebnis.reason, "no_due_date");
});

test("erledigtes_und_geloeschtes_wird_nicht_gemahnt", () => {
  const faellig = { dueAt: um("2026-08-01", 9), hasTime: true };
  const jetzt = um(HEUTE, 12);
  assert.equal(decideNag(aufgabe({ ...faellig, completedAt: jetzt }), false, jetzt, QUIET_HOURS_DEFAULT).reason, "completed");
  assert.equal(decideNag(aufgabe({ ...faellig, deletedAt: jetzt }), false, jetzt, QUIET_HOURS_DEFAULT).reason, "deleted");
  assert.equal(decideNag(aufgabe(faellig), true, jetzt, QUIET_HOURS_DEFAULT).reason, "list_excluded");
});

test("aufgeschobenes_schlaegt_den_zeitplan", () => {
  const jetzt = um(HEUTE, 12);
  const task = aufgabe({ dueAt: um("2026-08-01", 9), hasTime: true, snoozedUntil: um(HEUTE, 15) });
  const ergebnis = decideNag(task, false, jetzt, QUIET_HOURS_DEFAULT);
  assert.equal(ergebnis.outcome, NagOutcome.RESCHEDULE);
  assert.equal(ergebnis.reason, "snoozed");
  assert.equal(ergebnis.at, um(HEUTE, 15));
});

test("vor_dem_termin_wird_nur_neu_eingeplant", () => {
  const task = aufgabe({ dueAt: um("2026-08-25", 9), hasTime: true });
  const ergebnis = decideNag(task, false, um(HEUTE, 12), QUIET_HOURS_DEFAULT);
  assert.equal(ergebnis.outcome, NagOutcome.RESCHEDULE);
  assert.equal(ergebnis.reason, "not_due_yet");
});

test("in_der_ruhezeit_wird_ans_fensterende_verschoben", () => {
  const task = aufgabe({ dueAt: um("2026-08-01", 9), hasTime: true });
  const ergebnis = decideNag(task, false, um(HEUTE, 2), QUIET_HOURS_DEFAULT);
  assert.equal(ergebnis.reason, "quiet_hours");
  assert.equal(ergebnis.at, um(HEUTE, 8));
});

test("faellige_aufgabe_wird_gemeldet_und_der_zaehler_steigt", () => {
  const task = aufgabe({ dueAt: um("2026-08-01", 0), dueTimeLocal: 8 * 60, nagCount: 3 });
  const ergebnis = decideNag(task, false, um(HEUTE, 12), QUIET_HOURS_DEFAULT);
  assert.equal(ergebnis.outcome, NagOutcome.POST);
  assert.equal(ergebnis.day, 4);
  assert.equal(ergebnis.stage, NagStage.LOUD);
  assert.equal(ergebnis.nagCount, 4);
  assert.equal(ergebnis.nextNagAt, um("2026-08-20", 8));
});

test("morgen_verschiebt_einen_kalendertag_und_behaelt_die_uhrzeit", () => {
  const task = aufgabe({ dueAt: um(HEUTE, 0), dueTimeLocal: 8 * 60 });
  assert.equal(postponeToTomorrow(task, um(HEUTE, 12)), um("2026-08-20", 8));
});

test("morgen_rechnet_ab_heute_wenn_der_termin_laengst_vorbei_ist", () => {
  const task = aufgabe({ dueAt: um("2026-01-01", 0), dueTimeLocal: 8 * 60 });
  assert.equal(postponeToTomorrow(task, um(HEUTE, 12)), um("2026-08-20", 8));
});

test("ab_drei_ueberfaelligen_wird_gesammelt_gemeldet", () => {
  assert.ok(!shouldGroup(Balance.NAG_GROUP_THRESHOLD - 1));
  assert.ok(shouldGroup(Balance.NAG_GROUP_THRESHOLD));
});
