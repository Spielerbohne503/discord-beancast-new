import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

/**
 * `src/domain/` ist reines JavaScript: kein Browser, keine Uhr, kein Speicher.
 *
 * Der Test liest den Quelltext statt das Verhalten — deshalb fällt er auch dann auf, wenn
 * die verbotene Stelle in einem Zweig steckt, den kein Test durchläuft. Er wird nicht
 * „repariert“, indem man ihn lockert.
 */

const DOMAIN = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "domain");

/** Alles, was einen Browser voraussetzt. */
const BROWSER = [
  "document", "window", "localStorage", "sessionStorage", "indexedDB",
  "navigator", "XMLHttpRequest", "alert", "location", "Notification",
];

/** Die Uhr wird hereingereicht, nie gelesen — sonst ist die Logik nicht prüfbar. */
const UHR = [/\bDate\.now\s*\(/, /\bnew\s+Date\s*\(\s*\)/, /\bperformance\.now\s*\(/];

function quellen() {
  return readdirSync(DOMAIN)
    .filter((name) => name.endsWith(".js"))
    .map((name) => ({ name, code: ohneKommentare(readFileSync(join(DOMAIN, name), "utf8")) }));
}

/** Kommentare heraus: Über eine verbotene Stelle darf man schreiben, nur nicht sie benutzen. */
function ohneKommentare(code) {
  return code
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((zeile) => !zeile.trim().startsWith("//"))
    .join("\n");
}

test("domain_kennt_keinen_browser", () => {
  for (const { name, code } of quellen()) {
    for (const wort of BROWSER) {
      const muster = new RegExp(`(^|[^\\w.$])${wort}\\s*[.[(]`, "m");
      assert.ok(!muster.test(code), `${name} benutzt ${wort}`);
    }
  }
});

test("domain_liest_die_uhr_nicht_selbst", () => {
  for (const { name, code } of quellen()) {
    for (const muster of UHR) {
      assert.ok(!muster.test(code), `${name} liest die Systemuhr (${muster})`);
    }
  }
});

test("domain_zieht_nichts_von_ausserhalb_herein", () => {
  for (const { name, code } of quellen()) {
    for (const treffer of code.matchAll(/from\s+["']([^"']+)["']/g)) {
      const pfad = treffer[1];
      assert.ok(
        pfad.startsWith("./") && !pfad.includes(".."),
        `${name} importiert ${pfad} — domain/ steht für sich allein`,
      );
    }
  }
});

test("es_gibt_ueberhaupt_etwas_zu_pruefen", () => {
  // Ohne diese Zusicherung wäre ein leeres Verzeichnis ein grüner Test.
  assert.ok(quellen().length >= 10, "domain/ ist verdächtig leer");
});
