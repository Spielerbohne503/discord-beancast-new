import { test } from "node:test";
import assert from "node:assert/strict";

import { aufgabenVerweise, parseLinks, plainText } from "../src/domain/links.js";
import {
  Schedule, besteSerie, darfAbhaken, fortschritt, hakenInWoche, hatZiel, serie, wochenziel,
} from "../src/domain/habits.js";
import { SKINS, naechsteGestalt, skinOder, verfuegbar } from "../src/domain/skins.js";
import { dauerVon, jeAufgabe, jeListe, jeTag } from "../src/domain/zeit.js";
import { dayFromIso, isoDate, monatVerschieben, monatsRaster, startOfWeek } from "../src/domain/time.js";

// ------------------------------------------------------------- Aufgabenverweise

test("doppelte_klammern_verweisen_auf_eine_andere_aufgabe", () => {
  const stuecke = parseLinks("Erst [[Steuer sortieren]], dann weiter");
  assert.equal(stuecke[1].kind, "aufgabe");
  assert.equal(stuecke[1].titel, "Steuer sortieren");
});

test("ein_aufgabenverweis_liest_sich_in_der_liste_wie_text", () => {
  assert.equal(plainText("Erst [[Steuer]] erledigen"), "Erst Steuer erledigen");
});

test("adressen_und_aufgabenverweise_kommen_sich_nicht_ins_gehege", () => {
  const stuecke = parseLinks("[[A]] und [Kotlin](https://x.org/a) und https://y.org");
  assert.deepEqual(stuecke.map((s) => s.kind), ["aufgabe", "text", "link", "text", "link"]);
});

test("alle_verwiesenen_titel_in_reihenfolge", () => {
  assert.deepEqual(aufgabenVerweise("[[A]] x [[B]] y [nix](https://z.org)"), ["A", "B"]);
  assert.deepEqual(aufgabenVerweise("nichts hier"), []);
});

test("leere_klammern_sind_kein_verweis", () => {
  // Ein Verweis ohne Ziel ist kein Verweis, sondern zwei Klammern.
  assert.deepEqual(aufgabenVerweise("[[]] und [[ ]]"), []);
  assert.equal(plainText("[[ ]] bleibt stehen"), "[[ ]] bleibt stehen");
});

// ---------------------------------------------------------- Gewohnheiten mit Ziel

const MO = dayFromIso("2026-08-17");
const mitZiel = (target) => ({ schedule: Schedule.NONE, target });
const mitTagen = (mask) => ({ schedule: mask, target: null });

test("ein_wochenziel_darf_an_jedem_tag_abgehakt_werden", () => {
  assert.ok(darfAbhaken(mitZiel(3), MO + 5));
  assert.ok(!darfAbhaken(mitTagen(Schedule.WEEKDAYS), MO + 5));
});

test("die_laufende_woche_bricht_die_serie_nicht", () => {
  // Zwei von drei diese Woche, die zwei Wochen davor voll: Die Serie steht bei zwei.
  const haken = new Set([MO, MO + 2, MO - 7, MO - 6, MO - 5, MO - 14, MO - 13, MO - 12]);
  assert.equal(serie(mitZiel(3), haken, MO + 3), 2);

  haken.add(MO + 4);
  assert.equal(serie(mitZiel(3), haken, MO + 4), 3);
});

test("eine_verfehlte_woche_beendet_die_serie", () => {
  const haken = new Set([MO - 7, MO - 6, MO - 5, MO - 21, MO - 20, MO - 19]);
  assert.equal(serie(mitZiel(3), haken, MO + 3), 1);
});

test("wochenfortschritt_zaehlt_gegen_das_ziel", () => {
  const haken = new Set([MO, MO + 2, MO + 4]);
  assert.deepEqual(fortschritt(mitZiel(4), haken, MO + 5), { done: 3, due: 4 });
  assert.equal(hakenInWoche(haken, startOfWeek(MO)), 3);
});

test("feste_tage_rechnen_weiter_wie_bisher", () => {
  const mwf = Schedule.toggle(Schedule.toggle(Schedule.toggle(Schedule.NONE, 1), 3), 5);
  const haken = new Set([MO, MO + 2]);
  assert.deepEqual(fortschritt(mitTagen(mwf), haken, MO + 3), { done: 2, due: 3 });
  assert.equal(serie(mitTagen(mwf), haken, MO + 3), 2);
});

test("das_wochenziel_kennt_beide_arten", () => {
  assert.equal(wochenziel(mitZiel(3)), 3);
  assert.equal(wochenziel(mitTagen(Schedule.DAILY)), 7);
  assert.ok(hatZiel(mitZiel(1)));
  assert.ok(!hatZiel(mitTagen(Schedule.DAILY)));
  assert.ok(!hatZiel({ target: 0 }));
});

test("beste_serie_findet_auch_bei_wochenzielen_den_besten_abschnitt", () => {
  const haken = new Set([
    MO - 35, MO - 34, // eine Woche geschafft
    MO - 21, MO - 20, MO - 14, MO - 13, MO - 7, MO - 6, // drei Wochen in Folge
  ]);
  assert.equal(besteSerie(mitZiel(2), haken), 3);
  assert.equal(besteSerie(mitZiel(9), haken), 0);
});

// ----------------------------------------------------------------------- Skins

test("gestalten_schalten_sich_ueber_level_frei", () => {
  assert.equal(verfuegbar(1).length, 1);
  assert.ok(verfuegbar(30).length === SKINS.length);
  assert.ok(verfuegbar(5).length < verfuegbar(20).length);
});

test("eine_gesperrte_oder_unbekannte_gestalt_faellt_auf_die_erste_zurueck", () => {
  assert.equal(skinOder("gold", 2).id, SKINS[0].id);
  assert.equal(skinOder("gibtesnicht", 30).id, SKINS[0].id);
  assert.equal(skinOder("gold", 20).id, "gold");
});

test("die_naechste_gestalt_liegt_immer_ueber_dem_aktuellen_level", () => {
  const naechste = naechsteGestalt(4);
  assert.ok(naechste.abLevel > 4);
  assert.equal(naechsteGestalt(999), null);
});

// -------------------------------------------------------------------- Fokuszeit

const runde = (taskId, minuten, extra = {}) => ({
  phase: "focus",
  taskId,
  startedAt: 0,
  completedAt: minuten * 60_000,
  abortedAt: null,
  ...extra,
});

test("abgebrochene_runden_zaehlen_nicht_mit", () => {
  // Wer nach drei Minuten abbricht, hat nicht drei Minuten fokussiert gearbeitet.
  assert.equal(dauerVon(runde("a", 25)), 25 * 60_000);
  assert.equal(dauerVon(runde("a", 3, { abortedAt: 1 })), 0);
  assert.equal(dauerVon({ startedAt: 0, completedAt: null }), 0);
});

test("fokuszeit_summiert_sich_je_aufgabe", () => {
  const summe = jeAufgabe([runde("a", 25), runde("a", 25), runde("b", 10)]);
  assert.deepEqual(summe.get("a"), { millis: 50 * 60_000, runden: 2 });
  assert.deepEqual(summe.get("b"), { millis: 10 * 60_000, runden: 1 });
});

test("pausen_und_runden_ohne_aufgabe_gehoeren_zu_keinem_eintrag", () => {
  const summe = jeAufgabe([
    runde("a", 5, { phase: "short_break" }),
    runde(null, 25),
    runde("", 25),
  ]);
  assert.equal(summe.size, 0);
});

test("fokuszeit_laesst_sich_auch_nach_tagen_lesen", () => {
  const tag = dayFromIso("2026-08-19");
  const nach = jeTag([{ phase: "focus", taskId: "a", startedAt: 0, completedAt: 1_787_000_000_000 }]);
  assert.equal(nach.size, 1);
  assert.ok(typeof [...nach.keys()][0] === "number");
  assert.ok(tag > 0);
});

// ----------------------------------------------------------------- Nach Listen

test("wohin_die_zeit_geht_absteigend_nach_dauer", () => {
  const heute = dayFromIso("2026-08-19");
  const alsZeit = (tag, minuten) => tag * 86_400_000 + minuten * 60_000;

  const tasks = [
    { id: "a", listId: "arbeit", completedAt: alsZeit(heute, 10), deletedAt: null },
    { id: "b", listId: "haus", completedAt: alsZeit(heute, 20), deletedAt: null },
    { id: "c", listId: "haus", completedAt: null, deletedAt: null },
  ];
  const sitzungen = [
    { phase: "focus", taskId: "a", startedAt: 0, completedAt: alsZeit(heute, 50) },
    { phase: "focus", taskId: "b", startedAt: alsZeit(heute, 0), completedAt: alsZeit(heute, 10) },
  ];
  const listen = [
    { id: "arbeit", name: "Arbeit" },
    { id: "haus", name: "Haushalt" },
    { id: "leer", name: "Nie benutzt" },
  ];

  const verteilung = jeListe(tasks, sitzungen, listen, heute - 7, heute);
  assert.deepEqual(verteilung.map((e) => e.name), ["Arbeit", "Haushalt"]);
  assert.equal(verteilung[0].erledigt, 1);
  assert.ok(verteilung[0].millis > verteilung[1].millis);
});

test("was_ausserhalb_des_zeitraums_liegt_zaehlt_nicht", () => {
  const heute = dayFromIso("2026-08-19");
  const tasks = [{ id: "a", listId: "l", completedAt: (heute - 30) * 86_400_000, deletedAt: null }];
  assert.deepEqual(jeListe(tasks, [], [{ id: "l", name: "L" }], heute - 7, heute), []);
});

// ------------------------------------------------------------------- Kalender

test("das_monatsraster_besteht_aus_vollen_wochen", () => {
  for (const iso of ["2026-08-15", "2027-02-10", "2024-02-05", "2026-11-30"]) {
    const wochen = monatsRaster(dayFromIso(iso));
    assert.ok(wochen.every((woche) => woche.length === 7), iso);
    assert.equal(wochen[0][0].day, startOfWeek(wochen[0][0].day), iso);
  }
});

test("die_tage_der_nachbarmonate_stehen_da_und_sind_als_solche_markiert", () => {
  const wochen = monatsRaster(dayFromIso("2026-08-15"));
  assert.equal(isoDate(wochen[0][0].day), "2026-07-27");
  assert.ok(!wochen[0][0].imMonat);
  assert.ok(wochen[0][6].imMonat);
});

test("jeder_tag_des_monats_kommt_genau_einmal_vor", () => {
  const wochen = monatsRaster(dayFromIso("2026-08-15"));
  const eigene = wochen.flat().filter((eintrag) => eintrag.imMonat);
  assert.equal(eigene.length, 31);
  assert.equal(new Set(eigene.map((e) => e.day)).size, 31);
});

test("monate_verschieben_landet_immer_auf_dem_ersten", () => {
  assert.equal(isoDate(monatVerschieben(dayFromIso("2026-08-15"), 1)), "2026-09-01");
  assert.equal(isoDate(monatVerschieben(dayFromIso("2026-12-20"), 1)), "2027-01-01");
  assert.equal(isoDate(monatVerschieben(dayFromIso("2026-01-31"), -1)), "2025-12-01");
});
