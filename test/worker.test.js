import { test } from "node:test";
import assert from "node:assert/strict";

import worker from "../worker/index.js";
import { istAbgelaufen } from "../worker/haltbarkeit.js";

/**
 * Der Ablageort, geprüft ohne Cloudflare.
 *
 * `Request`, `Response` und `crypto` gibt es in Node genauso wie im Worker — der Raum wird
 * nachgebaut. Damit läuft die Prüfung im normalen Testlauf mit, ohne Netz und ohne
 * Anmeldung, und der Server ist geprüft, bevor er irgendwo steht.
 *
 * Der Nachbau hält sich an die eine Eigenschaft, auf die es ankommt: Prüfung und Schreiben
 * liegen in **einem** Aufruf. Wäre das hier zweigeteilt, prüfte der Test etwas anderes als
 * das, was in `worker/raum.js` läuft.
 */
function raumNachbau() {
  const raeume = new Map();

  const objekt = (kennung) => ({
    lesen: async () => raeume.get(kennung) ?? null,

    ablegen: async (umschlag, { erwartet, nurNeu }) => {
      const vorhanden = raeume.get(kennung) ?? null;

      if (nurNeu && vorhanden !== null) return { fehler: "gibt es schon" };
      if (!nurNeu && erwartet === null) return { fehler: "If-Match fehlt", status: 428 };
      if (!nurNeu && erwartet !== (vorhanden?.stempel ?? null)) return { fehler: "veralteter Stand" };

      const stempel = `"${crypto.randomUUID()}"`;
      raeume.set(kennung, { umschlag, stempel });
      return { stempel };
    },

    raeumen: async () => {
      raeume.delete(kennung);
    },
  });

  return { raeume, idFromName: (name) => name, get: (id) => objekt(id) };
}

const RAUM = "a".repeat(43);
const UMSCHLAG = JSON.stringify({ v: 1, iv: "AAAA", daten: "BBBB" });

const ruf = (umgebung, pfad, optionen = {}) =>
  worker.fetch(new Request(`https://petodo.test${pfad}`, optionen), umgebung);

const ablegen = (umgebung, koerper = UMSCHLAG, kopfzeilen = { "If-None-Match": "*" }) =>
  ruf(umgebung, `/sync/${RAUM}`, { method: "PUT", body: koerper, headers: kopfzeilen });

test("ohne_speicher_sagt_der_server_das_und_stuerzt_nicht_ab", async () => {
  const antwort = await ruf({}, `/sync/${RAUM}`);
  assert.equal(antwort.status, 501);
  assert.match((await antwort.json()).hinweis, /wrangler/);
});

test("was_nicht_zum_abgleich_gehoert_holen_die_dateien_der_webseite", async () => {
  let gefragt = null;
  const umgebung = { ASSETS: { fetch: (anfrage) => { gefragt = new URL(anfrage.url).pathname; return new Response("seite"); } } };

  assert.equal(await (await ruf(umgebung, "/index.html")).text(), "seite");
  assert.equal(gefragt, "/index.html");
});

test("das_erste_geraet_findet_nichts_vor", async () => {
  const antwort = await ruf({ RAUM: raumNachbau() }, `/sync/${RAUM}`);
  assert.equal(antwort.status, 404);
});

test("abgelegtes_kommt_unveraendert_zurueck_samt_stempel", async () => {
  const umgebung = { RAUM: raumNachbau() };
  const gesetzt = await ablegen(umgebung);
  assert.equal(gesetzt.status, 200);

  const geholt = await ruf(umgebung, `/sync/${RAUM}`);
  assert.equal(await geholt.text(), UMSCHLAG);
  assert.equal(geholt.headers.get("ETag"), gesetzt.headers.get("ETag"));
});

test("wer_auf_einem_veralteten_stand_aufsetzt_bekommt_eine_abfuhr", async () => {
  // Ohne diese Prüfung überschriebe ein langsames Gerät die Arbeit eines schnellen, und
  // zwar lautlos — der schlimmste Fehler, den ein Abgleich haben kann.
  const umgebung = { RAUM: raumNachbau() };
  const erste = await ablegen(umgebung);
  const veraltet = erste.headers.get("ETag");

  const zweite = await ablegen(umgebung, UMSCHLAG, { "If-Match": veraltet });
  assert.equal(zweite.status, 200);

  const dritte = await ablegen(umgebung, UMSCHLAG, { "If-Match": veraltet });
  assert.equal(dritte.status, 412);
});

test("ohne_handhabe_wird_gar_nicht_erst_geschrieben", async () => {
  const umgebung = { RAUM: raumNachbau() };
  await ablegen(umgebung);

  const ohne = await ablegen(umgebung, UMSCHLAG, {});
  assert.equal(ohne.status, 428);
});

test("nur_neu_anlegen_scheitert_wenn_es_den_raum_schon_gibt", async () => {
  const umgebung = { RAUM: raumNachbau() };
  await ablegen(umgebung);
  assert.equal((await ablegen(umgebung)).status, 412);
});

test("die_ablage_ist_kein_allgemeiner_speicher", async () => {
  const umgebung = { RAUM: raumNachbau() };
  for (const raum of ["kurz", "", "a".repeat(44), "a".repeat(42), "ein/pfad", "a".repeat(42) + "!"]) {
    const antwort = await ruf(umgebung, `/sync/${raum}`);
    assert.notEqual(antwort.status, 200, raum);
    assert.notEqual(antwort.status, 404, raum);
  }
});

test("was_kein_umschlag_ist_wird_abgewiesen", async () => {
  const umgebung = { RAUM: raumNachbau() };
  assert.equal((await ablegen(umgebung, "kein json")).status, 400);
  assert.equal((await ablegen(umgebung, '{"v":1}')).status, 400);
  assert.equal((await ablegen(umgebung, '{"iv":1,"daten":"x"}')).status, 400);
  assert.equal(umgebung.RAUM.raeume.size, 0);
});

test("zu_grosses_wird_abgewiesen_statt_abgelegt", async () => {
  const umgebung = { RAUM: raumNachbau() };
  const riesig = JSON.stringify({ v: 1, iv: "AAAA", daten: "B".repeat(9 * 1024 * 1024) });
  assert.equal((await ablegen(umgebung, riesig)).status, 413);
  assert.equal(umgebung.RAUM.raeume.size, 0);
});

test("ein_raum_laesst_sich_wieder_raeumen", async () => {
  const umgebung = { RAUM: raumNachbau() };
  await ablegen(umgebung);
  assert.equal((await ruf(umgebung, `/sync/${RAUM}`, { method: "DELETE" })).status, 200);
  assert.equal((await ruf(umgebung, `/sync/${RAUM}`)).status, 404);
});

test("unbekannte_methoden_werden_benannt_nicht_verschluckt", async () => {
  const antwort = await ruf({ RAUM: raumNachbau() }, `/sync/${RAUM}`, { method: "POST", body: "x" });
  assert.equal(antwort.status, 405);
  assert.equal(antwort.headers.get("Allow"), "GET, PUT, DELETE, OPTIONS");
});

test("nichts_wird_zwischengespeichert", async () => {
  const umgebung = { RAUM: raumNachbau() };
  await ablegen(umgebung);
  assert.equal((await ruf(umgebung, `/sync/${RAUM}`)).headers.get("Cache-Control"), "no-store");
});

test("der_raum_faellt_von_selbst_weg_wenn_niemand_mehr_ablegt", () => {
  // Die Regel selbst, nicht ihr Nachbau: `worker/raum.js` fragt genau diese Funktion.
  const jetzt = Date.UTC(2026, 7, 23);

  assert.equal(istAbgelaufen(jetzt - 399 * 86_400_000, jetzt), false);
  assert.equal(istAbgelaufen(jetzt - 401 * 86_400_000, jetzt), true);
});

test("wer_ein_jahr_nicht_abgleicht_findet_seinen_stand_noch_vor", () => {
  // Großzügiger als die Grabsteine: Ein Gerät, das ein Jahr lag, soll nicht bei null
  // anfangen — es hat noch Tombstones, die zu diesem Stand gehören.
  const jetzt = Date.UTC(2026, 7, 23);
  assert.equal(istAbgelaufen(jetzt - 365 * 86_400_000, jetzt), false);
});

// -------------------------------------------------------- CORS für die Android-Hülle

const HUELLE = "https://appassets.androidplatform.net";

test("ohne_ursprung_bleibt_die_antwort_wie_bisher_ohne_cors_kopf", async () => {
  const antwort = await ruf({ RAUM: raumNachbau() }, `/sync/${RAUM}`);
  assert.equal(antwort.headers.get("Access-Control-Allow-Origin"), null);
});

test("ein_fremder_ursprung_bekommt_keine_freigabe", async () => {
  const antwort = await ruf(
    { RAUM: raumNachbau() },
    `/sync/${RAUM}`,
    { headers: { Origin: "https://irgendwer-sonst.example" } },
  );
  assert.equal(antwort.headers.get("Access-Control-Allow-Origin"), null);
});

test("die_huelle_bekommt_den_vorflug_beantwortet_noch_vor_der_raumpruefung", async () => {
  const antwort = await ruf(
    {},
    "/sync/kein-gueltiger-raum",
    { method: "OPTIONS", headers: { Origin: HUELLE } },
  );
  assert.equal(antwort.status, 204);
  assert.equal(antwort.headers.get("Access-Control-Allow-Origin"), HUELLE);
  assert.equal(antwort.headers.get("Access-Control-Allow-Methods"), "GET, PUT, DELETE, OPTIONS");
});

test("die_huelle_darf_den_etag_lesen_daran_haengt_der_schutz_vor_ueberschreiben", async () => {
  const umgebung = { RAUM: raumNachbau() };
  const antwort = await ruf(umgebung, `/sync/${RAUM}`, {
    method: "PUT",
    body: UMSCHLAG,
    headers: { "If-None-Match": "*", Origin: HUELLE },
  });
  assert.equal(antwort.headers.get("Access-Control-Allow-Origin"), HUELLE);
  assert.equal(antwort.headers.get("Access-Control-Expose-Headers"), "ETag");
  assert.ok(antwort.headers.get("ETag"));
});
