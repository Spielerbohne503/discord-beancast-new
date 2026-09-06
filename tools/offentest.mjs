/**
 * Die offene Seite, gegen den echten Worker.
 *
 * Geprüft wird die Zusage, die dahintersteht — und sie ist unbequem: **Wer die Adresse
 * aufruft, ist drin.** Kein Koppeln, kein Link, keine Rückfrage. Zwei getrennte
 * Browser-Kontexte sind zwei getrennte Geräte; dass der zweite ohne Zutun dieselben
 * Aufgaben sieht, ist genau der Punkt.
 *
 * Voraussetzung: `npx wrangler dev --port 8790 --var OEFFENTLICH:<43 Zeichen>`
 * Aufruf: `node tools/offentest.mjs [adresse] [geheimnis]`
 */

import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("/opt/node22/lib/node_modules/playwright");

const ADRESSE = (process.argv[2] ?? "http://127.0.0.1:8790/").replace(/\/+$/, "");
const GEHEIM = process.argv[3] ?? null;

let bestanden = 0;
const fehlgeschlagen = [];

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

console.log("\nDer Server gibt Auskunft");
const auskunft = await (await fetch(`${ADRESSE}/offen`)).json();
pruefe("die Seite meldet sich als offen", auskunft.offen === true, JSON.stringify(auskunft));
pruefe(
  "und nennt ein brauchbares Geheimnis",
  /^[A-Za-z0-9_-]{43}$/.test(String(auskunft.geheimnis)),
);
if (GEHEIM !== null) {
  pruefe("nämlich genau das eingetragene", auskunft.geheimnis === GEHEIM);
}

console.log("\nDas erste Gerät");
const erstes = await neuesGeraet();
pruefe(
  "es ist beim ersten Öffnen schon verbunden — ohne einen einzigen Klick",
  await istVerbunden(erstes),
);
pruefe(
  "und es wurde nicht nach dem Willkommen gefragt",
  (await erstes.seite.locator("dialog[open]").count()) === 0,
);
pruefe("die Einstellungen sagen, dass die Seite offen ist", await sagtOffen(erstes));

await eingeben(erstes, "Zahnarzt");
await abgleichen(erstes);

console.log("\nEin völlig fremder Browser");
// Eigener Kontext heißt eigene IndexedDB, eigener Zwischenspeicher, nichts geteilt. Näher
// kommt man einem fremden Gerät auf einem Rechner nicht.
const fremder = await neuesGeraet();
pruefe(
  "sieht die Aufgaben, ohne irgendetwas einzugeben",
  (await zeilen(fremder)).includes("Zahnarzt"),
  (await zeilen(fremder)).join(", "),
);

await eingeben(fremder, "Vom Fremden");
await abgleichen(fremder);
await abgleichen(erstes);
pruefe(
  "und kann sie auch ändern",
  (await zeilen(erstes)).includes("Vom Fremden"),
  (await zeilen(erstes)).join(", "),
);

console.log("\nWas trotzdem gilt");
const raum = await raumVon(erstes);
const abgelegt = await (await fetch(`${ADRESSE}/sync/${raum}`)).text();
pruefe(
  "abgelegt wird weiterhin verschlüsselt, nicht im Klartext",
  abgelegt.startsWith('{"v":1') && !abgelegt.includes("Zahnarzt"),
  abgelegt.slice(0, 60),
);

const mitLink = await neuesGeraet(`${ADRESSE}/#koppeln=${"Q".repeat(43)}`);
pruefe(
  "ein ausdrücklicher Koppel-Link schlägt die offene Seite",
  (await raumVon(mitLink)) !== raum,
);

await browser.close();

console.log(`\n${bestanden} bestanden, ${fehlgeschlagen.length} fehlgeschlagen`);
if (fehlgeschlagen.length > 0) process.exit(1);

// --------------------------------------------------------------------------- Hilfen

async function neuesGeraet(adresse = ADRESSE) {
  const kontext = await browser.newContext({ locale: "de-DE", timezoneId: "Europe/Berlin" });
  const seite = await kontext.newPage();
  seite.on("pageerror", (fehler) => console.log(`  [Gerät] ${fehler.message}`));

  await seite.goto(adresse, { waitUntil: "domcontentloaded" });
  await seite.waitForSelector(".rahmen");
  // Übernehmen und erster Abgleich laufen beim Start; danach steht der Bestand.
  await seite.waitForTimeout(2500);
  return { seite };
}

async function istVerbunden(geraet) {
  await klicken(geraet, "Einstellungen");
  const da = (await geraet.seite.locator('button:has-text("Jetzt abgleichen")').count()) > 0;
  await klicken(geraet, "Heute");
  return da;
}

async function sagtOffen(geraet) {
  await klicken(geraet, "Einstellungen");
  const text = await geraet.seite.locator(".einstellungen").innerText();
  await klicken(geraet, "Heute");
  // Nicht die Beschriftung prüfen: Die setzt die Gestaltung in Versalien. Der Fließtext
  // darunter steht so da, wie er in `strings.js` steht.
  return text.includes("sieht deine Aufgaben und kann sie ändern");
}

async function raumVon(geraet) {
  return geraet.seite.evaluate(async () => {
    const db = await new Promise((fertig) => {
      const anfrage = indexedDB.open("petodo", 2);
      anfrage.onsuccess = () => fertig(anfrage.result);
    });
    return new Promise((fertig) => {
      const anfrage = db.transaction("settings").objectStore("settings").get("syncRaum");
      anfrage.onsuccess = () => fertig(anfrage.result?.value ?? null);
    });
  });
}

async function abgleichen(geraet) {
  await klicken(geraet, "Einstellungen");
  await klicken(geraet, "Jetzt abgleichen");
  await geraet.seite.waitForTimeout(1600);
  await klicken(geraet, "Heute");
}

async function eingeben(geraet, text) {
  const feld = geraet.seite.locator(".schnell__eingabe");
  await feld.fill(text);
  await feld.press("Enter");
  await geraet.seite.waitForTimeout(600);
}

async function zeilen(geraet) {
  return geraet.seite.locator(".zeile__titel").allInnerTexts();
}

async function klicken(geraet, text) {
  await geraet.seite.locator(`button:visible:has-text("${text}")`).first().click();
  await geraet.seite.waitForTimeout(500);
}
