import { test } from "node:test";
import assert from "node:assert/strict";

import {
  DEFAULT_FOCUS_SETTINGS, FocusPhase, FocusState, breakAfterFocus, earnsReward, endsAt,
  focusStateOf, formatRemaining, nextAfterPhase, phaseDurationMs, remainingAt, resumedEndsAt,
} from "../src/domain/focus.js";
import { Balance } from "../src/domain/balance.js";

const MINUTE = 60_000;
const START = 1_000_000;

const runde = (over = {}) => ({
  id: "f1",
  taskId: null,
  phase: FocusPhase.FOCUS,
  startedAt: START,
  endsAt: endsAt(START, FocusPhase.FOCUS),
  pausedAt: null,
  completedAt: null,
  abortedAt: null,
  ...over,
});

test("abgelaufener_endzeitpunkt_heisst_fertig_nicht_laeuft_noch", () => {
  const zustand = focusStateOf(runde(), START + 26 * MINUTE);
  assert.equal(zustand.state, FocusState.ELAPSED);
  assert.equal(zustand.remaining, 0);
});

test("ohne_sitzung_ist_der_timer_bereit", () => {
  assert.equal(focusStateOf(null, START).state, FocusState.READY);
  assert.equal(focusStateOf(runde({ completedAt: START }), START).state, FocusState.READY);
  assert.equal(focusStateOf(runde({ abortedAt: START }), START).state, FocusState.READY);
});

test("anhalten_friert_den_rest_ein_egal_wie_lange_es_dauert", () => {
  const angehalten = runde({ pausedAt: START + 10 * MINUTE });
  const nachEinerMinute = focusStateOf(angehalten, START + 11 * MINUTE);
  const nachEinerStunde = focusStateOf(angehalten, START + 70 * MINUTE);
  assert.equal(nachEinerMinute.state, FocusState.PAUSED);
  assert.equal(nachEinerMinute.remaining, nachEinerStunde.remaining);
  assert.equal(nachEinerMinute.remaining, 15 * MINUTE);
});

test("fortsetzen_schiebt_den_endzeitpunkt_um_die_standzeit_nach_hinten", () => {
  const angehalten = runde({ pausedAt: START + 10 * MINUTE });
  const jetzt = START + 70 * MINUTE;
  assert.equal(resumedEndsAt(angehalten, jetzt), jetzt + 15 * MINUTE);
});

test("ein_nicht_angehaltener_lauf_behaelt_seinen_endzeitpunkt", () => {
  const laufend = runde();
  assert.equal(resumedEndsAt(laufend, START + MINUTE), laufend.endsAt);
});

test("restzeit_wird_nie_negativ", () => {
  assert.equal(remainingAt(runde(), START + 99 * MINUTE), 0);
});

test("lange_pause_kommt_nach_der_vierten_runde", () => {
  assert.equal(breakAfterFocus(1), FocusPhase.SHORT_BREAK);
  assert.equal(breakAfterFocus(3), FocusPhase.SHORT_BREAK);
  assert.equal(breakAfterFocus(Balance.ROUNDS_BEFORE_LONG_BREAK), FocusPhase.LONG_BREAK);
  assert.equal(breakAfterFocus(2 * Balance.ROUNDS_BEFORE_LONG_BREAK), FocusPhase.LONG_BREAK);
});

test("nach_der_pause_laeuft_nichts_von_selbst_weiter", () => {
  assert.equal(nextAfterPhase(FocusPhase.SHORT_BREAK, 1).kind, "back_to_ready");
  assert.equal(nextAfterPhase(FocusPhase.LONG_BREAK, 4).kind, "back_to_ready");
  assert.equal(nextAfterPhase(FocusPhase.FOCUS, 1).kind, "start_break");
});

test("abgebrochene_runden_geben_keine_belohnung", () => {
  assert.ok(earnsReward(runde({ completedAt: START })));
  assert.ok(!earnsReward(runde({ completedAt: START, abortedAt: START })));
  assert.ok(!earnsReward(runde({ phase: FocusPhase.SHORT_BREAK, completedAt: START })));
  assert.ok(!earnsReward(runde()));
});

test("dauern_kommen_aus_balance_und_sind_nie_null", () => {
  assert.equal(phaseDurationMs(FocusPhase.FOCUS), Balance.FOCUS_MINUTES * MINUTE);
  assert.equal(phaseDurationMs(FocusPhase.LONG_BREAK), Balance.LONG_BREAK_MINUTES * MINUTE);
  assert.equal(phaseDurationMs(FocusPhase.FOCUS, { ...DEFAULT_FOCUS_SETTINGS, focusMinutes: 0 }), MINUTE);
});

test("anzeige_rundet_auf_damit_nicht_bei_null_noch_zeit_laeuft", () => {
  assert.equal(formatRemaining(25 * MINUTE), "25:00");
  assert.equal(formatRemaining(90_500), "1:31");
  assert.equal(formatRemaining(0), "0:00");
  assert.equal(formatRemaining(-5), "0:00");
});
