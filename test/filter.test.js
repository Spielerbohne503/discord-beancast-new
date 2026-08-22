import { test } from "node:test";
import assert from "node:assert/strict";

import { Scope, filterTasks, isManuallyOrdered, listScope, scope } from "../src/domain/filter.js";
import { aufgabe, um } from "./helpers.mjs";

const HEUTE = "2026-08-19";
const JETZT = um(HEUTE, 12);

const bestand = [
  aufgabe({ id: "ueberfaellig", title: "Zahnarzt", dueAt: um("2026-08-01", 9), hasTime: true, sortKey: "c" }),
  aufgabe({ id: "bald", title: "Milch", dueAt: um("2026-08-22", 9), hasTime: true, sortKey: "b" }),
  aufgabe({ id: "spaet", title: "Steuer", dueAt: um("2026-12-01", 9), hasTime: true, sortKey: "a" }),
  aufgabe({ id: "ohne", title: "zahnpasta kaufen", sortKey: "d" }),
  aufgabe({ id: "fertig", title: "Erledigt", completedAt: um(HEUTE, 8), sortKey: "e" }),
  aufgabe({ id: "weg", title: "Gelöscht", deletedAt: JETZT, sortKey: "f" }),
  aufgabe({ id: "kind", title: "Zahnbürste", parentId: "ueberfaellig", sortKey: "g" }),
  aufgabe({ id: "andere", title: "Später mal", listId: "irgendwann", sortKey: "h" }),
];

test("geloeschtes_taucht_in_keiner_ansicht_auf", () => {
  for (const bereich of [Scope.ALL_OPEN, Scope.NEXT_SEVEN_DAYS, Scope.COMPLETED]) {
    const ids = filterTasks(bestand, scope(bereich), "", JETZT).map((t) => t.id);
    assert.ok(!ids.includes("weg"), bereich);
  }
});

test("unteraufgaben_erscheinen_nur_beim_suchen", () => {
  assert.ok(!filterTasks(bestand, scope(Scope.ALL_OPEN), "", JETZT).map((t) => t.id).includes("kind"));
  assert.ok(filterTasks(bestand, scope(Scope.ALL_OPEN), "zahn", JETZT).map((t) => t.id).includes("kind"));
});

test("sieben_tage_nimmt_ueberfaelliges_mit_es_ist_das_dringendste", () => {
  const ids = filterTasks(bestand, scope(Scope.NEXT_SEVEN_DAYS), "", JETZT).map((t) => t.id);
  assert.deepEqual(ids, ["ueberfaellig", "bald"]);
});

test("aufgaben_ohne_faelligkeit_stehen_nicht_in_den_naechsten_sieben_tagen", () => {
  const ids = filterTasks(bestand, scope(Scope.NEXT_SEVEN_DAYS), "", JETZT).map((t) => t.id);
  assert.ok(!ids.includes("ohne"));
});

test("gesucht_wird_in_titel_und_notiz_ohne_gross_und_kleinschreibung", () => {
  const mitNotiz = aufgabe({ id: "n", title: "Termin", note: "Beim ZAHNARZT nachfragen" });
  const ids = filterTasks([...bestand, mitNotiz], scope(Scope.ALL_OPEN), "zahnarzt", JETZT).map((t) => t.id);
  assert.deepEqual(ids.sort(), ["n", "ueberfaellig"]);
});

test("eine_liste_zeigt_nur_ihre_eigenen_offenen_aufgaben", () => {
  const ids = filterTasks(bestand, listScope("irgendwann"), "", JETZT).map((t) => t.id);
  assert.deepEqual(ids, ["andere"]);
});

test("erledigtes_steht_mit_dem_juengsten_oben", () => {
  const frueher = aufgabe({ id: "alt", completedAt: um("2026-08-10", 8) });
  const ids = filterTasks([...bestand, frueher], scope(Scope.COMPLETED), "", JETZT).map((t) => t.id);
  assert.deepEqual(ids, ["fertig", "alt"]);
});

test("eine_liste_behaelt_die_reihenfolge_von_hand_schlaue_listen_nicht", () => {
  const nachHand = filterTasks(bestand, listScope("inbox"), "", JETZT).map((t) => t.sortKey);
  assert.deepEqual(nachHand, [...nachHand].sort());

  const nachFaelligkeit = filterTasks(bestand, scope(Scope.ALL_OPEN), "", JETZT).map((t) => t.id);
  assert.equal(nachFaelligkeit[0], "ueberfaellig");
  // Aufgaben ohne Fälligkeit stehen hinten, untereinander nach ihrem Bruchindex.
  assert.deepEqual(nachFaelligkeit.slice(-2), ["ohne", "andere"]);
});

test("hoehere_prioritaet_steht_bei_gleicher_faelligkeit_oben", () => {
  const gleich = um("2026-08-20", 9);
  const ids = filterTasks(
    [
      aufgabe({ id: "normal", dueAt: gleich, hasTime: true, priority: 1, sortKey: "a" }),
      aufgabe({ id: "dringend", dueAt: gleich, hasTime: true, priority: 3, sortKey: "b" }),
    ],
    scope(Scope.ALL_OPEN),
    "",
    JETZT,
  ).map((t) => t.id);
  assert.deepEqual(ids, ["dringend", "normal"]);
});

test("eine_raute_sucht_nur_im_etikett", () => {
  // Ohne diese Regel fände „#haus“ auch jede Aufgabe, in deren Notiz das Wort steht — und
  // die Etikettensuche wäre unbrauchbar, sobald man sie wirklich braucht.
  const mitEtikett = aufgabe({ id: "e", title: "Fenster putzen" });
  const nurText = aufgabe({ id: "t", title: "Über Haus nachdenken", note: "haus" });
  const etiketten = new Map([["e", ["Haus"]]]);

  const bereich = scope(Scope.ALL_OPEN);
  assert.deepEqual(
    filterTasks([mitEtikett, nurText], bereich, "#haus", JETZT, etiketten).map((t) => t.id),
    ["e"],
  );
  assert.deepEqual(
    filterTasks([mitEtikett, nurText], bereich, "haus", JETZT, etiketten).map((t) => t.id).sort(),
    ["e", "t"],
  );
});

test("eine_nackte_raute_findet_alles_mit_irgendeinem_etikett", () => {
  const mit = aufgabe({ id: "m", title: "A" });
  const ohne = aufgabe({ id: "o", title: "B" });
  const gefunden = filterTasks(
    [mit, ohne],
    scope(Scope.ALL_OPEN),
    "#",
    JETZT,
    new Map([["m", ["x"]]]),
  );
  assert.deepEqual(gefunden.map((t) => t.id), ["m"]);
});

test("ohne_etiketten_verhaelt_sich_die_suche_wie_zuvor", () => {
  // Zahnarzt, zahnpasta kaufen und die Unteraufgabe Zahnbürste — beim Suchen zählt sie mit.
  const ids = filterTasks(bestand, scope(Scope.ALL_OPEN), "zahn", JETZT).map((t) => t.id);
  assert.deepEqual(ids.sort(), ["kind", "ohne", "ueberfaellig"]);
});

test("von_hand_umsortiert_wird_nur_in_einer_liste_ohne_suche", () => {
  assert.ok(isManuallyOrdered(listScope("inbox"), ""));
  assert.ok(!isManuallyOrdered(listScope("inbox"), "milch"));
  assert.ok(!isManuallyOrdered(scope(Scope.ALL_OPEN), ""));
});
