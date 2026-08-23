/**
 * Der Vertrag zwischen Webseite und Android-Hülle, geprüft im Browser.
 *
 * Die Hülle selbst lässt sich hier nicht starten — für einen Emulator fehlt KVM. Was sich
 * prüfen lässt, ist die Hälfte, die in JavaScript steht: ob der Zeitplan stimmt, ob
 * abgehakte Aufgaben aus dem Wecker verschwinden und ob eine an der Meldung angetippte
 * Handlung beim nächsten Öffnen richtig ankommt.
 *
 * Genau diese Sorte Prüfung hat in der Android-Fassung gefehlt: Der Startabsturz lag in
 * einer Zeile, die kein Test je ausgeführt hat.
 *
 * Aufruf: `node tools/bruecketest.mjs [adresse]`
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

/**
 * Die nachgebaute Hülle.
 *
 * Sie muss **genau** das anbieten, was `Bruecke.kt` anbietet — Name für Name. Weicht sie
 * ab, prüft dieser Test etwas anderes als das, was auf dem Telefon läuft, und ist wertlos.
 */
const HUELLE = `
  // Die Warteschlange muss ein Neuladen überleben — auf dem Telefon liegt sie in den
  // Einstellungen der App, hier im sessionStorage. Ohne das prüfte der Test nur, dass
  // eine frisch geleerte Liste leer ist.
  const lies = (schluessel, vorgabe) => {
    try { return JSON.parse(sessionStorage.getItem(schluessel) ?? "null") ?? vorgabe; }
    catch { return vorgabe; }
  };
  const schreib = (schluessel, wert) => sessionStorage.setItem(schluessel, JSON.stringify(wert));

  globalThis.__weckerRufe = [];
  globalThis.__hueller = {
    get aktionen() { return lies("stub.aktionen", []); },
    set aktionen(wert) { schreib("stub.aktionen", wert); },
    get quittiert() { return lies("stub.quittiert", []); },
    set quittiert(wert) { schreib("stub.quittiert", wert); },
    get gefragt() { return lies("stub.gefragt", 0); },
    set gefragt(wert) { schreib("stub.gefragt", wert); },
    get erlaubt() { return lies("stub.erlaubt", true); },
    set erlaubt(wert) { schreib("stub.erlaubt", wert); },
    get gesichert() { return lies("stub.gesichert", null); },
    set gesichert(wert) { schreib("stub.gesichert", wert); },
  };

  globalThis.Petodo = {
    zeitplanSetzen: (json) => { globalThis.__weckerRufe.push(JSON.parse(json)); },
    offeneAktionen: () => JSON.stringify(globalThis.__hueller.aktionen),
    aktionenErledigt: (json) => {
      const ids = JSON.parse(json);
      globalThis.__hueller.quittiert = [...globalThis.__hueller.quittiert, ...ids];
      globalThis.__hueller.aktionen = globalThis.__hueller.aktionen.filter((a) => !ids.includes(a.id));
    },
    erinnerungenErlaubt: () => globalThis.__hueller.erlaubt,
    erlaubnisAnfragen: () => { globalThis.__hueller.gefragt = globalThis.__hueller.gefragt + 1; },
    exakteWeckerErlaubt: () => true,
    fassung: () => "1.0.0 (Test)",
    // Die echte Hülle reicht das an die Systemauswahl weiter. Hier wird nur festgehalten,
    // **dass** und **womit** sie gerufen wurde — mehr ist ohne Telefon nicht zu prüfen.
    dateiSichern: (name, inhalt) => { globalThis.__hueller.gesichert = { name, inhalt }; },
  };
`;

const browser = await chromium.launch();
const kontext = await browser.newContext({
  viewport: { width: 1440, height: 960 },
  locale: "de-DE",
  timezoneId: "Europe/Berlin",
});
await kontext.addInitScript(HUELLE);

const seite = await kontext.newPage();
seite.on("console", (m) => {
  if (m.type() === "error") konsolenfehler.push(m.text());
});
seite.on("pageerror", (e) => konsolenfehler.push(e.message));

await seite.goto(ADRESSE, { waitUntil: "networkidle" });
await seite.waitForSelector(".rahmen");
await willkommenWeg(seite);

// ------------------------------------------------------------------- Grundsätzliches

console.log("\nDie Seite erkennt die Hülle");
pruefe(
  "der Zeitplan wird beim Start gestellt",
  (await seite.evaluate(() => globalThis.__weckerRufe.length)) > 0,
);
pruefe(
  "in der Hülle wird kein Dienstarbeiter angemeldet",
  (await seite.evaluate(() => navigator.serviceWorker?.controller ?? null)) === null,
);

// ------------------------------------------------------------------------ Zeitplan

console.log("\nDer Wecker");
await eingeben(seite, "Zahnarzt");
await faelligMachen(seite, "Zahnarzt", "2020-03-05");

let plan = await letzterPlan(seite);
const zahnarzt = plan.find((eintrag) => eintrag.titel === "Zahnarzt");
pruefe("eine überfällige Aufgabe steht im Zeitplan", zahnarzt !== undefined, JSON.stringify(plan));
pruefe(
  "der Termin liegt nicht in der Vergangenheit",
  zahnarzt !== undefined && zahnarzt.at >= Date.now() - 60_000,
  zahnarzt && new Date(zahnarzt.at).toISOString(),
);
pruefe(
  "die Meldung trägt Titel und Stufe",
  zahnarzt !== undefined && typeof zahnarzt.stufe === "string" && zahnarzt.stufe.length > 0,
  JSON.stringify(zahnarzt),
);
pruefe(
  "die erste Stufe klingelt nicht",
  zahnarzt !== undefined && zahnarzt.ton === false,
  JSON.stringify(zahnarzt),
);

await eingeben(seite, "Ohne Termin");
plan = await letzterPlan(seite);
pruefe(
  "eine Aufgabe ohne Fälligkeit steht in keinem Wecker",
  plan.every((eintrag) => eintrag.titel !== "Ohne Termin"),
  JSON.stringify(plan),
);

// Eine Liste, die nie mahnt, darf auch keinen Wecker stellen.
await verschiebenNach(seite, "Zahnarzt", "Irgendwann");
plan = await letzterPlan(seite);
pruefe(
  "eine Liste ohne Mahnung stellt keinen Wecker",
  plan.every((eintrag) => eintrag.titel !== "Zahnarzt"),
  JSON.stringify(plan),
);
await verschiebenNach(seite, "Zahnarzt", "Posteingang");

await seite.locator('.zeile:has-text("Zahnarzt") .haken').first().click();
await seite.waitForTimeout(700);
plan = await letzterPlan(seite);
pruefe(
  "eine abgehakte Aufgabe klingelt nicht mehr",
  plan.every((eintrag) => eintrag.titel !== "Zahnarzt"),
  JSON.stringify(plan),
);

// ------------------------------------------------------------- Handlungen nachholen

console.log("\nAn der Meldung angetippt, während die Seite zu war");
await eingeben(seite, "Müll rausbringen");
await faelligMachen(seite, "Müll rausbringen", "2020-03-05");

const kennung = await seite.evaluate(() =>
  [...document.querySelectorAll(".zeile")]
    .find((zeile) => zeile.textContent.includes("Müll rausbringen"))
    ?.dataset.id,
);
pruefe("die Aufgabe hat eine Kennung", typeof kennung === "string" && kennung.length > 0);

// „Erledigt“ am Dienstag angetippt, App am Freitag geöffnet.
const damals = Date.now() - 3 * 86_400_000;
await seite.evaluate(
  ([id, at]) => {
    globalThis.__hueller.aktionen = [{ id: "a1", art: "erledigt", taskId: id, at }];
  },
  [kennung, damals],
);

await seite.reload({ waitUntil: "networkidle" });
await seite.waitForSelector(".zeile");
await seite.waitForTimeout(700);

pruefe(
  "die Handlung wird quittiert",
  (await seite.evaluate(() => globalThis.__hueller.quittiert)).includes("a1"),
);
pruefe(
  "die Aufgabe ist erledigt",
  (await seite.locator('.zeile:has-text("Müll rausbringen")').first().getAttribute("class")).includes(
    "zeile--erledigt",
  ),
);
pruefe(
  "und zwar rückwirkend zum Zeitpunkt des Antippens",
  (await seite.locator('.rueckblick .zeile:has-text("Müll rausbringen")').count()) === 1,
  "steht nicht im Rückblick, also mit dem falschen Datum verbucht",
);

// „Morgen“ an einer überfälligen Aufgabe.
console.log("\n„Morgen“ von der Meldung aus");
await eingeben(seite, "Fahrrad abholen");
await faelligMachen(seite, "Fahrrad abholen", "2020-03-05");
const radId = await seite.evaluate(() =>
  [...document.querySelectorAll(".zeile")]
    .find((zeile) => zeile.textContent.includes("Fahrrad abholen"))
    ?.dataset.id,
);

await seite.evaluate((id) => {
  globalThis.__hueller.aktionen = [{ id: "a2", art: "morgen", taskId: id, at: Date.now() }];
}, radId);
await seite.reload({ waitUntil: "networkidle" });
await seite.waitForSelector(".zeile");
await seite.waitForTimeout(700);

const rad = seite.locator('.zeile:has-text("Fahrrad abholen")').first();
pruefe(
  "„Morgen“ nimmt der Aufgabe die Überfälligkeit",
  (await rad.getAttribute("class")).includes("zeile--ueberfaellig") === false,
);
pruefe("und legt sie auf morgen", (await rad.innerText()).includes("Morgen"), await rad.innerText());

// „Gemahnt“ — die Kette muss weiterzählen, auch wenn niemand zugesehen hat.
console.log("\nDie Kette zählt weiter, auch bei geschlossener Seite");
await eingeben(seite, "Alte Rechnung");
await faelligMachen(seite, "Alte Rechnung", "2020-03-05");
const rechnungId = await seite.evaluate(() =>
  [...document.querySelectorAll(".zeile")]
    .find((zeile) => zeile.textContent.includes("Alte Rechnung"))
    ?.dataset.id,
);

const vorher = (await letzterPlan(seite)).find((eintrag) => eintrag.titel === "Alte Rechnung");

await seite.evaluate(
  ([id, at]) => {
    globalThis.__hueller.aktionen = [
      { id: "m1", art: "gemahnt", taskId: id, at: at - 3 * 86_400_000 },
      { id: "m2", art: "gemahnt", taskId: id, at: at - 2 * 86_400_000 },
      { id: "m3", art: "gemahnt", taskId: id, at: at - 86_400_000 },
    ];
  },
  [rechnungId, Date.now()],
);
await seite.reload({ waitUntil: "networkidle" });
await seite.waitForSelector(".zeile");
await seite.waitForTimeout(700);

const nachher = (await letzterPlan(seite)).find((eintrag) => eintrag.titel === "Alte Rechnung");
pruefe("die Aufgabe klingelt weiter", nachher !== undefined);
pruefe(
  "aber lauter als beim ersten Mal",
  vorher !== undefined && nachher !== undefined && nachher.ton === true && vorher.ton === false,
  `vorher ${JSON.stringify(vorher)} / nachher ${JSON.stringify(nachher)}`,
);
pruefe(
  "und mit einem anderen Text",
  vorher !== undefined && nachher !== undefined && nachher.stufe !== vorher.stufe,
  `${vorher?.stufe} → ${nachher?.stufe}`,
);

// ----------------------------------------------------------------------- Erlaubnis

console.log("\nDie Erlaubnis");
await klicken(seite, "Einstellungen");
pruefe(
  "die Einstellungen nennen die Fassung der Hülle",
  (await seite.locator(".gruppe").last().innerText()).includes("1.0.0 (Test)"),
);
pruefe(
  "und sagen, dass Android den Wecker stellt",
  (await erinnerungsgruppe(seite).innerText()).includes("Android"),
);

await seite.evaluate(() => {
  globalThis.__hueller.erlaubt = false;
});
await seite.reload({ waitUntil: "networkidle" });
await seite.waitForSelector(".rahmen");
await klicken(seite, "Einstellungen");
await erinnerungsgruppe(seite).locator(".schalter").first().click();
await seite.waitForTimeout(500);
pruefe(
  "beim Einschalten ohne Erlaubnis wird gefragt",
  (await seite.evaluate(() => globalThis.__hueller.gefragt)) > 0,
);

// ------------------------------------------------------------------------ Kein Abgleich

console.log("\nAbgleich in der Hülle");
pruefe(
  "der Abgleich steht wie im Browser da, mit einem Hinweis zum eigenen Ursprung",
  (await seite.locator('button:has-text("Verbinden")').count()) === 1 &&
    (await seite.locator(".einstellungen").innerText()).includes("eigenen Ursprung"),
);
pruefe(
  "die Adresse der Ablage steht von selbst offen, statt hinter einem Klapptext versteckt zu sein",
  await seite.locator(".einstellungen details[open]").isVisible(),
);

await seite.locator('.einstellungen input[type="password"]').fill("eine Losung");
await seite.locator('button:has-text("Verbinden")').click();
await seite.waitForTimeout(300);
pruefe(
  "ohne Adresse verbindet er nicht, sondern sagt warum",
  (await seite.locator(".meldung__text").count()) === 1 &&
    (await seite.locator(".meldung__text").innerText()).includes("eigenen Ursprung"),
);

// ------------------------------------------------------------------------ Sicherung

console.log("\nSicherung in der Hülle");
await seite.locator('button:has-text("Sicherung herunterladen")').click();
await seite.waitForTimeout(600);

const gesichert = await seite.evaluate(() => globalThis.__hueller.gesichert);
pruefe(
  "„Herunterladen“ reicht die Datei an die Hülle weiter, statt ins Leere zu klicken",
  gesichert !== null,
);
pruefe(
  "sie trägt einen Namen mit Datum und endet auf .json",
  Boolean(gesichert?.name?.endsWith(".json")),
  gesichert?.name,
);
pruefe(
  "und enthält die Tabellen der Sicherung, nicht bloß eine Hülse",
  (() => {
    try {
      const inhalt = JSON.parse(gesichert?.inhalt ?? "null");
      return inhalt?.version === 1 && Array.isArray(inhalt?.tables?.tasks);
    } catch {
      return false;
    }
  })(),
);

// Das Gegenstück lässt sich hier nicht klicken — eine Dateiauswahl gehört dem System.
// Geprüft wird deshalb, dass die Seite überhaupt eine anbietet: Ohne dieses Feld hätte die
// Hülle nichts, was sie über `onShowFileChooser` beantworten könnte.
pruefe(
  "„Einlesen“ hängt an einer echten Dateiauswahl",
  (await seite.locator('.einstellungen input[type="file"]').count()) === 1,
);

// -------------------------------------------------------------------------- Schluss

await browser.close();

console.log(`\n${bestanden} bestanden, ${fehlgeschlagen.length} fehlgeschlagen`);
if (konsolenfehler.length > 0) {
  console.log(`\n${konsolenfehler.length} Konsolenfehler:`);
  for (const zeile of konsolenfehler) console.log("  " + zeile);
}
if (fehlgeschlagen.length > 0 || konsolenfehler.length > 0) process.exit(1);

// --------------------------------------------------------------------------- Hilfen

/** Die Gruppe mit der Überschrift „Erinnerungen“ — nicht die, die das Wort nur erwähnt. */
function erinnerungsgruppe(seite) {
  return seite.locator('.gruppe:has(.gruppe__titel:text-is("Erinnerungen"))').first();
}

async function letzterPlan(seite) {
  return seite.evaluate(() => globalThis.__weckerRufe.at(-1) ?? []);
}

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
  await seite.waitForTimeout(420);
}

/** Ein Datum in der Vergangenheit gibt es nur über die Einzelheiten. */
async function faelligMachen(seite, titel, datum) {
  await seite.locator(`.zeile:has-text("${titel}")`).first().click();
  await seite.waitForSelector("dialog[open]");
  await seite.locator('dialog[open] input[type="date"]').fill(datum);
  await seite.locator('dialog[open] input[type="date"]').press("Enter");
  await seite.waitForTimeout(500);
  await seite.locator('dialog[open] button:has-text("Schließen")').click();
  await seite.waitForTimeout(500);
}

async function verschiebenNach(seite, titel, liste) {
  await seite.locator(`.zeile:has-text("${titel}")`).first().click();
  await seite.waitForSelector("dialog[open]");
  await seite.locator("dialog[open] select").last().selectOption({ label: liste });
  await seite.waitForTimeout(500);
  await seite.locator('dialog[open] button:has-text("Schließen")').click();
  await seite.waitForTimeout(600);
}

async function klicken(seite, text) {
  await seite.locator(`button:visible:has-text("${text}")`).first().click();
  await seite.waitForTimeout(360);
}
