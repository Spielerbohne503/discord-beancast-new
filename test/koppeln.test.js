import { test } from "node:test";
import assert from "node:assert/strict";

import { ausKoppelLink, istGeheimnis, koppelLink } from "../src/domain/koppeln.js";

const GEHEIM = "A".repeat(43);
const ADRESSE = "https://petodo.beispiel.workers.dev";

test("was_gebaut_wurde_laesst_sich_wieder_lesen", () => {
  const gelesen = ausKoppelLink(koppelLink(ADRESSE, GEHEIM));
  assert.deepEqual(gelesen, { adresse: ADRESSE, geheimnis: GEHEIM });
});

test("ein_schraegstrich_am_ende_verdoppelt_sich_nicht", () => {
  assert.equal(koppelLink(`${ADRESSE}/`, GEHEIM), koppelLink(ADRESSE, GEHEIM));
  assert.equal(koppelLink(`${ADRESSE}///`, GEHEIM), koppelLink(ADRESSE, GEHEIM));
});

test("eingefuegtes_darf_dreckig_sein", () => {
  // So kommt ein Link aus einer Nachrichten-App zurück: mit Zeilenumbruch, mit spitzen
  // Klammern, mit einem Punkt dahinter. Wer koppeln will, soll einfügen und fertig.
  const varianten = [
    `  ${koppelLink(ADRESSE, GEHEIM)}  `,
    `<${koppelLink(ADRESSE, GEHEIM)}>`,
    `\n${koppelLink(ADRESSE, GEHEIM)}\n`,
    `${koppelLink(ADRESSE, GEHEIM)},`,
  ];

  for (const roh of varianten) {
    assert.deepEqual(ausKoppelLink(roh), { adresse: ADRESSE, geheimnis: GEHEIM }, roh);
  }
});

test("das_geheimnis_allein_reicht_die_adresse_darf_fehlen", () => {
  // Im Browser wurde der Link ohnehin schon an der richtigen Stelle geöffnet — dort ist
  // die eigene Adresse die richtige.
  assert.deepEqual(ausKoppelLink(GEHEIM), { adresse: null, geheimnis: GEHEIM });
  assert.deepEqual(ausKoppelLink(`#koppeln=${GEHEIM}`), { adresse: null, geheimnis: GEHEIM });
});

test("was_hinter_dem_geheimnis_noch_steht_gehoert_nicht_dazu", () => {
  assert.deepEqual(ausKoppelLink(`${ADRESSE}/#koppeln=${GEHEIM}&sonst=egal`), {
    adresse: ADRESSE,
    geheimnis: GEHEIM,
  });
});

test("unbrauchbares_gibt_null_statt_eines_absturzes", () => {
  const unfug = [
    "",
    "   ",
    null,
    undefined,
    "hallo",
    ADRESSE,
    `${ADRESSE}/#koppeln=zu-kurz`,
    `${ADRESSE}/#koppeln=${"A".repeat(42)}`,
    `${ADRESSE}/#koppeln=${"A".repeat(44)}`,
    `${ADRESSE}/#koppeln=${"A".repeat(42)}!`,
  ];

  for (const roh of unfug) assert.equal(ausKoppelLink(roh), null, String(roh));
});

test("eine_adresse_die_keine_ist_wird_nicht_uebernommen", () => {
  // Die Adresse ist genau die Zeichenkette, an die später Anfragen gehen. Ein eingefügtes
  // `javascript:` darf dort nie landen — auch nicht, wenn das Geheimnis stimmt.
  for (const boese of ["javascript:alert(1)", "data:text/html,x", "ftp://irgendwo.example"]) {
    const gelesen = ausKoppelLink(`${boese}/#koppeln=${GEHEIM}`);
    assert.equal(gelesen.geheimnis, GEHEIM);
    assert.equal(gelesen.adresse, null, boese);
  }
});

test("das_mass_des_geheimnisses_ist_das_des_servers", () => {
  // 32 Byte als Base64url. `worker/index.js` nimmt genau dieses Muster an — wer hier
  // lockert, bekommt dort eine Abfuhr, die niemand erklären kann.
  assert.equal(istGeheimnis("A".repeat(43)), true);
  assert.equal(istGeheimnis("A".repeat(42)), false);
  assert.equal(istGeheimnis(`${"A".repeat(42)}=`), false);
  assert.equal(istGeheimnis(`${"-_".repeat(21)}A`), true);
});
