import { test } from "node:test";
import assert from "node:assert/strict";

import { advance, formatRule, nextAfter, parseRule } from "../src/domain/recurrence.js";
import { isoDate } from "../src/domain/time.js";
import { tag } from "./helpers.mjs";

const iso = (day) => (day === null ? null : isoDate(day));

test("kaputte_regel_heisst_wiederholt_sich_nicht_nicht_absturz", () => {
  for (const text of [null, "", "   ", "FREQ=MONDAYS", "Unsinn", "INTERVAL=2"]) {
    assert.equal(parseRule(text), null);
  }
});

test("unbekannte_teile_werden_uebergangen_statt_abgelehnt", () => {
  const regel = parseRule("RRULE:FREQ=WEEKLY;WKST=SU;BYSETPOS=1;BYDAY=MO");
  assert.equal(regel.frequency, "WEEKLY");
  assert.deepEqual(regel.byDay, [1]);
});

test("ordnungszahl_vor_dem_wochentag_wird_auf_den_wochentag_reduziert", () => {
  assert.deepEqual(parseRule("FREQ=MONTHLY;BYDAY=2MO").byDay, [1]);
});

test("regel_ueberlebt_schreiben_und_lesen", () => {
  const text = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;COUNT=5";
  assert.equal(formatRule(parseRule(text)), text);
  assert.equal(formatRule(parseRule("FREQ=DAILY")), "FREQ=DAILY");
});

test("until_mit_zeitanteil_wird_gelesen", () => {
  assert.equal(iso(parseRule("FREQ=DAILY;UNTIL=20261231T235959Z").until), "2026-12-31");
});

test("woechentlich_springt_zum_naechsten_gewaehlten_wochentag", () => {
  const regel = parseRule("FREQ=WEEKLY;BYDAY=MO,WE,FR");
  assert.equal(iso(nextAfter(regel, tag("2026-08-17"))), "2026-08-19");
  assert.equal(iso(nextAfter(regel, tag("2026-08-21"))), "2026-08-24");
});

test("intervall_ueberspringt_ganze_wochen", () => {
  const regel = parseRule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO");
  assert.equal(iso(nextAfter(regel, tag("2026-08-17"))), "2026-08-31");
});

test("monatsende_wird_abgeschnitten_statt_uebersprungen", () => {
  assert.equal(iso(nextAfter(parseRule("FREQ=MONTHLY"), tag("2026-01-31"))), "2026-02-28");
});

test("verpasste_termine_werden_uebersprungen_und_gezaehlt", () => {
  // Zwei Wochen Urlaub: Es entsteht **eine** offene Instanz, nicht vierzehn.
  const ergebnis = advance(parseRule("FREQ=DAILY"), tag("2026-08-05"), tag("2026-08-19"));
  assert.equal(iso(ergebnis.nextDue), "2026-08-20");
  assert.equal(ergebnis.skipped, 14);
});

test("abhaken_am_termin_ueberspringt_nichts", () => {
  const ergebnis = advance(parseRule("FREQ=DAILY"), tag("2026-08-19"), tag("2026-08-19"));
  assert.equal(iso(ergebnis.nextDue), "2026-08-20");
  assert.equal(ergebnis.skipped, 0);
});

test("count_zaehlt_herunter_und_beendet_die_serie", () => {
  const eine = advance(parseRule("FREQ=DAILY;COUNT=1"), tag("2026-08-19"), tag("2026-08-19"));
  assert.equal(iso(eine.nextDue), "2026-08-20");
  assert.equal(eine.remainingRule, null);

  const zwei = advance(parseRule("FREQ=DAILY;COUNT=2"), tag("2026-08-19"), tag("2026-08-19"));
  assert.equal(zwei.remainingRule.count, 1);
});

test("until_beendet_die_serie", () => {
  const ergebnis = advance(parseRule("FREQ=DAILY;UNTIL=20260819"), tag("2026-08-19"), tag("2026-08-19"));
  assert.equal(ergebnis.nextDue, null);
  assert.ok(ergebnis.isFinished);
});
