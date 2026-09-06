import { test } from "node:test";
import assert from "node:assert/strict";

import worker from "../worker/index.js";

/**
 * Die offene Seite.
 *
 * Zwei Zusagen, und beide sind hier festgenagelt: Ohne Eintrag ist sie **aus**, und mit
 * Eintrag gibt sie das Geheimnis **jedem** heraus. Das Zweite ist keine Lücke, sondern der
 * Zweck — der Test steht hier, damit niemand es später für einen Fehler hält und zumacht.
 */
const GEHEIM = "A".repeat(43);

const ruf = (umgebung, pfad = "/offen") =>
  worker.fetch(new Request(`https://petodo.test${pfad}`), umgebung);

test("ohne_eintrag_ist_die_seite_zu", async () => {
  const antwort = await ruf({});
  assert.equal(antwort.status, 200);
  assert.deepEqual(await antwort.json(), { offen: false });
});

test("ein_leerer_eintrag_zaehlt_als_zu", async () => {
  // Sonst schaltete ein versehentlich angelegtes, leeres Secret die Seite auf.
  for (const wert of ["", "   ", null, undefined]) {
    assert.deepEqual(await (await ruf({ OEFFENTLICH: wert })).json(), { offen: false }, String(wert));
  }
});

test("was_kein_geheimnis_ist_schaltet_nichts_auf", async () => {
  for (const wert of ["ja", "true", "A".repeat(42), "A".repeat(44), `${"A".repeat(42)}!`]) {
    assert.deepEqual(await (await ruf({ OEFFENTLICH: wert })).json(), { offen: false }, wert);
  }
});

test("mit_eintrag_bekommt_es_jeder", async () => {
  // Genau das ist gewollt: kein Kopf, kein Koppeln, keine Rückfrage.
  const antwort = await ruf({ OEFFENTLICH: GEHEIM });
  assert.deepEqual(await antwort.json(), { offen: true, geheimnis: GEHEIM });
});

test("die_antwort_wird_nirgends_zwischengespeichert", async () => {
  // Wird die Seite wieder zugemacht, soll das sofort gelten.
  const antwort = await ruf({ OEFFENTLICH: GEHEIM });
  assert.equal(antwort.headers.get("Cache-Control"), "no-store");
});

test("ein_schraegstrich_am_ende_ist_derselbe_weg", async () => {
  assert.deepEqual(await (await ruf({ OEFFENTLICH: GEHEIM }, "/offen/")).json(), {
    offen: true,
    geheimnis: GEHEIM,
  });
});

test("kein_anderer_weg_gibt_das_geheimnis_heraus", async () => {
  // Der Weg heißt `/offen` und sonst nichts — eine Verwechslung mit einer Datei der
  // Webseite würde das Geheimnis an einer Stelle ausliefern, die niemand vermutet.
  let gefragt = null;
  const umgebung = {
    OEFFENTLICH: GEHEIM,
    ASSETS: {
      fetch: (anfrage) => {
        gefragt = new URL(anfrage.url).pathname;
        return new Response("seite");
      },
    },
  };

  for (const pfad of ["/", "/index.html", "/offenbar", "/src/ui/app.js"]) {
    const antwort = await ruf(umgebung, pfad);
    assert.equal(await antwort.text(), "seite", pfad);
    assert.equal(gefragt, pfad);
  }
});

test("der_abgleich_bleibt_daneben_unberuehrt", async () => {
  // Ohne Speicher sagt `/sync/…` weiterhin, was fehlt — die offene Seite ändert daran
  // nichts.
  const antwort = await worker.fetch(
    new Request(`https://petodo.test/sync/${"a".repeat(43)}`),
    { OEFFENTLICH: GEHEIM },
  );
  assert.equal(antwort.status, 501);
});
