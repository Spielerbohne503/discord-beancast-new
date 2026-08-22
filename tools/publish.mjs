/**
 * Die Webseite für die Auslieferung zusammenlegen.
 *
 * **Das ist kein Bauschritt im eigentlichen Sinn.** Es wird nichts übersetzt und nichts
 * gebündelt — es wird kopiert. `index.html` im Wurzelverzeichnis bleibt unverändert
 * lauffähig; wer die App nur benutzen will, braucht das hier nie. Dieselbe Rolle hat
 * `webseiteKopieren` im Android-Projekt: eine Fassung der Webseite, mehrere Ziele.
 *
 * Nötig ist es, weil Cloudflare **alles** im angegebenen Verzeichnis ausliefert. Zeigt man
 * dort auf das Wurzelverzeichnis, landet auch `.git` im Netz — und damit die gesamte
 * Geschichte des Projekts unter `/.git/…`. `.assetsignore` greift an dieser Stelle nicht.
 *
 * Aufruf: `node tools/publish.mjs [ziel]`
 */

import { cp, mkdir, readdir, readFile, rm, stat } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const WURZEL = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const ZIEL = resolve(process.argv[2] ?? join(WURZEL, "dist"));

/**
 * Was zur Webseite gehört.
 *
 * Eine Liste dessen, was mitkommt — nicht eine Liste dessen, was draußen bleibt. Wer eine
 * Datei vergisst, merkt es sofort; wer eine Ausnahme vergisst, liefert versehentlich etwas
 * aus, und das merkt niemand.
 */
const MITKOMMEN = ["index.html", "icon.svg", "manifest.webmanifest", "sw.js", "_headers", "src"];

if (ZIEL === WURZEL) {
  console.error("Das Ziel darf nicht das Wurzelverzeichnis sein.");
  process.exit(1);
}

// Erst leeren: Eine Datei, die es nicht mehr gibt, soll auch im Ziel verschwinden.
await rm(ZIEL, { recursive: true, force: true });
await mkdir(ZIEL, { recursive: true });

for (const eintrag of MITKOMMEN) {
  const quelle = join(WURZEL, eintrag);
  if (!existsSync(quelle)) {
    console.error(`Fehlt: ${eintrag}`);
    process.exit(1);
  }
  await cp(quelle, join(ZIEL, eintrag), { recursive: true });
}

await vollstaendig();

console.log(`${await zaehlen(ZIEL)} Dateien in ${ZIEL}`);

/**
 * Prüft, ob wirklich alles mitgekommen ist.
 *
 * `MITKOMMEN` ist von Hand gepflegt, und eine vergessene Datei fällt sonst erst im Netz
 * auf — dort, wo man sie am wenigsten sucht. Deshalb wird der Verweisbaum verfolgt: vom
 * `index.html` über die Skripte und Stilvorlagen bis zum letzten Modulimport. Was
 * angefordert wird und nicht da ist, bricht den Lauf hier ab statt beim Nutzer.
 */
async function vollstaendig() {
  const offen = ["index.html"];
  const gesehen = new Set(offen);
  const fehlend = [];

  while (offen.length > 0) {
    const datei = offen.pop();
    const pfad = join(ZIEL, datei);

    if (!existsSync(pfad)) {
      fehlend.push(datei);
      continue;
    }
    if (!/\.(html|js|css)$/.test(datei)) continue;

    const text = await readFile(pfad, "utf8");
    for (const verweis of verweiseAus(text, datei)) {
      if (gesehen.has(verweis)) continue;
      gesehen.add(verweis);
      offen.push(verweis);
    }
  }

  if (fehlend.length > 0) {
    console.error("Wird angefordert, liegt aber nicht im Ziel:");
    for (const datei of fehlend) console.error(`  ${datei}`);
    console.error("\nGehört das in MITKOMMEN?");
    process.exit(1);
  }
}

/** Alle relativen Verweise aus HTML, JavaScript und CSS — Adressen nach außen gibt es nicht. */
function verweiseAus(text, herkunft) {
  const muster = [
    /(?:src|href)\s*=\s*["']([^"']+)["']/g, // HTML
    /(?:^|[^\w$])(?:import|export)[^"'`;]*?from\s*["']([^"']+)["']/gm, // ES-Module
    /(?:^|[^\w$])import\s*\(\s*["']([^"']+)["']/g, // Import zur Laufzeit
    /url\(\s*["']?([^"')]+)["']?\s*\)/g, // CSS
  ];

  const gefunden = [];
  for (const regex of muster) {
    for (const treffer of text.matchAll(regex)) {
      const ziel = treffer[1].split(/[?#]/)[0];
      // Nur Eigenes: Adressen, Datenblöcke und Sprungmarken gehen den Packer nichts an.
      if (ziel === "" || /^(https?:|data:|blob:|mailto:|#|\/\/)/.test(ziel)) continue;
      gefunden.push(normalisieren(ziel, herkunft));
    }
  }
  return gefunden;
}

/** Ein Verweis relativ zur Quelldatei wird zu einem Pfad relativ zum Ziel. */
function normalisieren(ziel, herkunft) {
  const basis = ziel.startsWith("/") ? "." : dirname(herkunft);
  return relative(ZIEL, resolve(ZIEL, basis, ziel.replace(/^\//, ""))).split(sep).join("/");
}

async function zaehlen(verzeichnis) {
  let anzahl = 0;
  for (const eintrag of await readdir(verzeichnis)) {
    const pfad = join(verzeichnis, eintrag);
    anzahl += (await stat(pfad)).isDirectory() ? await zaehlen(pfad) : 1;
  }
  return anzahl;
}
