import { test } from "node:test";
import assert from "node:assert/strict";

import { captureShare } from "../src/domain/share.js";

test("betreff_und_adresse_ergeben_einen_verweis_mit_beschriftung", () => {
  assert.deepEqual(captureShare("Bester Song", null, "https://youtu.be/x"), {
    title: "[Bester Song](https://youtu.be/x)",
    note: null,
  });
});

test("eine_alleinstehende_adresse_bleibt_die_adresse", () => {
  assert.deepEqual(captureShare(null, "https://example.org/a"), {
    title: "https://example.org/a",
    note: null,
  });
});

test("betreff_gleich_adresse_ergibt_keinen_doppelten_verweis", () => {
  assert.deepEqual(captureShare("https://example.org", "https://example.org"), {
    title: "https://example.org",
    note: null,
  });
});

test("freitext_mit_betreff_der_betreff_traegt_der_rest_wird_notiz", () => {
  assert.deepEqual(captureShare("Einkauf", "Milch\nBrot"), { title: "Einkauf", note: "Milch\nBrot" });
});

test("freitext_ohne_betreff_die_erste_zeile_traegt", () => {
  assert.deepEqual(captureShare(null, "Erste Zeile\nZweite\nDritte"), {
    title: "Erste Zeile",
    note: "Zweite\nDritte",
  });
});

test("fuehrende_leerzeilen_kosten_nicht_den_titel", () => {
  assert.deepEqual(captureShare(null, "\n\n  Titel  \nRest"), { title: "Titel", note: "Rest" });
});

test("ein_titel_ist_einzeilig", () => {
  assert.equal(captureShare("Zwei\nZeilen", null).title, "Zwei Zeilen");
});

test("nichts_geteilt_ergibt_keine_aufgabe", () => {
  assert.equal(captureShare(null, null), null);
  assert.equal(captureShare("", "   "), null);
});

test("nur_ein_betreff_ergibt_eine_aufgabe_ohne_notiz", () => {
  assert.deepEqual(captureShare("Kurz notiert", null), { title: "Kurz notiert", note: null });
});
