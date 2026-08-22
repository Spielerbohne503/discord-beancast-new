/**
 * Die neuen Funktionen in der laufenden App.
 *
 * Gesten lassen sich nicht behaupten — man muss sie ausführen. Playwright bewegt dafür
 * einen echten Zeiger über die Zeile, statt ein Ereignis zu erfinden: Was hier durchgeht,
 * geht auch mit einem Finger durch.
 *
 * Aufruf: `node tools/featuretest.mjs [adresse]`
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
    fehlgeschlagen.push(name);
    console.log(`  FEHL ${name}${zusatz ? ` — ${zusatz}` : ""}`);
  }
}

const browser = await chromium.launch();
const kontext = await browser.newContext({
  viewport: { width: 414, height: 896 },
  hasTouch: true,
  isMobile: true,
  locale: "de-DE",
  timezoneId: "Europe/Berlin",
});

const seite = await kontext.newPage();
seite.on("console", (m) => {
  if (m.type() === "error") konsolenfehler.push(m.text());
});
seite.on("pageerror", (e) => konsolenfehler.push(e.message));

await seite.goto(ADRESSE, { waitUntil: "networkidle" });
await seite.waitForSelector(".rahmen");
await willkommenWeg();

// ---------------------------------------------------------------- Wischgesten

console.log("\nWischen");
await eingeben("Zahnarzt");
await eingeben("Steuer");

await wischen("Zahnarzt", 1);
pruefe(
  "nach rechts wischen hakt ab",
  (await klasseVon("Zahnarzt")).includes("zeile--erledigt"),
  await klasseVon("Zahnarzt"),
);

await wischen("Steuer", -1);
pruefe(
  "nach links wischen schiebt auf morgen",
  (await zeilentext("Steuer")).includes("Morgen"),
  await zeilentext("Steuer"),
);

// Ein kurzer Wisch darf nichts auslösen — sonst löst jedes Antippen mit Versatz aus.
await eingeben("Bleibt liegen");
await wischen("Bleibt liegen", 1, 30);
pruefe(
  "ein kurzer Wisch löst nichts aus",
  !(await klasseVon("Bleibt liegen")).includes("zeile--erledigt"),
);

// ------------------------------------------------------------ Mehrfachauswahl

console.log("\nMehrfachauswahl");
await langDruecken("Steuer");
pruefe("langes Drücken wählt aus", (await seite.locator(".auswahl").count()) === 1);

await seite.locator('.zeile:has-text("Bleibt liegen")').first().tap();
await seite.waitForTimeout(300);
pruefe(
  "im Auswahlmodus wählt Antippen aus statt zu öffnen",
  (await seite.locator(".zeile--gewaehlt").count()) === 2,
  `${await seite.locator(".zeile--gewaehlt").count()} gewählt`,
);
pruefe("und öffnet keine Einzelheiten", (await seite.locator("dialog[open]").count()) === 0);

await seite.locator('.auswahl button:has-text("Abhaken")').tap();
await seite.waitForTimeout(700);
pruefe(
  "mehrere auf einmal abhaken",
  (await klasseVon("Steuer")).includes("zeile--erledigt") &&
    (await klasseVon("Bleibt liegen")).includes("zeile--erledigt"),
);
pruefe("danach ist die Auswahl leer", (await seite.locator(".auswahl").count()) === 0);

await seite.locator(".meldung__knopf").tap();
await seite.waitForTimeout(700);
pruefe(
  "und Rückgängig nimmt alle zurück",
  !(await klasseVon("Steuer")).includes("zeile--erledigt"),
);

// ------------------------------------------------------------------ Etiketten

console.log("\nEtiketten");
await zuAnsicht("Etiketten");
await seite.locator('input[placeholder="Name"]').fill("haus");
await seite.locator('input[placeholder="Name"]').press("Enter");
await seite.waitForTimeout(500);
pruefe("ein Etikett lässt sich anlegen", (await seite.locator(".etikett").count()) >= 1);

await zuAnsicht("Heute");
await seite.locator('.zeile:has-text("Steuer")').first().tap();
await seite.waitForSelector("dialog[open]");
await seite.locator('dialog[open] .chip:has-text("#haus")').tap();
await seite.waitForTimeout(600);
await seite.locator('dialog[open] button:has-text("Schließen")').tap();
await seite.waitForTimeout(500);
pruefe(
  "das Etikett steht an der Aufgabe",
  (await zeilentext("Steuer")).includes("#haus"),
  await zeilentext("Steuer"),
);

await zuAnsicht("Listen");
await seite.locator('input[type="search"]').fill("#haus");
await seite.waitForTimeout(600);
pruefe(
  "die Rautensuche findet nur Etikettierte",
  (await seite.locator(".zeile").count()) === 1,
  `${await seite.locator(".zeile").count()} Treffer`,
);
await seite.locator('input[type="search"]').fill("");
await seite.waitForTimeout(400);

// ------------------------------------------------------------------- Vorlagen

console.log("\nVorlagen");
await zuAnsicht("Vorlagen");
await seite.locator('input[placeholder*="Vorlage"]').fill("Wocheneinkauf");
await seite.locator("textarea").fill("Milch\nBrot\n- Käse\n\nObst");
await seite.locator('button:has-text("Anlegen")').tap();
await seite.waitForTimeout(600);

pruefe("die Vorlage steht da", (await seite.locator(".vorlage__punkte li").count()) === 4);
pruefe(
  "leere Zeilen und Striche fallen weg",
  (await seite.locator(".vorlage__punkte li").allInnerTexts()).join(",") === "Milch,Brot,Käse,Obst",
  (await seite.locator(".vorlage__punkte li").allInnerTexts()).join(","),
);

await seite.locator('button:has-text("Übernehmen")').tap();
await seite.waitForTimeout(900);
pruefe(
  "übernommen wird eine Aufgabe mit Unteraufgaben",
  (await zeilentext("Wocheneinkauf")).includes("0/4"),
  await zeilentext("Wocheneinkauf"),
);

// ------------------------------------------------------------------- Kalender

console.log("\nKalender");
await zuAnsicht("Kalender");
pruefe("das Raster hat volle Wochen", (await seite.locator(".kalender__tag").count()) % 7 === 0);
pruefe("heute ist markiert", (await seite.locator('.kalender__tag[data-heute="true"]').count()) === 1);

const monatVorher = await seite.locator(".kalender__monat").innerText();
await seite.locator('button[aria-label="Nächster Monat"]').tap();
await seite.waitForTimeout(400);
pruefe("blättern wechselt den Monat", (await seite.locator(".kalender__monat").innerText()) !== monatVorher);

// Genau der Knopf im Kalenderkopf — „Heute“ steht auch in der (verborgenen) Seitenleiste.
await seite.locator('.kalender__kopf button:has-text("Heute")').first().tap();
await seite.waitForTimeout(400);
pruefe("„Heute“ führt zurück", (await seite.locator(".kalender__monat").innerText()) === monatVorher);

// -------------------------------------------------------------- Gewohnheiten

console.log("\nGewohnheiten mit Wochenziel");
await zuAnsicht("Gewohnheiten");
await seite.locator('input[placeholder*="regelmäßig"]').fill("Laufen");
await seite.locator('input[placeholder*="regelmäßig"]').press("Enter");
await seite.waitForTimeout(500);

await seite.locator('.chip:has-text("So oft pro Woche")').first().tap();
await seite.waitForTimeout(500);
pruefe(
  "der Rhythmus lässt sich auf ein Wochenziel stellen",
  (await seite.locator('.chip:has-text("× pro Woche")').count()) === 7,
);

await seite.locator('.chip:has-text("3× pro Woche")').first().tap();
await seite.waitForTimeout(500);
pruefe(
  "beim Wochenziel steht jeder Tag offen",
  (await seite.locator(".woche__tag:not([disabled])").count()) === 7,
  `${await seite.locator(".woche__tag:not([disabled])").count()} offen`,
);

await seite.locator('.woche__tag[data-heute="true"]').first().tap();
await seite.waitForTimeout(500);
pruefe(
  "und der Fortschritt zählt gegen das Ziel",
  (await seite.locator(".gewohnheit").first().innerText()).includes("1 von 3"),
  (await seite.locator(".gewohnheit").first().innerText()).replace(/\n/g, " | "),
);

// ------------------------------------------------------------------- Verweise

console.log("\nVerweise auf Aufgaben");
await zuAnsicht("Heute");
await eingeben("Unterlagen für [[Steuer]] suchen");
pruefe(
  "ein Verweis wird zum Knopf",
  (await seite.locator('.zeile button.verweis:has-text("Steuer")').count()) === 1,
);

await eingeben("Antwort von [[Gibt es nicht]]");
pruefe(
  "ein Verweis ins Leere bleibt Text",
  (await seite.locator(".zeile .verweis--leer").count()) === 1,
);

await seite.locator('.zeile button.verweis:has-text("Steuer")').first().tap();
await seite.waitForSelector("dialog[open]");
await seite.waitForTimeout(400);
// Der Titel steht in einem Feld — `innerText` sieht ihn nicht.
pruefe(
  "der Verweis öffnet die gemeinte Aufgabe",
  (await seite.locator("dialog[open] input").first().inputValue()) === "Steuer",
  await seite.locator("dialog[open] input").first().inputValue(),
);
await seite.locator('dialog[open] button:has-text("Schließen")').tap();
await seite.waitForTimeout(400);

// ------------------------------------------------------------------ Rückblick

console.log("\nRückblick nach Liste");
await zuAnsicht("Rückblick");
const nachListe = seite.locator('.karte:has-text("Wohin die Zeit geht")').first();
await nachListe.waitFor({ timeout: 5000 });
const verteilung = (await nachListe.innerText()).replace(/\n/g, " ");
pruefe("der Rückblick teilt nach Listen auf", verteilung.includes("Posteingang"), verteilung);
pruefe(
  "ohne Fokusrunde steht dort die Zahl der erledigten Aufgaben",
  /\d+ erledigt/.test(verteilung),
  verteilung,
);

// ------------------------------------------------------------------- Gestalten

console.log("\nGestalt und Fokuszeit");
await zuAnsicht("Einstellungen");
pruefe("es gibt Gestalten zur Auswahl", (await seite.locator(".gestalt").count()) >= 5);
pruefe(
  "höhere sind gesperrt und zeigen die Stufe",
  (await seite.locator(".gestalt:disabled").count()) >= 4,
);
pruefe("die erste ist gewählt", (await seite.locator('.gestalt[aria-pressed="true"]').count()) === 1);

await browser.close();

console.log(`\n${bestanden} bestanden, ${fehlgeschlagen.length} fehlgeschlagen`);
if (konsolenfehler.length > 0) {
  console.log(`\n${konsolenfehler.length} Konsolenfehler:`);
  for (const zeile of konsolenfehler) console.log("  " + zeile);
}
if (fehlgeschlagen.length > 0 || konsolenfehler.length > 0) process.exit(1);

// --------------------------------------------------------------------------- Hilfen

/** Ein echter Zeiger, keine erfundenen Ereignisse. */
async function wischen(titel, richtung, weite = 140) {
  const zeile = seite.locator(`.zeile:has-text("${titel}")`).first();
  const kasten = await zeile.boundingBox();
  const y = kasten.y + kasten.height / 2;
  // Aus der Mitte heraus, wie mit dem Daumen. An den Rändern sitzen Haken und Werkzeuge.
  const start = kasten.x + kasten.width / 2;

  await seite.mouse.move(start, y);
  await seite.mouse.down();
  // In Schritten, damit die Richtungsentscheidung wie beim Finger fällt.
  for (let schritt = 1; schritt <= 6; schritt++) {
    await seite.mouse.move(start + richtung * (weite / 6) * schritt, y);
    await seite.waitForTimeout(20);
  }
  await seite.mouse.up();
  await seite.waitForTimeout(800);
}

async function langDruecken(titel) {
  const zeile = seite.locator(`.zeile:has-text("${titel}")`).first();
  const kasten = await zeile.boundingBox();

  await seite.mouse.move(kasten.x + kasten.width / 2, kasten.y + kasten.height / 2);
  await seite.mouse.down();
  await seite.waitForTimeout(650);
  await seite.mouse.up();
  await seite.waitForTimeout(400);
}

async function klasseVon(titel) {
  return String(await seite.locator(`.zeile:has-text("${titel}")`).first().getAttribute("class"));
}

async function zeilentext(titel) {
  return (await seite.locator(`.zeile:has-text("${titel}")`).first().innerText()).replace(/\n/g, " ");
}

async function willkommenWeg() {
  const dialog = seite.locator("dialog[open]");
  if ((await dialog.count()) === 0) return;
  await seite.locator('dialog[open] button:has-text("Los geht")').tap();
  await seite.waitForTimeout(300);
}

async function eingeben(text) {
  const feld = seite.locator(".schnell__eingabe");
  await feld.fill(text);
  await feld.press("Enter");
  await seite.waitForTimeout(450);
}

/** Über „Mehr“, wo die Leiste unten den Punkt nicht selbst hat. */
async function zuAnsicht(name) {
  const direkt = seite.locator(`.leiste__knopf:has-text("${name}")`);
  if ((await direkt.count()) > 0) {
    await direkt.first().tap();
  } else {
    await seite.locator('.leiste__knopf:has-text("Mehr")').tap();
    await seite.waitForTimeout(320);
    await seite.locator(`button:visible:has-text("${name}")`).first().tap();
  }
  await seite.waitForTimeout(500);
}
