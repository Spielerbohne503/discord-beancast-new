import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

/**
 * Reguläre Ausdrücke, die überall laufen.
 *
 * Hintergrund ist ein Fehler, der die Android-Fassung unstartbar machte: `(?U)` versteht
 * Javas Regex-Maschine, ICU auf dem Telefon nicht — das Muster ließ sich dort nicht
 * übersetzen, das Modul kam nicht hoch, die App startete nicht mehr. **Ein Test, der nur
 * das Verhalten prüft, bemerkt davon nichts**, weil er dieselbe Maschine benutzt wie der
 * Entwicklungsrechner. Dieser Test liest deshalb den Quelltext.
 *
 * Im Browser sind es zwei andere Fallen:
 *
 * - `\b` zählt nur `[A-Za-z0-9_]` als Wortzeichen. Vor „ü“ steht damit keine Wortgrenze,
 *   und `\bübermorgen\b` findet nie etwas.
 * - Rückschau (`(?<=`, `(?<!`) kennen ältere Safari-Fassungen nicht. Ein Ausdruck, der
 *   sich nicht übersetzen lässt, nimmt beim Laden das ganze Modul mit.
 */

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src");

function quellen(verzeichnis = SRC, gesammelt = []) {
  for (const eintrag of readdirSync(verzeichnis, { withFileTypes: true })) {
    const pfad = join(verzeichnis, eintrag.name);
    if (eintrag.isDirectory()) quellen(pfad, gesammelt);
    else if (eintrag.name.endsWith(".js")) {
      gesammelt.push({ name: pfad.slice(SRC.length + 1), code: readFileSync(pfad, "utf8") });
    }
  }
  return gesammelt;
}

/** Kommentare heraus — über die Falle darf man schreiben, nur nicht hineintappen. */
function ohneKommentare(code) {
  return code
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((zeile) => !zeile.trim().startsWith("//"))
    .join("\n");
}

test("kein_ascii_wortgrenzen_kuerzel_in_regulaeren_ausdruecken", () => {
  for (const { name, code } of quellen()) {
    const nackt = ohneKommentare(code);
    assert.ok(!/\\b/.test(nackt), `${name}: \\b kennt keine Umlaute — Wortgrenze ausschreiben`);
    assert.ok(!/\\B/.test(nackt), `${name}: \\B kennt keine Umlaute`);
  }
});

test("keine_rueckschau_die_aeltere_browser_nicht_uebersetzen", () => {
  for (const { name, code } of quellen()) {
    const nackt = ohneKommentare(code);
    assert.ok(!/\(\?<[=!]/.test(nackt), `${name}: Rückschau — stattdessen die Grenze mitlesen`);
  }
});

test("keine_schalter_die_nur_java_kennt", () => {
  for (const { name, code } of quellen()) {
    const nackt = ohneKommentare(code);
    assert.ok(!/\(\?U\)/.test(nackt), `${name}: (?U) kennt außerhalb von Java niemand`);
  }
});

test("unicode_klassen_stehen_nur_in_ausdruecken_mit_u_schalter", () => {
  // `\p{L}` ohne `u` ist in JavaScript schlicht der Buchstabe „p“ — still falsch statt
  // laut kaputt, und deshalb der unangenehmere Fehler.
  for (const { name, code } of quellen()) {
    for (const treffer of ohneKommentare(code).matchAll(/\/((?:[^/\\\n]|\\.)+)\/([a-z]*)/g)) {
      if (!treffer[1].includes("\\p{")) continue;
      assert.ok(treffer[2].includes("u"), `${name}: ${treffer[0]} braucht den u-Schalter`);
    }
    for (const treffer of ohneKommentare(code).matchAll(/new RegExp\(([\s\S]*?)\)/g)) {
      if (!treffer[1].includes("\\\\p{")) continue;
      assert.ok(/["'`]i?u/.test(treffer[1]) || treffer[1].includes('"iu"'), `${name}: ${treffer[0]}`);
    }
  }
});

test("es_gibt_ueberhaupt_etwas_zu_pruefen", () => {
  assert.ok(quellen().length >= 10, "src/ ist verdächtig leer");
});
