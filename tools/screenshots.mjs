/**
 * Bildschirmfotos in echtem Chromium.
 *
 * Der Grund, warum die App jetzt eine Webseite ist: Hier lässt sie sich **wirklich
 * starten**. Der Android-Startabsturz war in dieser Umgebung nicht nachstellbar, weil kein
 * Emulator läuft — ein Browser läuft.
 *
 * Aufruf: `node tools/screenshots.mjs [adresse]`
 */

import { mkdir, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("/opt/node22/lib/node_modules/playwright");

const ADRESSE = process.argv[2] ?? "http://localhost:8000/";
const ORDNER = ".screenshots";

const ANSICHTEN = [
  { name: "telefon", breite: 414, hoehe: 896 },
  { name: "schreibtisch", breite: 1440, hoehe: 960 },
];

/**
 * Der Weg zu jeder Ansicht — auf dem Telefon anders als am Schreibtisch.
 *
 * Genau das ist der Punkt, an dem die beiden Anordnungen auseinandergehen: Was am
 * Schreibtisch in der Seitenleiste steht, liegt auf dem Telefon hinter „Mehr“.
 */
const SEITEN = {
  telefon: [
    { name: "heute", weg: [] },
    { name: "fokus", weg: ["Fokus"] },
    { name: "gewohnheiten", weg: ["Gewohnheiten"] },
    { name: "mehr", weg: ["Mehr"] },
    { name: "begleiter", weg: ["Mehr", "Begleiter"] },
    { name: "rueckblick", weg: ["Mehr", "Rückblick"] },
    { name: "einstellungen", weg: ["Mehr", "Einstellungen"] },
    { name: "listen", weg: ["Listen"] },
    { name: "liste-erledigt", weg: ["Listen", "Erledigt"] },
  ],
  schreibtisch: [
    { name: "heute", weg: [] },
    { name: "fokus", weg: ["Fokus"] },
    { name: "begleiter", weg: ["Begleiter"] },
    { name: "gewohnheiten", weg: ["Gewohnheiten"] },
    { name: "rueckblick", weg: ["Rückblick"] },
    { name: "einstellungen", weg: ["Einstellungen"] },
    { name: "listen", weg: ["Alles Offene"] },
  ],
};

const BEISPIELE = [
  "morgen 9 Uhr Zahnarzt",
  "Steuererklärung fertig machen",
  "in 3 Tagen Fahrrad zur Inspektion",
  "[Kotlin nachlesen](https://de.wikipedia.org/wiki/Kotlin_(Programmiersprache))",
  "heute abends Mama anrufen",
];

const GEWOHNHEITEN = ["Laufen", "Lesen"];

const fehler = [];

// Playwright findet Chromium über PLAYWRIGHT_BROWSERS_PATH; nichts wird nachgeladen.
const browser = await chromium.launch();
await mkdir(ORDNER, { recursive: true });

for (const ansicht of ANSICHTEN) {
  const kontext = await browser.newContext({
    viewport: { width: ansicht.breite, height: ansicht.hoehe },
    deviceScaleFactor: 2,
    locale: "de-DE",
    timezoneId: "Europe/Berlin",
  });

  const seite = await kontext.newPage();
  seite.on("console", (meldung) => {
    if (meldung.type() === "error") fehler.push(`[${ansicht.name}] ${meldung.text()}`);
  });
  seite.on("pageerror", (ausnahme) => fehler.push(`[${ansicht.name}] ${ausnahme.message}`));

  await seite.goto(ADRESSE, { waitUntil: "networkidle" });
  await seite.waitForSelector(".rahmen", { timeout: 5000 });

  await willkommenWegklicken(seite, ansicht.name);
  await beispieleAnlegen(seite);
  await gewohnheitenAnlegen(seite);

  for (const eintrag of SEITEN[ansicht.name]) {
    // Immer bei „Heute“ anfangen, sonst hängt der Weg vom vorigen Bild ab.
    if (eintrag.weg.length > 0) await klicken(seite, "Heute");
    for (const ziel of eintrag.weg) await klicken(seite, ziel);
    await seite.waitForTimeout(500);

    const breiter = await seite.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    if (breiter > 1) {
      fehler.push(`[${ansicht.name}/${eintrag.name}] Seite ist ${breiter}px zu breit`);
    }

    await seite.screenshot({
      path: `${ORDNER}/${ansicht.name}-${eintrag.name}.png`,
      fullPage: ansicht.name === "telefon",
    });
  }

  // Die dunkle Fassung: dieselbe Sprache, getauschte Rollen. Umgeschaltet wird über die
  // Einstellungen, nicht über den localStorage — der ist nur ein Vorgriff aufs erste
  // Zeichnen, die Wahrheit steht in der Datenbank und würde ihn sofort überschreiben.
  await klicken(seite, "Heute");
  for (const ziel of SEITEN[ansicht.name].find((e) => e.name === "einstellungen").weg) {
    await klicken(seite, ziel);
  }
  await klicken(seite, "Dunkel");
  await klicken(seite, "Heute");
  await seite.waitForTimeout(700);
  await seite.screenshot({
    path: `${ORDNER}/${ansicht.name}-dunkel.png`,
    fullPage: ansicht.name === "telefon",
  });

  await kontext.close();
}

await browser.close();

if (fehler.length > 0) {
  await writeFile(`${ORDNER}/fehler.txt`, fehler.join("\n"));
  console.error(`\n${fehler.length} Fehler in der Konsole:`);
  for (const zeile of fehler) console.error("  " + zeile);
  process.exit(1);
}

console.log(`Bilder in ${ORDNER}/ — keine Konsolenfehler.`);

/** Klickt den ersten **sichtbaren** Knopf mit diesem Text — die Seitenleiste ist auf dem
 * Telefon vorhanden, aber ausgeblendet. */
async function klicken(seite, text) {
  const knopf = seite.locator(`button:visible:has-text("${text}")`).first();
  if ((await knopf.count()) === 0) {
    fehler.push(`Kein sichtbarer Knopf „${text}“`);
    return;
  }
  await knopf.click();
  await seite.waitForTimeout(260);
}

/** Eine Gewohnheit mit ein paar Haken — die Wochenübersicht soll nicht leer sein. */
async function gewohnheitenAnlegen(seite) {
  await klicken(seite, "Gewohnheiten");
  if ((await seite.locator(".gewohnheit").count()) > 0) return;

  const eingabe = seite.locator('input[placeholder*="regelmäßig"]');
  for (const name of GEWOHNHEITEN) {
    await eingabe.fill(name);
    await eingabe.press("Enter");
    await seite.waitForTimeout(220);
  }

  // Die ersten Tage der Woche abhaken, damit eine Serie zu sehen ist.
  const tage = seite.locator(".gewohnheit").first().locator(".woche__tag");
  for (const index of [0, 1, 2]) {
    await tage.nth(index).click();
    await seite.waitForTimeout(160);
  }
  await klicken(seite, "Heute");
}

/** Der Willkommensbildschirm kommt beim allerersten Start — erst ablichten, dann weg. */
async function willkommenWegklicken(seite, ansichtName) {
  const dialog = seite.locator("dialog[open]");
  if ((await dialog.count()) === 0) return;

  await seite.screenshot({ path: `${ORDNER}/${ansichtName}-willkommen.png` });
  await seite.locator('dialog[open] button:has-text("Los geht")').click();
  await seite.waitForTimeout(320);
}

async function beispieleAnlegen(seite) {
  const eingabe = seite.locator(".schnell__eingabe");
  if ((await eingabe.count()) === 0) return;

  const schonDa = await seite.locator(".zeile").count();
  if (schonDa > 0) return;

  for (const text of BEISPIELE) {
    await eingabe.fill(text);
    await eingabe.press("Enter");
    await seite.waitForTimeout(160);
  }

  // Eine abhaken, damit „Heute geschafft“ nicht leer bleibt.
  const haken = seite.locator(".haken").last();
  if ((await haken.count()) > 0) {
    await haken.click();
    await seite.waitForTimeout(300);
  }
}
