/**
 * Die Schnittstelle für eine KI.
 *
 * ## Was das kostet, ehrlich vorweg
 *
 * Für **diesen** Weg entschlüsselt der Server. Das Geheimnis kommt im
 * `Authorization`-Kopf mit, der Worker leitet daraus Raum und Schlüssel ab wie jedes
 * andere Gerät, macht den Klumpen auf, antwortet — und vergisst beides. Gespeichert wird
 * der Schlüssel **nirgends**: nicht in der Bindung, nicht im Durable Object, nicht im
 * Protokoll.
 *
 * Trotzdem ist das eine echte Abschwächung, und sie wird nicht kleingeredet: Während einer
 * Anfrage liegen die Aufgaben im Klartext im Speicher des Workers. „Der Server kann nichts
 * lesen“ gilt weiter für den Abgleich zwischen den Geräten — für diese Strecke nicht.
 *
 * Der Tausch dafür: Es läuft nichts auf einem eigenen Rechner. Ein Chat von irgendwo
 * kommt mit einer Zeichenkette an die Aufgaben, und es gibt nichts zu warten.
 *
 * ## Das Geheimnis ist der Zugang
 *
 * Es gibt kein zweites Token. Wer das Geheimnis hat, hat ohnehin die Aufgaben — ein
 * eigener Schlüssel daneben wäre ein zweiter Ort zum Verlieren, ohne etwas zu schützen.
 * Widerrufen heißt deshalb: in der App trennen und neu koppeln.
 */

import { alsMarkdown } from "../src/domain/markdown.js";
import { encodeBackup } from "../src/domain/backup.js";
import { makeTask, isCompleted, isDeleted, isOpen } from "../src/domain/tasks.js";
import { FractionalIndex, uuid } from "../src/domain/ids.js";
import { SYNC_VERSION } from "../src/domain/sync.js";
import { Priority } from "../src/domain/balance.js";
import { dayFromIso, isoDate } from "../src/domain/time.js";
import { istZone, tagIn, zeitpunktIn } from "../src/domain/zonen.js";
import { ausGeheimnis, entschluesseln, verschluesseln } from "../src/data/krypto.js";

/**
 * So oft wird ein Schreibversuch wiederholt.
 *
 * Zwischen Lesen und Schreiben kann ein Gerät dazwischenkommen. Dann ist der Stempel alt,
 * der Raum weist ab, und der Versuch beginnt von vorn — mit dem neuen Stand. Lautlos
 * überschrieben wird nie.
 */
const VERSUCHE = 4;

/**
 * Die Zeitzone, in der die Termine gemeint sind.
 *
 * Der Worker steht nirgends, wo jemand wohnt — seine Ortszeit ist UTC. Ohne diese Angabe
 * läge „Freitag 18:30“ im Sommer zwei Stunden daneben. Sie steht in `wrangler.toml`,
 * einmal, und ist die einzige Angabe, die diese Schnittstelle überhaupt braucht.
 */
const VORGABE_ZONE = "Europe/Berlin";

const MARKEN = Object.freeze({
  json: "application/json; charset=utf-8",
  markdown: "text/markdown; charset=utf-8",
});

/**
 * Beantwortet alles unter `/api/`.
 *
 * @returns eine `Response`, oder `null` wenn der Pfad nicht hierher gehört
 */
export async function api(anfrage, umgebung, adresse) {
  if (!adresse.pathname.startsWith("/api/")) return null;

  if (anfrage.method === "OPTIONS") return leer(204);

  if (!umgebung.RAUM) {
    return json(501, { fehler: "kein Speicher gebunden", hinweis: "siehe docs/SYNC.md" });
  }

  const geheimnis = ausKopf(anfrage);
  if (geheimnis === null) {
    return json(401, {
      fehler: "kein Zugang",
      hinweis: "Authorization: Bearer <Geheimnis aus dem Koppel-Link>",
    });
  }

  const zone = zoneAus(umgebung);
  if (zone === null) {
    return json(500, {
      fehler: "ZEITZONE in wrangler.toml ist keine Zeitzone",
      hinweis: "z. B. Europe/Berlin",
    });
  }

  const abgeleitet = await ausGeheimnis(geheimnis);
  const raum = umgebung.RAUM.get(umgebung.RAUM.idFromName(abgeleitet.raum));
  const pfad = adresse.pathname.slice("/api/".length).replace(/\/+$/, "");

  if (anfrage.method === "GET") return lesen(pfad, raum, abgeleitet, adresse, zone);
  if (anfrage.method === "POST" && pfad === "aufgaben") return anlegen(anfrage, raum, abgeleitet, zone);
  if (anfrage.method === "PATCH" && pfad.startsWith("aufgaben/")) {
    return aendern(anfrage, raum, abgeleitet, zone, pfad.slice("aufgaben/".length));
  }

  return json(404, { fehler: "unbekannter Weg", wege: WEGE });
}

const WEGE = Object.freeze([
  "GET /api/aufgaben?alles=1",
  "GET /api/markdown",
  "GET /api/sicherung",
  "POST /api/aufgaben",
  "PATCH /api/aufgaben/<id>",
]);

// ------------------------------------------------------------------------------ Lesen

async function lesen(pfad, raum, abgeleitet, adresse, zone) {
  const stand = await bestand(raum, abgeleitet);
  if (stand.fehler) return json(stand.status, { fehler: stand.fehler });

  const { tabellen } = stand;
  const jetzt = Date.now();

  if (pfad === "markdown") {
    return new Response(alsMarkdown(sicht(tabellen), jetzt, zone), {
      status: 200,
      headers: { "Content-Type": MARKEN.markdown, "Cache-Control": "no-store" },
    });
  }

  if (pfad === "sicherung") {
    return new Response(encodeBackup(tabellen, jetzt), {
      status: 200,
      headers: { "Content-Type": MARKEN.json, "Cache-Control": "no-store" },
    });
  }

  if (pfad === "aufgaben") {
    // Ohne `alles=1` nur das Offene. Wer eine KI fragt „was steht an“, will nicht die
    // Erledigten der letzten Monate im Zusammenhang haben.
    const alles = adresse.searchParams.get("alles") === "1";
    const tasks = (tabellen.tasks ?? []).filter((task) => (alles ? !isDeleted(task) : isOpen(task)));
    const listen = (tabellen.task_lists ?? []).filter((liste) => !isDeleted(liste));

    return json(200, {
      stand: new Date(jetzt).toISOString(),
      listen: listen.map((liste) => ({ id: liste.id, name: liste.name })),
      etiketten: (tabellen.tags ?? []).filter((tag) => !isDeleted(tag)).map((tag) => ({ id: tag.id, name: tag.name })),
      aufgaben: tasks.map((task) => nachAussen(task, tabellen, zone)),
    });
  }

  return json(404, { fehler: "unbekannter Weg", wege: WEGE });
}

/**
 * Eine Aufgabe, wie sie nach außen aussieht.
 *
 * Deutsche Namen und keine Zeitstempel in Millisekunden: Das hier liest eine KI, und ein
 * `1789...` als Fälligkeit lädt zum Falschrechnen ein.
 */
function nachAussen(task, tabellen, zone) {
  const etiketten = (tabellen.task_tags ?? [])
    .filter((verweis) => verweis.taskId === task.id && !isDeleted(verweis))
    .map((verweis) => (tabellen.tags ?? []).find((tag) => tag.id === verweis.tagId))
    .filter((tag) => tag && !isDeleted(tag))
    .map((tag) => tag.name);

  return {
    id: task.id,
    titel: task.title,
    notiz: task.note,
    liste: task.listId,
    teilVon: task.parentId,
    erledigt: isCompleted(task),
    faellig: task.dueAt === null ? null : isoDate(tagIn(zone, task.dueAt)),
    uhrzeit: task.hasTime ? task.dueTimeLocal : null,
    prioritaet: task.priority,
    wiederholung: task.rrule,
    etiketten,
  };
}

const sicht = (tabellen) => ({
  tasks: tabellen.tasks ?? [],
  lists: tabellen.task_lists ?? [],
  tags: tabellen.tags ?? [],
  tagLinks: tabellen.task_tags ?? [],
  habits: tabellen.habits ?? [],
});

// --------------------------------------------------------------------------- Schreiben

async function anlegen(anfrage, raum, abgeleitet, zone) {
  const eingabe = await koerper(anfrage);
  if (eingabe === null) return json(400, { fehler: "kein JSON" });

  const titel = String(eingabe.titel ?? "").trim();
  if (titel.length === 0) return json(400, { fehler: "titel fehlt" });

  let angelegt = null;

  const ausgang = await schreiben(raum, abgeleitet, (tabellen, jetzt) => {
    const listen = (tabellen.task_lists ?? []).filter((liste) => !isDeleted(liste));
    if (listen.length === 0) return { fehler: "es gibt noch keine Liste", status: 409 };

    const liste = listen.find((eintrag) => eintrag.id === eingabe.liste || eintrag.name === eingabe.liste) ?? listen[0];

    const faellig = tagAus(eingabe.faellig);
    if (eingabe.faellig !== undefined && eingabe.faellig !== null && faellig === null) {
      return { fehler: "faellig ist kein Datum (JJJJ-MM-TT)", status: 400 };
    }

    const minuten = zahlOderNull(eingabe.uhrzeit);
    const geschwister = (tabellen.tasks ?? []).filter(
      (task) => task.listId === liste.id && (task.parentId ?? null) === null,
    );
    const letzter = geschwister.map((task) => task.sortKey).sort().at(-1) ?? null;

    angelegt = makeTask({
      id: uuid(),
      listId: liste.id,
      parentId: eingabe.teilVon ?? null,
      title: titel,
      note: eingabe.notiz ? String(eingabe.notiz) : null,
      dueAt: faellig === null ? null : zeitpunktIn(zone, faellig, minuten ?? 0),
      hasTime: faellig !== null && minuten !== null,
      dueTimeLocal: faellig !== null && minuten !== null ? minuten : null,
      priority: Priority.coerce(eingabe.prioritaet ?? Priority.DEFAULT),
      rrule: eingabe.wiederholung ?? null,
      sortKey: FractionalIndex.afterOrInitial(letzter),
      createdAt: jetzt,
      updatedAt: jetzt,
    });

    return { tabellen: { ...tabellen, tasks: [...(tabellen.tasks ?? []), angelegt] } };
  });

  if (ausgang.fehler) return json(ausgang.status ?? 500, { fehler: ausgang.fehler });
  return json(201, { angelegt: nachAussen(angelegt, ausgang.tabellen, zone) });
}

async function aendern(anfrage, raum, abgeleitet, zone, id) {
  const eingabe = await koerper(anfrage);
  if (eingabe === null) return json(400, { fehler: "kein JSON" });

  let geaendert = null;

  const ausgang = await schreiben(raum, abgeleitet, (tabellen, jetzt) => {
    const vorher = (tabellen.tasks ?? []).find((task) => task.id === id);
    if (!vorher || isDeleted(vorher)) return { fehler: "gibt es nicht", status: 404 };

    const patch = { updatedAt: jetzt };

    if (eingabe.titel !== undefined) patch.title = String(eingabe.titel).trim();
    if (eingabe.notiz !== undefined) patch.note = eingabe.notiz === null ? null : String(eingabe.notiz);
    if (eingabe.prioritaet !== undefined) patch.priority = Priority.coerce(eingabe.prioritaet);
    if (eingabe.wiederholung !== undefined) patch.rrule = eingabe.wiederholung;

    if (eingabe.erledigt !== undefined) patch.completedAt = eingabe.erledigt ? jetzt : null;
    // Gelöscht wird als Grabstein, nie wirklich — sonst käme die Zeile beim nächsten
    // Abgleich vom Telefon zurück.
    if (eingabe.geloescht === true) patch.deletedAt = jetzt;

    if (eingabe.faellig !== undefined) {
      const tag = tagAus(eingabe.faellig);
      if (eingabe.faellig !== null && tag === null) {
        return { fehler: "faellig ist kein Datum (JJJJ-MM-TT)", status: 400 };
      }
      const minuten = eingabe.uhrzeit === undefined ? (vorher.hasTime ? vorher.dueTimeLocal : null) : zahlOderNull(eingabe.uhrzeit);

      patch.dueAt = tag === null ? null : zeitpunktIn(zone, tag, minuten ?? 0);
      patch.hasTime = tag !== null && minuten !== null;
      patch.dueTimeLocal = tag !== null && minuten !== null ? minuten : null;
      // Eine neue Fälligkeit setzt die Erinnerungskette zurück — dieselbe Regel wie in
      // `repo.updateTask`, sonst mahnt die Aufgabe sofort auf der alten Stufe weiter.
      patch.nagCount = 0;
      patch.nagLastAt = null;
      patch.snoozedUntil = null;
    }

    geaendert = { ...vorher, ...patch };

    return {
      tabellen: {
        ...tabellen,
        tasks: (tabellen.tasks ?? []).map((task) => (task.id === id ? geaendert : task)),
      },
    };
  });

  if (ausgang.fehler) return json(ausgang.status ?? 500, { fehler: ausgang.fehler });
  return json(200, { geaendert: nachAussen(geaendert, ausgang.tabellen, zone) });
}

/**
 * Lesen, ändern, zurückschreiben — mit dem Stempel des gelesenen Stands.
 *
 * Kommt zwischendurch ein Gerät dazwischen, weist der Raum ab und der ganze Vorgang läuft
 * neu, auf dem neuen Stand. Die Änderung wird dabei **noch einmal** angewandt, nicht
 * bloß der alte Stand hochgeschoben.
 */
async function schreiben(raum, abgeleitet, aendern) {
  for (let versuch = 0; versuch < VERSUCHE; versuch++) {
    const stand = await bestand(raum, abgeleitet);
    if (stand.fehler) return { fehler: stand.fehler, status: stand.status };

    const ergebnis = aendern(stand.tabellen, Date.now());
    if (ergebnis.fehler) return ergebnis;

    const umschlag = await verschluesseln(abgeleitet.schluessel, JSON.stringify(ergebnis.tabellen));
    const ausgang = await raum.ablegen(
      JSON.stringify({ v: SYNC_VERSION, ...umschlag }),
      { erwartet: stand.stempel, nurNeu: stand.stempel === null },
    );

    if (!ausgang.fehler) return { tabellen: ergebnis.tabellen };
  }

  return { fehler: "zu viele Zusammenstöße", status: 409 };
}

/** Holt den Stand und macht ihn auf. */
async function bestand(raum, abgeleitet) {
  const stand = await raum.lesen();
  if (stand === null) {
    return { fehler: "auf dem Server liegt noch nichts — in der App einmal abgleichen", status: 404 };
  }

  let umschlag;
  try {
    umschlag = JSON.parse(stand.umschlag);
  } catch {
    return { fehler: "unlesbarer Inhalt", status: 500 };
  }

  if (Number(umschlag.v) > SYNC_VERSION) {
    return { fehler: "neuere Fassung des Formats", status: 409 };
  }

  const klartext = await entschluesseln(abgeleitet.schluessel, umschlag.iv, umschlag.daten);
  // Das ist der Normalfall bei einem falschen Geheimnis: Der Raum wurde gefunden, aber der
  // Schlüssel passt nicht. Für den Aufrufer ist beides dasselbe — er darf nicht.
  if (klartext === null) return { fehler: "Geheimnis passt nicht", status: 401 };

  try {
    return { tabellen: JSON.parse(klartext), stempel: stand.stempel };
  } catch {
    return { fehler: "unlesbarer Inhalt", status: 500 };
  }
}

// ------------------------------------------------------------------------------ Kleinkram

/**
 * Das Geheimnis aus dem Kopf.
 *
 * Nur aus `Authorization`, nie aus der Adresse: Ein Geheimnis in der Adresse landet in
 * jedem Zugriffsprotokoll, das zwischen hier und dem Aufrufer steht.
 */
function ausKopf(anfrage) {
  const kopf = anfrage.headers.get("Authorization") ?? "";
  const treffer = /^Bearer\s+([A-Za-z0-9_-]{43})$/.exec(kopf.trim());
  return treffer === null ? null : treffer[1];
}

async function koerper(anfrage) {
  try {
    const gelesen = await anfrage.json();
    return gelesen !== null && typeof gelesen === "object" ? gelesen : null;
  } catch {
    return null;
  }
}

/**
 * `JJJJ-MM-TT` in einen Kalendertag. Alles andere ist `null`.
 *
 * Geprüft wird die Form hier, gerechnet wird mit `dayFromIso` — sonst gäbe es eine zweite
 * Stelle, an der aus einer Zeichenkette ein Tag wird, und die beiden liefen auseinander.
 */
function tagAus(text) {
  if (text === null || text === undefined) return null;
  const sauber = String(text).trim();
  return /^\d{4}-\d{2}-\d{2}$/.test(sauber) ? dayFromIso(sauber) : null;
}

function zahlOderNull(wert) {
  if (wert === null || wert === undefined) return null;
  const zahl = Number(wert);
  return Number.isFinite(zahl) && zahl >= 0 && zahl < 1440 ? Math.floor(zahl) : null;
}

function json(status, koerper) {
  return new Response(JSON.stringify(koerper, null, 2), {
    status,
    headers: { "Content-Type": MARKEN.json, "Cache-Control": "no-store" },
  });
}

const leer = (status) => new Response(null, { status, headers: { "Cache-Control": "no-store" } });

/**
 * Die Zeitzone aus der Umgebung, oder `null` wenn sie Unfug ist.
 *
 * Lieber ein klarer Fehler als Termine, die still eine Stunde danebenliegen.
 */
function zoneAus(umgebung) {
  const zone = String(umgebung.ZEITZONE ?? VORGABE_ZONE).trim();
  return istZone(zone) ? zone : null;
}
