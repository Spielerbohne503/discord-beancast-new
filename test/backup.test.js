import { test } from "node:test";
import assert from "node:assert/strict";

import {
  BACKUP_MAGIC, BACKUP_TABLES, BACKUP_VERSION, addReports, decodeBackup, encodeBackup,
  restoreReport, shouldReplace,
} from "../src/domain/backup.js";

const beispiel = {
  tasks: [{ id: "a", title: "Zahnarzt", updatedAt: 1000, deletedAt: null }],
  habits: [{ id: "h", name: "Laufen", updatedAt: 2000 }],
};

test("sicherung_ueberlebt_schreiben_und_lesen", () => {
  const gelesen = decodeBackup(encodeBackup(beispiel, 12345));
  assert.equal(gelesen.version, BACKUP_VERSION);
  assert.equal(gelesen.exportedAt, 12345);
  assert.deepEqual(gelesen.rows("tasks"), beispiel.tasks);
  assert.equal(gelesen.rowCount, 2);
});

test("jede_tabelle_steht_in_der_datei_auch_wenn_sie_leer_ist", () => {
  const gelesen = decodeBackup(encodeBackup({}, 0));
  for (const tabelle of BACKUP_TABLES) assert.deepEqual(gelesen.rows(tabelle), [], tabelle);
});

test("tombstones_und_updatedAt_stehen_mit_in_der_datei", () => {
  const text = encodeBackup({ tasks: [{ id: "a", updatedAt: 7, deletedAt: 9 }] }, 0);
  assert.match(text, /"deletedAt": 9/);
  assert.match(text, /"updatedAt": 7/);
});

test("millisekunden_zeitstempel_verlieren_keine_stelle", () => {
  const genau = 1_787_212_800_123;
  const gelesen = decodeBackup(encodeBackup({ tasks: [{ id: "a", updatedAt: genau }] }, genau));
  assert.equal(gelesen.rows("tasks")[0].updatedAt, genau);
  assert.equal(gelesen.exportedAt, genau);
});

test("eine_unlesbare_datei_darf_nichts_ueberschreiben", () => {
  assert.equal(decodeBackup("{"), null);
  assert.equal(decodeBackup(""), null);
  assert.equal(decodeBackup("[1,2,3]"), null);
  assert.equal(decodeBackup('{"format":"etwas anderes","version":1,"tables":{}}'), null);
  assert.equal(decodeBackup(`{"format":"${BACKUP_MAGIC}","version":1}`), null);
});

test("eine_neuere_fassung_wird_nicht_geraten", () => {
  assert.equal(decodeBackup(`{"format":"${BACKUP_MAGIC}","version":99,"tables":{}}`), null);
});

test("kaputte_zeilen_werden_uebergangen_die_datei_bleibt_brauchbar", () => {
  const text = `{"format":"${BACKUP_MAGIC}","version":1,"tables":{"tasks":[{"id":"a"},null,42,["x"]]}}`;
  assert.deepEqual(decodeBackup(text).rows("tasks"), [{ id: "a" }]);
});

test("last_write_wins_eine_alte_sicherung_ueberschreibt_keine_neuere_arbeit", () => {
  assert.ok(shouldReplace(5, 7));
  assert.ok(!shouldReplace(7, 5));
  assert.ok(!shouldReplace(7, 7));
  assert.ok(shouldReplace(null, 1));
  assert.ok(!shouldReplace(5, null));
});

test("bericht_laesst_sich_zusammenzaehlen", () => {
  const summe = addReports(restoreReport(1, 2, 3), restoreReport(4, 5, 6));
  assert.deepEqual(summe, { inserted: 5, updated: 7, skipped: 9, touched: 12 });
});
