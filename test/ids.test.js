import { test } from "node:test";
import assert from "node:assert/strict";

import { FractionalIndex, keyForMove, moveItem, uuid } from "../src/domain/ids.js";

test("uuid_ist_eindeutig_und_hat_die_richtige_form", () => {
  const werte = new Set();
  for (let index = 0; index < 500; index++) {
    const wert = uuid();
    assert.match(wert, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    werte.add(wert);
  }
  assert.equal(werte.size, 500);
});

test("bruchindex_liegt_immer_echt_zwischen_den_nachbarn", () => {
  let links = FractionalIndex.initial();
  let rechts = FractionalIndex.after(links);

  // Hundertmal in dieselbe Lücke schieben — das ist der Fall, der eine naive
  // Mittelwertbildung sprengt.
  for (let schritt = 0; schritt < 100; schritt++) {
    const mitte = FractionalIndex.between(links, rechts);
    assert.ok(links < mitte, `${links} < ${mitte}`);
    assert.ok(mitte < rechts, `${mitte} < ${rechts}`);
    assert.ok(FractionalIndex.isValid(mitte));
    rechts = mitte;
  }
});

test("schluessel_endet_nie_auf_null_weil_das_mehrdeutig_waere", () => {
  let schluessel = FractionalIndex.initial();
  for (let schritt = 0; schritt < 200; schritt++) {
    assert.ok(!schluessel.endsWith("0"), schluessel);
    schluessel = FractionalIndex.before(schluessel);
  }
});

test("verdrehte_nachbarn_werden_abgewiesen", () => {
  assert.throws(() => FractionalIndex.between("z", "a"));
  assert.throws(() => FractionalIndex.between("a", "a"));
  assert.throws(() => FractionalIndex.between("!", null));
});

test("beschaedigter_schluessel_kostet_die_reihenfolge_nicht_das_anlegen", () => {
  assert.ok(FractionalIndex.isValid(FractionalIndex.afterOrInitial("kaputt!")));
  assert.ok(FractionalIndex.isValid(FractionalIndex.afterOrInitial(null)));
});

test("verschieben_ergibt_einen_schluessel_an_der_zielstelle", () => {
  // `to` ist die Stelle in der **fertigen** Liste: aus a,b,c,d wird b,c,a,d.
  const keys = ["a", "b", "c", "d"];
  const neuer = keyForMove(keys, 0, 2);
  assert.ok(neuer > "c" && neuer < "d", neuer);
  assert.deepEqual(moveItem(keys, 0, 2), ["b", "c", "a", "d"]);
});

test("verschieben_auf_denselben_platz_schreibt_nichts", () => {
  assert.equal(keyForMove(["a", "b"], 1, 1), null);
  assert.equal(keyForMove(["a", "b"], 5, 0), null);
});

test("verschieben_an_den_rand_geht_in_beide_richtungen", () => {
  const keys = ["b", "c", "d"];
  assert.ok(keyForMove(keys, 2, 0) < "b");
  assert.ok(keyForMove(keys, 0, 2) > "d");
});

test("vorschau_verschiebt_das_element_ohne_die_liste_zu_verlieren", () => {
  assert.deepEqual(moveItem([1, 2, 3, 4], 0, 2), [2, 3, 1, 4]);
  assert.deepEqual(moveItem([1, 2, 3], 1, 1), [1, 2, 3]);
});
