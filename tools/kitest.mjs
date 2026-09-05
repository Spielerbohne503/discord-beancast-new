/**
 * Die Schnittstelle für die KI, gegen den echten Worker und eine echte App.
 *
 * `test/api.test.js` prüft die Wegewahl gegen einen nachgebauten Raum. Was **dort** nicht
 * geprüft werden kann, ist das Zusammenspiel: dass ein Browser einen Bestand ablegt, den
 * der Worker aufbekommt, und dass eine über die Schnittstelle angelegte Aufgabe auf dem
 * Gerät ankommt. Genau daran scheitert so etwas — nicht an der Wegewahl.
 *
 * Voraussetzung: `npx wrangler dev --port 8788` läuft.
 * Aufruf: `node tools/kitest.mjs [adresse]`
 */

import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("/opt/node22/lib/node_modules/playwright");

const ADRESSE = (process.argv[2] ?? "http://127.0.0.1:8788/").replace(/\/+$/, "");

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

// ------------------------------------------------------------------ Ein Gerät legt an

console.log("\nEin Gerät richtet sich ein");
const geraet = await neuesGeraet();
await eingeben(geraet, "Zahnarzt");
await einschalten(geraet);

const geheimnis = await geheimnisVon(geraet);
pruefe("die App zeigt ein Geheimnis", /^[A-Za-z0-9_-]{43}$/.test(geheimnis), geheimnis);

// ------------------------------------------------------------------------- Die KI liest

console.log("\nDie KI liest");
const ohneSchluessel = await api("/api/aufgaben", { schluessel: null });
pruefe("ohne Geheimnis kommt sie nicht rein", ohneSchluessel.status === 401);

const gelesen = await api("/api/aufgaben");
pruefe("mit Geheimnis bekommt sie die Aufgaben", gelesen.status === 200, String(gelesen.status));
pruefe(
  "und „Zahnarzt“ steht darin",
  (gelesen.koerper?.aufgaben ?? []).some((aufgabe) => aufgabe.titel === "Zahnarzt"),
  JSON.stringify(gelesen.koerper?.aufgaben),
);
pruefe(
  "die Listen kommen mit, damit sie eine wählen kann",
  (gelesen.koerper?.listen ?? []).some((liste) => liste.name === "Posteingang"),
);

const markdown = await api("/api/markdown");
pruefe("das Vault kommt als Markdown", markdown.status === 200 && markdown.text.startsWith("---\nquelle: petodo"));
pruefe(
  "mit einem Kästchen, das Obsidian versteht",
  /^- \[ \] Zahnarzt$/m.test(markdown.text),
  markdown.text.split("\n").slice(0, 14).join(" | "),
);

const zweimal = await api("/api/markdown");
pruefe(
  "zweimal abgeholt ist zeichengleich, bis auf den Stand",
  ohneStand(markdown.text) === ohneStand(zweimal.text),
);

const sicherung = await api("/api/sicherung");
pruefe(
  "die Sicherung ist dieselbe Datei wie aus der App",
  sicherung.koerper?.format === "petodo-backup" && Array.isArray(sicherung.koerper?.tables?.tasks),
);

// ---------------------------------------------------------------------- Die KI schreibt

console.log("\nDie KI legt an");
const angelegt = await api("/api/aufgaben", {
  methode: "POST",
  koerper: { titel: "Reifen wechseln", faellig: "2026-07-07", uhrzeit: 18 * 60 + 30, notiz: "Werkstatt anrufen" },
});
pruefe("das Anlegen geht durch", angelegt.status === 201, JSON.stringify(angelegt.koerper));

await abgleichen(geraet);
pruefe(
  "und die Aufgabe steht auf dem Gerät",
  (await zeilen(geraet)).includes("Reifen wechseln"),
  (await zeilen(geraet)).join(", "),
);
pruefe(
  "mit der Uhrzeit, die die KI gemeint hat",
  (await zeilentext(geraet, "Reifen wechseln")).includes("18:30"),
  await zeilentext(geraet, "Reifen wechseln"),
);

console.log("\nDie KI hakt ab");
const kennung = angelegt.koerper.angelegt.id;
const abgehakt = await api(`/api/aufgaben/${kennung}`, { methode: "PATCH", koerper: { erledigt: true } });
pruefe("das Abhaken geht durch", abgehakt.status === 200);

await abgleichen(geraet);
pruefe(
  "und der Haken steht auf dem Gerät",
  await istErledigt(geraet, "Reifen wechseln"),
);

// ----------------------------------------------------------------- Und wieder zurück

console.log("\nUnd andersherum");
await eingeben(geraet, "Vom Gerät aus");
await abgleichen(geraet);

const nachher = await api("/api/aufgaben");
pruefe(
  "was auf dem Gerät entsteht, sieht die KI",
  (nachher.koerper?.aufgaben ?? []).some((aufgabe) => aufgabe.titel === "Vom Gerät aus"),
  JSON.stringify((nachher.koerper?.aufgaben ?? []).map((a) => a.titel)),
);

console.log("\nWas der Schnittstelle nicht gehört");
// Gewürfelt, nicht fest: Ein festes „fremdes“ Geheimnis trifft irgendwann einen Raum, den
// ein anderer Durchlauf angelegt hat — `synctest.mjs` benutzt genau dafür `Z`×43. Dann
// steht dort etwas, der Test schlägt fehl, und der Fehler liegt nicht im Programm.
const fremd = await api("/api/aufgaben", { schluessel: zufallsGeheimnis() });
pruefe("ein fremdes Geheimnis sieht nichts", fremd.status === 401 || fremd.status === 404, String(fremd.status));
pruefe(
  "der Abgleich der Geräte antwortet weiterhin verschlüsselt",
  await abgleichIstVerschluesselt(),
);

await browser.close();

console.log(`\n${bestanden} bestanden, ${fehlgeschlagen.length} fehlgeschlagen`);
if (fehlgeschlagen.length > 0) process.exit(1);

// --------------------------------------------------------------------------- Hilfen

async function api(pfad, { methode = "GET", koerper = null, schluessel = geheimnis } = {}) {
  const antwort = await fetch(`${ADRESSE}${pfad}`, {
    method: methode,
    headers: {
      ...(schluessel === null ? {} : { Authorization: `Bearer ${schluessel}` }),
      ...(koerper === null ? {} : { "Content-Type": "application/json" }),
    },
    body: koerper === null ? undefined : JSON.stringify(koerper),
  });

  const text = await antwort.text();
  let gelesen = null;
  try {
    gelesen = JSON.parse(text);
  } catch {
    /* Markdown ist kein JSON — das ist der Normalfall, kein Fehler. */
  }
  return { status: antwort.status, text, koerper: gelesen };
}

/** 32 Byte Zufall als Base64url — dasselbe Maß wie ein echtes Geheimnis. */
function zufallsGeheimnis() {
  return Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString("base64url");
}

/** Der Stand im Vorspann ändert sich bei jedem Abruf; alles andere darf es nicht. */
function ohneStand(text) {
  return text.replace(/^stand: .*$/m, "stand: —");
}

async function abgleichIstVerschluesselt() {
  const raum = await geraet.seite.evaluate(async () => {
    const db = await new Promise((fertig) => {
      const anfrage = indexedDB.open("petodo", 2);
      anfrage.onsuccess = () => fertig(anfrage.result);
    });
    return new Promise((fertig) => {
      const anfrage = db.transaction("settings").objectStore("settings").get("syncRaum");
      anfrage.onsuccess = () => fertig(anfrage.result?.value ?? null);
    });
  });

  const antwort = await fetch(`${ADRESSE}/sync/${raum}`);
  const text = await antwort.text();
  return antwort.status === 200 && !text.includes("Zahnarzt") && text.includes('"iv"');
}

async function neuesGeraet() {
  const kontext = await browser.newContext({ locale: "de-DE", timezoneId: "Europe/Berlin" });
  const seite = await kontext.newPage();
  seite.on("pageerror", (fehler) => console.log(`  [Gerät] ${fehler.message}`));

  await seite.goto(ADRESSE, { waitUntil: "domcontentloaded" });
  await seite.waitForSelector(".rahmen");
  await seite.waitForTimeout(900);

  const dialog = seite.locator("dialog[open]");
  if ((await dialog.count()) > 0) {
    await seite.locator('dialog[open] button:has-text("Los geht")').click();
    await seite.waitForTimeout(300);
  }
  return { seite };
}

async function einschalten(geraet) {
  await klicken(geraet, "Einstellungen");
  await klicken(geraet, "Abgleich einschalten");
  await geraet.seite.waitForTimeout(1500);
  await klicken(geraet, "Heute");
}

async function geheimnisVon(geraet) {
  await klicken(geraet, "Einstellungen");
  const link = await geraet.seite.locator("input[readonly]").first().inputValue();
  await klicken(geraet, "Heute");
  return link.split("#koppeln=")[1] ?? "";
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

async function zeilentext(geraet, titel) {
  return (await geraet.seite.locator(`.zeile:has-text("${titel}")`).first().innerText()).replace(/\n/g, " ");
}

async function istErledigt(geraet, titel) {
  const klasse = await geraet.seite.locator(`.zeile:has-text("${titel}")`).first().getAttribute("class");
  return String(klasse).includes("zeile--erledigt");
}

async function klicken(geraet, text) {
  await geraet.seite.locator(`button:visible:has-text("${text}")`).first().click();
  await geraet.seite.waitForTimeout(500);
}
