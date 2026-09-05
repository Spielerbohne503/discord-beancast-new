import { test } from "node:test";
import assert from "node:assert/strict";

import { istZone, tagIn, zeitpunktIn } from "../src/domain/zonen.js";
import { dayFromIso } from "../src/domain/time.js";

const BERLIN = "Europe/Berlin";

/** Der Zeitpunkt als UTC-Wandzeit — so lässt sich prüfen, was tatsächlich abgelegt wird. */
const utc = (millis) => new Date(millis).toISOString();

test("winterzeit_liegt_eine_stunde_vor_utc", () => {
  // 7. Januar 2026, 18:30 in Berlin ist 17:30 UTC.
  assert.equal(utc(zeitpunktIn(BERLIN, dayFromIso("2026-01-07"), 18 * 60 + 30)), "2026-01-07T17:30:00.000Z");
});

test("sommerzeit_liegt_zwei_stunden_vor_utc", () => {
  // Genau das ist der Fehler, den der Worker sonst machte: Er rechnet in UTC, und im
  // Sommer läge der Termin zwei Stunden daneben.
  assert.equal(utc(zeitpunktIn(BERLIN, dayFromIso("2026-07-07"), 18 * 60 + 30)), "2026-07-07T16:30:00.000Z");
});

test("mitternacht_bleibt_mitternacht", () => {
  assert.equal(utc(zeitpunktIn(BERLIN, dayFromIso("2026-07-07"), 0)), "2026-07-06T22:00:00.000Z");
});

test("am_tag_der_umstellung_stimmt_es_vorher_und_nachher", () => {
  // 2026 wird in Europa am 29. März vor- und am 25. Oktober zurückgestellt.
  assert.equal(utc(zeitpunktIn(BERLIN, dayFromIso("2026-03-29"), 1 * 60)), "2026-03-29T00:00:00.000Z");
  assert.equal(utc(zeitpunktIn(BERLIN, dayFromIso("2026-03-29"), 12 * 60)), "2026-03-29T10:00:00.000Z");
  assert.equal(utc(zeitpunktIn(BERLIN, dayFromIso("2026-10-25"), 12 * 60)), "2026-10-25T11:00:00.000Z");
});

test("in_utc_aendert_sich_nichts", () => {
  assert.equal(utc(zeitpunktIn("UTC", dayFromIso("2026-07-07"), 18 * 60 + 30)), "2026-07-07T18:30:00.000Z");
});

test("jenseits_der_datumsgrenze_stimmt_der_tag", () => {
  // Auckland liegt im Juli 12 Stunden vor UTC — der Termin fällt auf den Vortag in UTC.
  assert.equal(
    utc(zeitpunktIn("Pacific/Auckland", dayFromIso("2026-07-07"), 9 * 60)),
    "2026-07-06T21:00:00.000Z",
  );
});

test("hin_und_zurueck_ergibt_denselben_tag", () => {
  for (const iso of ["2026-01-07", "2026-03-29", "2026-07-07", "2026-10-25", "2026-12-31"]) {
    const tag = dayFromIso(iso);
    assert.equal(tagIn(BERLIN, zeitpunktIn(BERLIN, tag, 12 * 60)), tag, iso);
  }
});

test("eine_zone_die_es_nicht_gibt_faellt_auf", () => {
  assert.equal(istZone(BERLIN), true);
  assert.equal(istZone("UTC"), true);
  assert.equal(istZone("Europa/Berlin"), false);
  assert.equal(istZone("völliger Unfug"), false);
});
