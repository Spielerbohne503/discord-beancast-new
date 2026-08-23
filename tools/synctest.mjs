/**
 * Zwei Geräte, ein Abgleich.
 *
 * Zwei getrennte Browser-Kontexte sind zwei getrennte IndexedDB-Bestände — näher kommt man
 * zwei Geräten auf einem Rechner nicht. Dazwischen läuft der echte Worker.
 *
 * Geprüft wird das, woran selbstgebauter Abgleich scheitert: dass gleichzeitige Arbeit auf
 * beiden Seiten überlebt, und dass **Gelöschtes gelöscht bleibt**.
 *
 * Voraussetzung: `npx wrangler dev --port 8787` läuft. Einzurichten ist dort nichts.
 * Aufruf: `node tools/synctest.mjs [adresse]`
 */

import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("/opt/node22/lib/node_modules/playwright");

const ADRESSE = process.argv[2] ?? "http://127.0.0.1:8787/";

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

console.log("\nGerät A richtet den Abgleich ein");
const a = await geraet("A");
await eingeben(a, "Zahnarzt");
await einschalten(a);
pruefe("ein Knopf genügt — nichts zu tippen, nichts abzuwarten", await verbundenIst(a));

const link = await koppelLinkVon(a);
pruefe("A zeigt einen Koppel-Link", /#koppeln=[A-Za-z0-9_-]{43}$/.test(link), link);
pruefe("und der Link trägt die Adresse der Ablage", link.startsWith(ADRESSE.replace(/\/+$/, "")), link);

console.log("\nGerät B kommt dazu — nur über den Link");
const b = await geraet("B", link);
pruefe(
  "das Öffnen des Links koppelt von allein",
  await verbundenIst(b),
);
pruefe(
  "B bekommt dabei gleich alles",
  (await zeilen(b)).includes("Zahnarzt"),
  (await zeilen(b)).join(", "),
);
pruefe(
  "und das Geheimnis bleibt nicht in der Adresszeile stehen",
  !(await b.seite.evaluate(() => globalThis.location.hash)).includes("koppeln"),
  await b.seite.evaluate(() => globalThis.location.hash),
);

console.log("\nBeide arbeiten gleichzeitig");
await eingeben(a, "Steuer");
await eingeben(b, "Fahrrad");
await abgleichen(a);
await abgleichen(b);
await abgleichen(a);

pruefe("A hat auch was von B", (await zeilen(a)).includes("Fahrrad"), (await zeilen(a)).join(", "));
pruefe("B hat auch was von A", (await zeilen(b)).includes("Steuer"), (await zeilen(b)).join(", "));
pruefe("nichts ist verloren gegangen", (await zeilen(a)).length === 3, (await zeilen(a)).join(", "));

console.log("\nAbhaken auf einem Gerät");
await abhaken(a, "Zahnarzt");
await abgleichen(a);
await abgleichen(b);
pruefe(
  "der Haken kommt drüben an",
  await istErledigt(b, "Zahnarzt"),
);

console.log("\nLöschen bleibt gelöscht");
await loeschen(b, "Fahrrad");
await abgleichen(b);
await abgleichen(a);
pruefe("A sieht die Löschung", !(await zeilen(a)).includes("Fahrrad"), (await zeilen(a)).join(", "));

// Der Fehler, an dem es fast immer scheitert: Das alte Gerät schiebt die Zeile zurück.
await abgleichen(a);
await abgleichen(b);
pruefe("und sie kommt nicht zurück", !(await zeilen(b)).includes("Fahrrad"), (await zeilen(b)).join(", "));

console.log("\nEin fremdes Geheimnis öffnet nichts");
// Ein anderer Zufall heißt ein anderer Raum. Dass dort nichts liegt, ist der Beweis, dass
// der Raum aus dem Geheimnis kommt und nicht aus der Adresse.
const c = await geraet("C", `${ADRESSE.replace(/\/+$/, "")}/#koppeln=${"Z".repeat(43)}`);
pruefe("C sieht die fremden Aufgaben nicht", (await zeilen(c)).length === 0, (await zeilen(c)).join(", "));
pruefe(
  "und landet in einem anderen Raum",
  (await raumVon(c)) !== (await raumVon(a)),
);

console.log("\nDer Server versteht nichts von dem, was er hat");
const abgelegt = await a.seite.evaluate(async (adresse) => {
  const antwort = await fetch(adresse);
  return antwort.ok ? await antwort.text() : `HTTP ${antwort.status}`;
}, `/sync/${await raumVon(a)}`);

pruefe("was dort liegt, ist ein Umschlag", abgelegt.startsWith('{"v":1'), abgelegt.slice(0, 40));
pruefe(
  "und enthält keinen Aufgabentitel im Klartext",
  !abgelegt.includes("Zahnarzt") && !abgelegt.includes("Steuer"),
);

await browser.close();

console.log(`\n${bestanden} bestanden, ${fehlgeschlagen.length} fehlgeschlagen`);
if (fehlgeschlagen.length > 0) process.exit(1);

// --------------------------------------------------------------------------- Hilfen

async function geraet(name, adresse = ADRESSE) {
  const kontext = await browser.newContext({ locale: "de-DE", timezoneId: "Europe/Berlin" });
  const seite = await kontext.newPage();
  seite.on("pageerror", (fehler) => console.log(`  [${name}] ${fehler.message}`));

  // Nicht auf Netzruhe warten: Ein Koppel-Link stößt sofort einen Abgleich an, und der
  // hält die Leitung länger wach, als `networkidle` Geduld hat.
  await seite.goto(adresse, { waitUntil: "domcontentloaded" });
  await seite.waitForSelector(".rahmen");
  // Ein Koppel-Link richtet ein und schließt den Willkommensdialog gleich mit.
  await seite.waitForTimeout(1200);

  const dialog = seite.locator("dialog[open]");
  if ((await dialog.count()) > 0) {
    await seite.locator('dialog[open] button:has-text("Los geht")').click();
    await seite.waitForTimeout(300);
  }
  return { name, kontext, seite };
}

async function einschalten(geraet) {
  await klicken(geraet, "Einstellungen");
  await klicken(geraet, "Abgleich einschalten");
  await geraet.seite.waitForTimeout(1400);
  await klicken(geraet, "Heute");
}

async function koppelLinkVon(geraet) {
  await klicken(geraet, "Einstellungen");
  const wert = await geraet.seite.locator("input[readonly]").first().inputValue();
  await klicken(geraet, "Heute");
  return wert;
}

async function verbundenIst(geraet) {
  await klicken(geraet, "Einstellungen");
  const da = (await geraet.seite.locator('button:has-text("Jetzt abgleichen")').count()) > 0;
  await klicken(geraet, "Heute");
  return da;
}

async function abgleichen(geraet) {
  await klicken(geraet, "Einstellungen");
  await klicken(geraet, "Jetzt abgleichen");
  await geraet.seite.waitForTimeout(1400);
  await klicken(geraet, "Heute");
}

async function raumVon(geraet) {
  return geraet.seite.evaluate(async () => {
    const db = await new Promise((fertig) => {
      const anfrage = indexedDB.open("petodo");
      anfrage.onsuccess = () => fertig(anfrage.result);
    });
    return new Promise((fertig) => {
      const anfrage = db.transaction("settings").objectStore("settings").get("syncRaum");
      anfrage.onsuccess = () => fertig(anfrage.result?.value ?? "");
    });
  });
}

async function eingeben(geraet, text) {
  const feld = geraet.seite.locator(".schnell__eingabe");
  await feld.fill(text);
  await feld.press("Enter");
  await geraet.seite.waitForTimeout(400);
}

async function abhaken(geraet, titel) {
  await geraet.seite.locator(`.zeile:has-text("${titel}") .haken`).first().click();
  await geraet.seite.waitForTimeout(600);
}

async function loeschen(geraet, titel) {
  const zeile = geraet.seite.locator(`.zeile:has-text("${titel}")`).first();
  await zeile.hover();
  await zeile.locator(".zeile__werkzeuge button").last().click();
  await geraet.seite.waitForTimeout(600);
}

async function istErledigt(geraet, titel) {
  const klasse = await geraet.seite
    .locator(`.zeile:has-text("${titel}")`)
    .first()
    .getAttribute("class");
  return String(klasse).includes("zeile--erledigt");
}

async function zeilen(geraet) {
  return geraet.seite.locator(".zeile__titel").allInnerTexts();
}

async function klicken(geraet, text) {
  await geraet.seite.locator(`button:visible:has-text("${text}")`).first().click();
  await geraet.seite.waitForTimeout(320);
}
