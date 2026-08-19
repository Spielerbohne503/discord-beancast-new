import { test } from "node:test";
import assert from "node:assert/strict";

import { hasLink, links, parseLinks, plainText, shortenUrl } from "../src/domain/links.js";

test("markdown_verweis_zeigt_die_beschriftung_nicht_die_adresse", () => {
  assert.equal(plainText("Schau [das Reel](https://instagram.com/reel/x) an"), "Schau das Reel an");
});

test("adresse_mit_klammer_im_pfad_bricht_nicht_mitten_im_wort_ab", () => {
  const [verweis] = links("[Kotlin](https://de.wikipedia.org/wiki/Kotlin_(Programmiersprache))");
  assert.equal(verweis.url, "https://de.wikipedia.org/wiki/Kotlin_(Programmiersprache)");
});

test("nackte_adresse_wird_erkannt_und_gekuerzt_angezeigt", () => {
  assert.equal(
    plainText("https://www.instagram.com/reel/DbBO_fiCJm8/?igsh=MWtqano2am95"),
    "instagram.com/reel/…",
  );
});

test("satzzeichen_hinter_der_adresse_gehoert_nicht_dazu", () => {
  const [verweis] = links("Siehe https://example.org/a.");
  assert.equal(verweis.url, "https://example.org/a");
});

test("adresse_in_einer_markdown_klammer_wird_nicht_doppelt_gezaehlt", () => {
  assert.equal(links("[Titel](https://example.org)").length, 1);
});

test("nur_http_und_https_gelten_als_verweis", () => {
  assert.ok(!hasLink("ftp://example.org/datei"));
  assert.ok(!hasLink("Guten Morgen"));
  assert.ok(hasLink("http://example.org"));
});

test("kurzform_behaelt_das_erste_pfadstueck", () => {
  assert.equal(shortenUrl("https://example.org"), "example.org");
  assert.equal(shortenUrl("https://www.example.org"), "example.org");
  assert.equal(shortenUrl("https://example.org/kurz"), "example.org/kurz");
  assert.equal(shortenUrl("https://www.example.org/kurz"), "example.org/kurz");
  assert.equal(shortenUrl("https://example.org/eins/zwei"), "example.org/eins/…");
});

test("leerer_text_ergibt_genau_ein_textstueck", () => {
  assert.deepEqual(parseLinks(""), [{ kind: "text", text: "" }]);
  assert.deepEqual(parseLinks(null), [{ kind: "text", text: "" }]);
});

test("text_ohne_verweis_bleibt_wie_er_ist", () => {
  assert.equal(plainText("Milch kaufen"), "Milch kaufen");
});
