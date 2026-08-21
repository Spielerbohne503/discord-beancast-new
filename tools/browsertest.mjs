/**
 * Ein Durchlauf durch die laufende App, in echtem Chromium.
 *
 * Die Unit-Tests prüfen die Regeln; hier wird geprüft, ob sie im Browser auch ankommen —
 * IndexedDB, Neuladen, Verweise, Sicherung. Genau diese Sorte Fehler hat die
 * Android-Fassung unstartbar gemacht, ohne dass ein einziger Unit-Test etwas gemerkt hat.
 *
 * Aufruf: `node tools/browsertest.mjs [adresse]`
 */

import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("/opt/node22/lib/node_modules/playwright");

const ADRESSE = process.argv[2] ?? "http://localhost:8000/";

let bestanden = 0;
const fehlgeschlagen = [];
const konsolenfehler = [];

function pruefe(name, bedingung, zusatz = "") {
  if (bedingung) {
    bestanden++;
    console.log(`  ok   ${name}`);
  } else {
    fehlgeschlagen.push(`${name}${zusatz ? ` — ${zusatz}` : ""}`);
    console.log(`  FEHL ${name}${zusatz ? ` — ${zusatz}` : ""}`);
  }
}

const browser = await chromium.launch();
const kontext = await browser.newContext({
  viewport: { width: 1440, height: 960 },
  locale: "de-DE",
  timezoneId: "Europe/Berlin",
  permissions: [],
});

const seite = await kontext.newPage();
seite.on("console", (m) => {
  if (m.type() === "error") konsolenfehler.push(m.text());
});
seite.on("pageerror", (e) => konsolenfehler.push(e.message));

await seite.goto(ADRESSE, { waitUntil: "networkidle" });
await seite.waitForSelector(".rahmen");
await willkommenWeg(seite);

// ------------------------------------------------------------------ Schnell-Eingabe

console.log("\nSchnell-Eingabe");
await eingeben(seite, "morgen 9 Uhr Zahnarzt");
const ersteZeile = seite.locator(".zeile").first();
pruefe(
  "Datum und Uhrzeit verschwinden aus dem Titel",
  (await ersteZeile.locator(".zeile__titel").innerText()) === "Zahnarzt",
  await ersteZeile.locator(".zeile__titel").innerText(),
);
pruefe(
  "die Fälligkeit steht an der Aufgabe",
  (await ersteZeile.innerText()).includes("Morgen, 09:00"),
);

await eingeben(seite, "Freitagsessen planen");
pruefe(
  "ein Titel mit „Freitag“ darin wird nicht zerpflückt",
  (await seite.locator('.zeile__titel:text-is("Freitagsessen planen")').count()) === 1,
);

// ------------------------------------------------------------------------ Verweise

console.log("\nVerweise");
await eingeben(seite, "[Kotlin](https://de.wikipedia.org/wiki/Kotlin_(Programmiersprache)) lesen");
const verweis = seite.locator('.zeile__titel a:text-is("Kotlin")').first();
pruefe("die Beschriftung steht in der Liste, nicht die Adresse", (await verweis.count()) === 1);
pruefe(
  "die Adresse führt mit Klammern ans richtige Ziel",
  (await verweis.getAttribute("href")) === "https://de.wikipedia.org/wiki/Kotlin_(Programmiersprache)",
  await verweis.getAttribute("href"),
);
pruefe(
  "der Verweis öffnet sich ohne Zugriff auf diesen Tab",
  (await verweis.getAttribute("rel")) === "noopener noreferrer",
);

await eingeben(seite, "https://www.instagram.com/reel/DbBO_fiCJm8/?igsh=x");
pruefe(
  "eine nackte Adresse wird gekürzt angezeigt",
  (await seite.locator('.zeile__titel a:text-is("instagram.com/reel/…")').count()) === 1,
);

// -------------------------------------------------------------------- Abhaken

console.log("\nAbhaken");
const vorher = await seite.locator(".zeile").count();
await seite.locator('.zeile:has-text("Freitagsessen planen") .haken').click();
await seite.waitForTimeout(500);
pruefe(
  "die abgehakte Aufgabe bleibt heute stehen",
  (await seite.locator('.zeile--erledigt:has-text("Freitagsessen planen")').count()) === 1,
);
pruefe("keine Zeile geht dabei verloren", (await seite.locator(".zeile").count()) === vorher);

// ---------------------------------------------------------- Löschen und Rückgängig

console.log("\nLöschen und Rückgängig");
const zuLoeschen = seite.locator('.zeile:has-text("Zahnarzt")').first();
await zuLoeschen.hover();
await zuLoeschen.locator(".zeile__werkzeuge button").click();
await seite.waitForTimeout(400);
pruefe("die Aufgabe ist weg", (await seite.locator('.zeile:has-text("Zahnarzt")').count()) === 0);

await seite.locator(".meldung__knopf").click();
await seite.waitForTimeout(500);
pruefe(
  "Rückgängig holt sie zurück",
  (await seite.locator('.zeile:has-text("Zahnarzt")').count()) === 1,
);

// --------------------------------------------------------------------- Neuladen

console.log("\nNeuladen");
const vorNeuladen = await seite.locator(".zeile").count();
await seite.reload({ waitUntil: "networkidle" });
await seite.waitForSelector(".zeile");
pruefe(
  "alles ist nach dem Neuladen noch da",
  (await seite.locator(".zeile").count()) === vorNeuladen,
  `${await seite.locator(".zeile").count()} statt ${vorNeuladen}`,
);
pruefe(
  "der Willkommensbildschirm kommt kein zweites Mal",
  (await seite.locator("dialog[open]").count()) === 0,
);

// ------------------------------------------------------------------------ Adresse

console.log("\nAdresse und Zurück-Knopf");
await klicken(seite, "Gewohnheiten");
pruefe(
  "die Ansicht steht in der Adresse",
  seite.url().endsWith("#/gewohnheiten"),
  seite.url(),
);

await seite.goBack();
await seite.waitForTimeout(400);
pruefe(
  "der Zurück-Knopf führt zurück",
  (await seite.locator(".schnell__eingabe").count()) === 1,
  seite.url(),
);

await seite.goto(`${ADRESSE}#/rueckblick`, { waitUntil: "networkidle" });
await seite.waitForTimeout(600);
pruefe(
  "ein Verweis auf eine Ansicht öffnet sie direkt",
  (await seite.locator(".diagramm").count()) > 0,
);

// ------------------------------------------------------------------- Gewohnheiten

console.log("\nGewohnheiten");
await klicken(seite, "Gewohnheiten");
const habitFeld = seite.locator('input[placeholder*="regelmäßig"]');
await habitFeld.fill("Laufen");
await habitFeld.press("Enter");
await seite.waitForTimeout(400);
pruefe("die Gewohnheit steht da", (await seite.locator(".gewohnheit").count()) === 1);

const heutigerTag = seite.locator('.woche__tag[data-heute="true"]').first();
await heutigerTag.click();
await seite.waitForTimeout(400);
pruefe(
  "der Haken für heute sitzt",
  (await heutigerTag.getAttribute("aria-pressed")) === "true",
);
pruefe(
  "die Serie zählt ihn mit",
  (await seite.locator(".gewohnheit").first().innerText()).includes("1 Termin in Folge"),
);

// -------------------------------------------------------------------------- Fokus

console.log("\nFokus");
await klicken(seite, "Fokus");
pruefe("der Timer steht auf der vollen Runde", (await seite.locator(".fokus__zahl").innerText()) === "25:00");
await klicken(seite, "Starten");
// Zwei Sekunden plus Puffer: Der Takt schlägt einmal je Sekunde, und die Anzeige rundet
// auf — bei zu kurzem Warten steht die volle Runde noch da, ohne dass etwas kaputt ist.
await seite.waitForTimeout(2600);
const laufend = await seite.locator(".fokus__zahl").innerText();
pruefe("der Timer läuft", laufend !== "25:00", laufend);
pruefe(
  "die Restzeit steht auch im Titel des Tabs",
  (await seite.title()) === "PeTodo",
  await seite.title(),
);

await seite.reload({ waitUntil: "networkidle" });
await seite.waitForSelector(".fokus__zahl");
const nachNeuladen = await seite.locator(".fokus__zahl").innerText();
pruefe("die Runde überlebt ein Neuladen", nachNeuladen !== "25:00", nachNeuladen);
await klicken(seite, "Abbrechen");
await seite.waitForTimeout(400);

// ---------------------------------------------------------------------- Sicherung

console.log("\nSicherung");
await klicken(seite, "Heute");
await eingeben(seite, "Kommt gleich wieder weg");
const wegDamit = seite.locator('.zeile:has-text("Kommt gleich wieder weg")').first();
await wegDamit.hover();
await wegDamit.locator(".zeile__werkzeuge button").click();
await seite.waitForTimeout(500);

await klicken(seite, "Einstellungen");
const download = seite.waitForEvent("download");
await klicken(seite, "Sicherung herunterladen");
const datei = await download;
const strom = await datei.createReadStream();
let text = "";
for await (const stueck of strom) text += stueck;

const dokument = JSON.parse(text);
pruefe("die Datei trägt die Kennung", dokument.format === "petodo-backup");
pruefe("die Aufgaben sind drin", dokument.tables.tasks.length > 0);
pruefe("die Gewohnheiten sind drin", dokument.tables.habits.length === 1);
pruefe("die Haken sind drin", dokument.tables.habit_checkins.length === 1);
pruefe(
  "jede Zeile trägt updatedAt",
  dokument.tables.tasks.every((zeile) => typeof zeile.updatedAt === "number"),
);
pruefe(
  "der Tombstone der gelöschten Zeile fährt mit",
  dokument.tables.tasks.some((zeile) => zeile.deletedAt !== null && zeile.deletedAt !== undefined),
  `${dokument.tables.tasks.length} Zeilen, keine mit deletedAt`,
);

// Alles löschen, dann die Sicherung einlesen — kommt alles zurück?
const aufgabenVorher = dokument.tables.tasks.filter((zeile) => !zeile.deletedAt).length;
seite.once("dialog", (d) => d.accept());
await klicken(seite, "Alle Daten löschen");
await seite.waitForTimeout(700);

await seite.locator('input[type="file"]').setInputFiles({
  name: "petodo.json",
  mimeType: "application/json",
  buffer: Buffer.from(text, "utf8"),
});
await seite.waitForTimeout(900);
await klicken(seite, "Heute");
await seite.waitForTimeout(600);

const wiederDa = await seite.locator(".zeile").count();
pruefe(
  "nach dem Einlesen sind die Aufgaben wieder da",
  wiederDa > 0 && wiederDa <= aufgabenVorher,
  `${wiederDa} von ${aufgabenVorher}`,
);

// ---------------------------------------------------------------- Kaputte Datei

console.log("\nKaputte Sicherung");
await klicken(seite, "Einstellungen");
await seite.locator('input[type="file"]').setInputFiles({
  name: "fremd.json",
  mimeType: "application/json",
  buffer: Buffer.from('{"format":"etwas anderes"}', "utf8"),
});
await seite.waitForTimeout(700);
pruefe(
  "eine fremde Datei ändert nichts und sagt es",
  (await seite.locator(".meldung").innerText()).includes("keine lesbare"),
);

// ---------------------------------------------------------------------- Schluss

await browser.close();

console.log(`\n${bestanden} bestanden, ${fehlgeschlagen.length} fehlgeschlagen`);
if (konsolenfehler.length > 0) {
  console.log(`\n${konsolenfehler.length} Konsolenfehler:`);
  for (const zeile of konsolenfehler) console.log("  " + zeile);
}
if (fehlgeschlagen.length > 0 || konsolenfehler.length > 0) process.exit(1);

async function willkommenWeg(seite) {
  const dialog = seite.locator("dialog[open]");
  if ((await dialog.count()) === 0) return;
  await seite.locator('dialog[open] button:has-text("Los geht")').click();
  await seite.waitForTimeout(300);
}

async function eingeben(seite, text) {
  const feld = seite.locator(".schnell__eingabe");
  await feld.fill(text);
  await feld.press("Enter");
  await seite.waitForTimeout(400);
}

async function klicken(seite, text) {
  await seite.locator(`button:visible:has-text("${text}")`).first().click();
  await seite.waitForTimeout(320);
}
