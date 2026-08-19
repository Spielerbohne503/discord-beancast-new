import { test } from "node:test";
import assert from "node:assert/strict";

import { parseQuickAdd } from "../src/domain/quickadd.js";
import { Balance } from "../src/domain/balance.js";
import { formatHhMm, isoDate } from "../src/domain/time.js";
import { um } from "./helpers.mjs";

/** Mittwoch, 19. August 2026, 10:00 Uhr. */
const JETZT = um("2026-08-19", 10);

const lies = (text, jetzt = JETZT) => {
  const ergebnis = parseQuickAdd(text, jetzt);
  return {
    title: ergebnis.title,
    datum: ergebnis.day === null ? null : isoDate(ergebnis.day),
    zeit: ergebnis.minutes === null ? null : formatHhMm(ergebnis.minutes),
  };
};

test("was_erkannt_wird_verschwindet_aus_dem_titel", () => {
  assert.deepEqual(lies("morgen 9 Uhr Zahnarzt"), {
    title: "Zahnarzt",
    datum: "2026-08-20",
    zeit: "09:00",
  });
});

test("umlaute_am_wortanfang_werden_erkannt", () => {
  // Der Fall, an dem `\b` scheitert: vor „ü“ steht keine ASCII-Wortgrenze.
  assert.deepEqual(lies("übermorgen Müll rausbringen"), {
    title: "Müll rausbringen",
    datum: "2026-08-21",
    zeit: null,
  });
  assert.equal(lies("nächste Woche aufräumen").datum, "2026-08-26");
  assert.equal(lies("3. März Termin").datum, "2027-03-03");
});

test("wortgrenzen_zerpfluecken_keine_titel", () => {
  assert.deepEqual(lies("Freitagsessen planen"), { title: "Freitagsessen planen", datum: null, zeit: null });
  assert.deepEqual(lies("Morgenroutine überarbeiten"), {
    title: "Morgenroutine überarbeiten",
    datum: null,
    zeit: null,
  });
});

test("wochentag_meint_immer_den_naechsten_nie_heute", () => {
  // Stichtag ist ein Mittwoch.
  assert.equal(lies("Mittwoch Sport").datum, "2026-08-26");
  assert.equal(lies("am Freitag Bericht").datum, "2026-08-21");
  assert.equal(lies("nächsten Montag Arzt").datum, "2026-08-24");
});

test("in_n_tagen_und_wochen", () => {
  assert.equal(lies("in 3 Tagen anrufen").datum, "2026-08-22");
  assert.equal(lies("in 2 Wochen Termin").datum, "2026-09-02");
  assert.equal(lies("in 1 Tag Rückruf").datum, "2026-08-20");
});

test("datum_ohne_jahr_meint_das_naechste_vorkommen", () => {
  assert.equal(lies("12.9. Steuer").datum, "2026-09-12");
  // Am 30. Dezember getippt meint „2.1.“ den Januar danach.
  assert.equal(lies("2.1. Steuer", um("2026-12-30", 10)).datum, "2027-01-02");
});

test("unmoegliche_daten_bleiben_im_titel_stehen", () => {
  assert.deepEqual(lies("31.2. Unsinn"), { title: "31.2. Unsinn", datum: null, zeit: null });
  assert.deepEqual(lies("40.13. Quatsch"), { title: "40.13. Quatsch", datum: null, zeit: null });
});

test("zweistelliges_jahr_wird_ins_jahrtausend_gehoben", () => {
  assert.equal(lies("1.9.27 Vertrag").datum, "2027-09-01");
});

test("monatsnamen_mit_und_ohne_jahr", () => {
  assert.equal(lies("3. September Geburtstag").datum, "2026-09-03");
  assert.equal(lies("12 Januar 2028 Termin").datum, "2028-01-12");
});

test("uhrzeiten_in_den_gebraeuchlichen_schreibweisen", () => {
  assert.equal(lies("Sport um 18:30").zeit, "18:30");
  assert.equal(lies("Sport 9 Uhr").zeit, "09:00");
  assert.equal(lies("Sport um 9.30 Uhr").zeit, "09:30");
  assert.equal(lies("14:30 Meeting").zeit, "14:30");
});

test("unmoegliche_uhrzeiten_bleiben_stehen", () => {
  assert.equal(lies("Treffen 25:00").zeit, null);
  assert.equal(lies("Wert 12:99 pruefen").zeit, null);
});

test("ungefaehre_tageszeiten_kommen_aus_balance", () => {
  assert.equal(lies("Mama anrufen abends").zeit, formatHhMm(Balance.VAGUE_EVENING_HOUR * 60));
  assert.equal(lies("heute mittags essen").zeit, formatHhMm(Balance.VAGUE_NOON_HOUR * 60));
  assert.equal(lies("morgens joggen").zeit, formatHhMm(Balance.VAGUE_MORNING_HOUR * 60));
});

test("uhrzeit_ohne_tag_meint_heute_solange_sie_nicht_vorbei_ist", () => {
  assert.equal(lies("14:30 Meeting").datum, "2026-08-19");
  assert.equal(lies("9 Uhr Frühstück").datum, "2026-08-20");
});

test("nichts_erkanntes_laesst_den_titel_unangetastet", () => {
  assert.deepEqual(lies("Milch kaufen"), { title: "Milch kaufen", datum: null, zeit: null });
  assert.deepEqual(lies("   "), { title: "", datum: null, zeit: null });
  assert.deepEqual(lies(""), { title: "", datum: null, zeit: null });
});

test("uebrig_gebliebene_satzzeichen_werden_aufgeraeumt", () => {
  assert.equal(lies("Zahnarzt, morgen").title, "Zahnarzt");
  assert.equal(lies("morgen – Zahnarzt").title, "Zahnarzt");
});
