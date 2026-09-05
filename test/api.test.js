import { test } from "node:test";
import assert from "node:assert/strict";

import worker from "../worker/index.js";
import { ausGeheimnis, verschluesseln } from "../src/data/krypto.js";
import { SYNC_VERSION } from "../src/domain/sync.js";
import { makeTask } from "../src/domain/tasks.js";
import { dayFromIso } from "../src/domain/time.js";
import { zeitpunktIn } from "../src/domain/zonen.js";

/**
 * Die Schnittstelle für die KI, geprüft ohne Cloudflare.
 *
 * Verschlüsselt wird **echt** — `crypto.subtle` gibt es in Node wie im Worker. Nachgebaut
 * ist nur der Raum. Damit läuft die Prüfung im normalen Testlauf mit, und was hier
 * durchgeht, ist derselbe Weg wie im Netz.
 */
function raumNachbau(anfangsInhalt = null) {
  let stand = anfangsInhalt;

  return {
    get stand() {
      return stand;
    },
    idFromName: (name) => name,
    get: () => ({
      lesen: async () => stand,
      ablegen: async (umschlag, { erwartet, nurNeu }) => {
        if (nurNeu && stand !== null) return { fehler: "gibt es schon" };
        if (!nurNeu && erwartet !== (stand?.stempel ?? null)) return { fehler: "veralteter Stand" };

        const stempel = `"${crypto.randomUUID()}"`;
        stand = { umschlag, stempel };
        return { stempel };
      },
      raeumen: async () => {
        stand = null;
      },
    }),
  };
}

const GEHEIM = "A".repeat(43);
const FREMD = "B".repeat(43);
const ZONE = "Europe/Berlin";

/** Legt einen verschlüsselten Bestand an, wie ihn ein Gerät abgelegt hätte. */
async function bestand(tabellen, geheimnis = GEHEIM) {
  const abgeleitet = await ausGeheimnis(geheimnis);
  const umschlag = await verschluesseln(abgeleitet.schluessel, JSON.stringify(tabellen));
  return raumNachbau({
    umschlag: JSON.stringify({ v: SYNC_VERSION, ...umschlag }),
    stempel: '"start"',
  });
}

function aufgabe(felder) {
  return makeTask({ listId: "l1", sortKey: "a0", createdAt: 1, ...felder });
}

const LISTEN = [{ id: "l1", name: "Posteingang", sortKey: "a0", deletedAt: null }];

const TABELLEN = {
  task_lists: LISTEN,
  tasks: [aufgabe({ id: "t1", title: "Zahnarzt" })],
  tags: [],
  task_tags: [],
};

const ruf = (RAUM, pfad, optionen = {}, geheimnis = GEHEIM) =>
  worker.fetch(
    new Request(`https://petodo.test${pfad}`, {
      ...optionen,
      headers: {
        ...(geheimnis === null ? {} : { Authorization: `Bearer ${geheimnis}` }),
        ...(optionen.headers ?? {}),
      },
    }),
    { RAUM, ZEITZONE: ZONE },
  );

// ------------------------------------------------------------------------------ Zugang

test("ohne_geheimnis_gibt_es_nichts", async () => {
  const antwort = await ruf(await bestand(TABELLEN), "/api/aufgaben", {}, null);
  assert.equal(antwort.status, 401);
});

test("ein_fremdes_geheimnis_oeffnet_nichts", async () => {
  // Es findet einen anderen Raum — dort liegt nichts. Für den Aufrufer ist beides
  // dasselbe: Er darf nicht.
  const antwort = await ruf(await bestand(TABELLEN), "/api/aufgaben", {}, FREMD);
  assert.ok(antwort.status === 401 || antwort.status === 404, String(antwort.status));
});

test("das_geheimnis_wird_nicht_aus_der_adresse_genommen", async () => {
  // In der Adresse landet es in jedem Zugriffsprotokoll dazwischen.
  const antwort = await ruf(await bestand(TABELLEN), `/api/aufgaben?token=${GEHEIM}`, {}, null);
  assert.equal(antwort.status, 401);
});

test("eine_kaputte_zeitzone_faellt_auf_statt_still_danebenzuliegen", async () => {
  const antwort = await worker.fetch(
    new Request("https://petodo.test/api/aufgaben", { headers: { Authorization: `Bearer ${GEHEIM}` } }),
    { RAUM: await bestand(TABELLEN), ZEITZONE: "Europa/Berlin" },
  );
  assert.equal(antwort.status, 500);
  assert.match((await antwort.json()).fehler, /Zeitzone/);
});

test("der_abgleich_bleibt_neben_der_schnittstelle_stehen", async () => {
  // `/sync/…` darf durch die Schnittstelle nicht anders antworten als vorher.
  const antwort = await worker.fetch(
    new Request(`https://petodo.test/sync/${"a".repeat(43)}`),
    { RAUM: await bestand(TABELLEN) },
  );
  assert.equal(antwort.status, 200);
});

// ------------------------------------------------------------------------------- Lesen

test("aufgaben_kommen_mit_deutschen_namen_und_einem_datum", async () => {
  const RAUM = await bestand({
    ...TABELLEN,
    tasks: [
      aufgabe({
        id: "t1",
        title: "Müll",
        dueAt: zeitpunktIn(ZONE, dayFromIso("2026-07-07"), 18 * 60 + 30),
        hasTime: true,
        dueTimeLocal: 18 * 60 + 30,
      }),
    ],
  });

  const gelesen = await (await ruf(RAUM, "/api/aufgaben")).json();
  assert.equal(gelesen.aufgaben.length, 1);
  assert.equal(gelesen.aufgaben[0].titel, "Müll");
  // Der Tag muss in Ortszeit stimmen, nicht in der des Servers.
  assert.equal(gelesen.aufgaben[0].faellig, "2026-07-07");
  assert.equal(gelesen.aufgaben[0].uhrzeit, 18 * 60 + 30);
});

test("ohne_alles_stehen_nur_die_offenen_da", async () => {
  const RAUM = await bestand({
    ...TABELLEN,
    tasks: [
      aufgabe({ id: "t1", title: "Offen" }),
      aufgabe({ id: "t2", title: "Fertig", sortKey: "a1", completedAt: 5 }),
      aufgabe({ id: "t3", title: "Weg", sortKey: "a2", deletedAt: 5 }),
    ],
  });

  const nurOffen = await (await ruf(RAUM, "/api/aufgaben")).json();
  assert.deepEqual(nurOffen.aufgaben.map((a) => a.titel), ["Offen"]);

  const alles = await (await ruf(RAUM, "/api/aufgaben?alles=1")).json();
  assert.deepEqual(alles.aufgaben.map((a) => a.titel), ["Offen", "Fertig"]);
});

test("markdown_kommt_als_markdown_und_traegt_den_vorspann", async () => {
  const antwort = await ruf(await bestand(TABELLEN), "/api/markdown");
  assert.equal(antwort.status, 200);
  assert.match(antwort.headers.get("Content-Type"), /text\/markdown/);

  const text = await antwort.text();
  assert.ok(text.startsWith("---\nquelle: petodo"));
  assert.match(text, /^- \[ \] Zahnarzt$/m);
});

test("die_sicherung_ist_dieselbe_wie_aus_der_app", async () => {
  const gelesen = await (await ruf(await bestand(TABELLEN), "/api/sicherung")).json();
  assert.equal(gelesen.format, "petodo-backup");
  assert.equal(gelesen.version, 1);
  assert.equal(gelesen.tables.tasks.length, 1);
});

test("nichts_von_der_schnittstelle_wird_zwischengespeichert", async () => {
  const antwort = await ruf(await bestand(TABELLEN), "/api/aufgaben");
  assert.equal(antwort.headers.get("Cache-Control"), "no-store");
});

// --------------------------------------------------------------------------- Schreiben

const anlegen = (RAUM, koerper) =>
  ruf(RAUM, "/api/aufgaben", {
    method: "POST",
    body: JSON.stringify(koerper),
    headers: { "Content-Type": "application/json" },
  });

test("eine_aufgabe_laesst_sich_anlegen_und_steht_danach_da", async () => {
  const RAUM = await bestand(TABELLEN);

  const antwort = await anlegen(RAUM, { titel: "Fahrrad zur Inspektion" });
  assert.equal(antwort.status, 201);
  assert.equal((await antwort.json()).angelegt.titel, "Fahrrad zur Inspektion");

  const gelesen = await (await ruf(RAUM, "/api/aufgaben")).json();
  assert.deepEqual(gelesen.aufgaben.map((a) => a.titel).sort(), ["Fahrrad zur Inspektion", "Zahnarzt"]);
});

test("ein_termin_landet_in_ortszeit_nicht_in_der_des_servers", async () => {
  // Der Fehler, den ein Worker ohne Zonenangabe macht: 18:30 würde 18:30 UTC, und in
  // Berlin klingelte es im Sommer um 20:30.
  const RAUM = await bestand(TABELLEN);
  await anlegen(RAUM, { titel: "Müll", faellig: "2026-07-07", uhrzeit: 18 * 60 + 30 });

  const gelesen = await (await ruf(RAUM, "/api/aufgaben")).json();
  const muell = gelesen.aufgaben.find((a) => a.titel === "Müll");

  assert.equal(muell.faellig, "2026-07-07");
  assert.equal(muell.uhrzeit, 18 * 60 + 30);
});

test("ohne_titel_wird_nichts_angelegt", async () => {
  const RAUM = await bestand(TABELLEN);
  assert.equal((await anlegen(RAUM, { titel: "   " })).status, 400);
  assert.equal((await anlegen(RAUM, {})).status, 400);

  const gelesen = await (await ruf(RAUM, "/api/aufgaben")).json();
  assert.equal(gelesen.aufgaben.length, 1);
});

test("ein_datum_das_keines_ist_wird_abgewiesen", async () => {
  const RAUM = await bestand(TABELLEN);
  const antwort = await anlegen(RAUM, { titel: "Krumm", faellig: "morgen" });

  assert.equal(antwort.status, 400);
  assert.match((await antwort.json()).fehler, /faellig/);
});

test("die_liste_laesst_sich_ueber_den_namen_waehlen", async () => {
  const RAUM = await bestand({
    ...TABELLEN,
    task_lists: [...LISTEN, { id: "l2", name: "Arbeit", sortKey: "a1", deletedAt: null }],
  });

  const angelegt = (await (await anlegen(RAUM, { titel: "Bericht", liste: "Arbeit" })).json()).angelegt;
  assert.equal(angelegt.liste, "l2");
});

test("abhaken_geht_und_kommt_beim_lesen_an", async () => {
  const RAUM = await bestand(TABELLEN);

  const antwort = await ruf(RAUM, "/api/aufgaben/t1", {
    method: "PATCH",
    body: JSON.stringify({ erledigt: true }),
    headers: { "Content-Type": "application/json" },
  });
  assert.equal(antwort.status, 200);
  assert.equal((await antwort.json()).geaendert.erledigt, true);

  const gelesen = await (await ruf(RAUM, "/api/aufgaben")).json();
  assert.equal(gelesen.aufgaben.length, 0);
});

test("loeschen_setzt_einen_grabstein_statt_die_zeile_zu_entfernen", async () => {
  // Ohne Grabstein käme die Aufgabe beim nächsten Abgleich vom Telefon zurück.
  const RAUM = await bestand(TABELLEN);

  await ruf(RAUM, "/api/aufgaben/t1", {
    method: "PATCH",
    body: JSON.stringify({ geloescht: true }),
    headers: { "Content-Type": "application/json" },
  });

  const abgeleitet = await ausGeheimnis(GEHEIM);
  const { entschluesseln } = await import("../src/data/krypto.js");
  const umschlag = JSON.parse(RAUM.stand.umschlag);
  const tabellen = JSON.parse(await entschluesseln(abgeleitet.schluessel, umschlag.iv, umschlag.daten));

  assert.equal(tabellen.tasks.length, 1);
  assert.ok(tabellen.tasks[0].deletedAt > 0);
});

test("eine_aufgabe_die_es_nicht_gibt_gibt_es_nicht", async () => {
  const antwort = await ruf(await bestand(TABELLEN), "/api/aufgaben/gibtsnicht", {
    method: "PATCH",
    body: JSON.stringify({ erledigt: true }),
    headers: { "Content-Type": "application/json" },
  });
  assert.equal(antwort.status, 404);
});

test("das_geschriebene_bleibt_verschluesselt_liegen", async () => {
  // Der Klartext darf den Worker nicht verlassen — im Raum liegt ein Umschlag.
  const RAUM = await bestand(TABELLEN);
  await anlegen(RAUM, { titel: "Streng geheim" });

  assert.ok(!RAUM.stand.umschlag.includes("Streng geheim"));
  assert.ok(!RAUM.stand.umschlag.includes("Zahnarzt"));
  assert.match(RAUM.stand.umschlag, /^\{"v":1/);
});

test("wo_noch_nichts_liegt_wird_nicht_geraten", async () => {
  // Ein leerer Raum heißt: Die App hat noch nie abgeglichen. Ein Bestand aus dem Nichts
  // wäre der falsche Dienst — er stünde neben dem der Geräte.
  const antwort = await ruf(raumNachbau(null), "/api/aufgaben");
  assert.equal(antwort.status, 404);
  assert.match((await antwort.json()).fehler, /abgleichen/);
});

test("unbekannte_wege_nennen_die_bekannten", async () => {
  const antwort = await ruf(await bestand(TABELLEN), "/api/unfug");
  assert.equal(antwort.status, 404);
  assert.ok((await antwort.json()).wege.some((weg) => weg.includes("/api/markdown")));
});
