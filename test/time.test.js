import { test } from "node:test";
import assert from "node:assert/strict";

import {
  dayFromIso, daysInMonth, epochDayOf, formatHhMm, isoDate, isoWeekday,
  minutesOfDay, parseHhMm, plusMonths, plusYears, startOfDay, startOfWeek, atTime, dayOf,
} from "../src/domain/time.js";

test("kalendertag_und_iso_sind_umkehrbar", () => {
  for (const iso of ["1970-01-01", "2000-02-29", "2026-08-19", "2099-12-31"]) {
    assert.equal(isoDate(dayFromIso(iso)), iso);
  }
});

test("wochentag_ist_iso_montag_ist_eins", () => {
  assert.equal(isoWeekday(dayFromIso("2026-08-17")), 1);
  assert.equal(isoWeekday(dayFromIso("2026-08-23")), 7);
});

test("wochenstart_ist_immer_der_montag", () => {
  const montag = dayFromIso("2026-08-17");
  for (let offset = 0; offset < 7; offset++) {
    assert.equal(startOfWeek(montag + offset), montag);
  }
});

test("tagesbeginn_liegt_vor_jedem_zeitpunkt_desselben_tages", () => {
  const tag = dayFromIso("2026-08-19");
  assert.ok(startOfDay(tag) <= atTime(tag, 0, 0));
  assert.equal(dayOf(atTime(tag, 23, 59)), tag);
});

test("monatsende_wird_abgeschnitten_statt_uebersprungen", () => {
  // Der 31. Januar wird im Februar zum 28. — eine Monatsaufgabe soll auch im Februar
  // erscheinen, nicht ausfallen.
  assert.equal(isoDate(plusMonths(dayFromIso("2026-01-31"), 1)), "2026-02-28");
  assert.equal(isoDate(plusMonths(dayFromIso("2024-01-31"), 1)), "2024-02-29");
  assert.equal(isoDate(plusYears(dayFromIso("2024-02-29"), 1)), "2025-02-28");
});

test("monatslaenge_kennt_schaltjahre", () => {
  assert.equal(daysInMonth(2024, 1), 29);
  assert.equal(daysInMonth(2026, 1), 28);
  assert.equal(daysInMonth(2026, 11), 31);
});

test("uhrzeiten_lesen_und_schreiben", () => {
  assert.equal(parseHhMm("08:00"), 480);
  assert.equal(parseHhMm("23:59"), 1439);
  assert.equal(formatHhMm(480), "08:00");
  assert.equal(minutesOfDay(atTime(dayFromIso("2026-08-19"), 14, 30)), 870);
});

test("unlesbare_uhrzeit_ergibt_null_statt_absturz", () => {
  for (const text of ["", "abc", "24:00", "12:60", "12", null, undefined]) {
    assert.equal(parseHhMm(text), null);
  }
});

test("epochentag_zaehlt_ab_1970", () => {
  assert.equal(epochDayOf(1970, 0, 1), 0);
});
