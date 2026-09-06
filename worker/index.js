/**
 * Der Ablageort für den Abgleich.
 *
 * **Er kann nichts lesen.** Was hier ankommt, ist ein Klumpen Bytes unter einer Kennung,
 * die beide aus derselben Losung abgeleitet sind — der Schlüssel verlässt das Gerät nie.
 * Der Server weiß nicht, wem etwas gehört, was drinsteht oder wie viele Aufgaben es sind.
 * Er weiß nur: unter dieser Kennung liegen so viele Bytes.
 *
 * Deshalb gibt es hier auch **kein Konto**. Wer die Losung kennt, kennt den Raum; wer den
 * Raum kennt, hat einen unlesbaren Klumpen.
 *
 * Der Worker läuft **neben** den Dateien der Webseite: Cloudflare liefert erst die Assets
 * aus, und nur was dort nicht passt, landet hier. Für die Webseite selbst ist der Abgleich
 * damit am selben Ursprung — dort braucht es kein CORS.
 *
 * Die Android-Hülle liegt an einem **anderen** Ursprung (`appassets.androidplatform.net`,
 * der Schlüssel, unter dem ihre IndexedDB liegt, und deshalb nicht verhandelbar) und
 * bräuchte ohne Erlaubnis eine Ausnahme von der Ursprungsregel des Browsers, um überhaupt
 * eine Antwort lesen zu können — nicht um den Speicher zu schützen, der schützt sich
 * selbst über die geratene Raumkennung, sondern weil sonst kein WebView ihr die Antwort
 * zeigt. Erlaubt ist deshalb genau dieser eine zusätzliche Ursprung, nicht jeder.
 *
 * Der Stand liegt je Raum in einem Durable Object (`worker/raum.js`). Das entsteht beim
 * Ausrollen von selbst — es gibt **keinen Schritt im Dashboard**, den man vergessen kann,
 * und damit auch keinen Abgleich, der stillschweigend nie lief.
 */

import { api } from "./api.js";
import { offen } from "./offen.js";

/**
 * Der einzige weitere Ursprung, der eine Antwort lesen darf.
 *
 * Fest verdrahtet, nicht aus der Anfrage gespiegelt: Sonst öffnete sich die Ablage für
 * jede beliebige Webseite, die im Namen eines Besuchers einen Raum erraten will.
 */
const HUELLEN_URSPRUNG = "https://appassets.androidplatform.net";

/** So groß darf ein Bestand höchstens werden. Weit jenseits dessen, was Aufgaben brauchen. */
const MAX_BYTES = 8 * 1024 * 1024;

export default {
  async fetch(anfrage, umgebung) {
    const adresse = new URL(anfrage.url);

    // Die Schnittstelle für eine KI. Sie steht bewusst **neben** dem Abgleich und nicht
    // darin: Der Abgleich bleibt Ende zu Ende verschlüsselt, `/api/` entschlüsselt.
    const fuerDieKi = await api(anfrage, umgebung, adresse);
    if (fuerDieKi !== null) return fuerDieKi;

    // Die offene Seite: Wer sie aufruft, bekommt das gemeinsame Geheimnis und ist damit
    // sofort drin. Ausgeschaltet, solange nichts eingetragen ist.
    const eintritt = offen(umgebung, adresse);
    if (eintritt !== null) return eintritt;

    if (!adresse.pathname.startsWith("/sync/")) {
      // Alles andere gehört den Dateien der Webseite.
      return umgebung.ASSETS ? umgebung.ASSETS.fetch(anfrage) : antwort(404, { fehler: "unbekannt" });
    }

    return mitCors(await sync(anfrage, umgebung, adresse), anfrage);
  },
};

async function sync(anfrage, umgebung, adresse) {
  // Der Vorflug fragt nur, ob er darf — noch bevor irgendetwas über den Raum bekannt ist.
  if (anfrage.method === "OPTIONS") return new Response(null, { status: 204 });

  if (!umgebung.RAUM) {
    return antwort(501, {
      fehler: "kein Speicher gebunden",
      hinweis: "In wrangler.toml fehlt die Bindung RAUM — siehe docs/SYNC.md",
    });
  }

  const kennung = adresse.pathname.slice("/sync/".length);
  // Die Kennung ist immer 43 Zeichen Base64url aus 32 Byte. Alles andere ist kein Raum,
  // sondern jemand, der die Ablage als allgemeinen Speicher benutzen möchte.
  if (!/^[A-Za-z0-9_-]{43}$/.test(kennung)) return antwort(400, { fehler: "ungültige Kennung" });

  // `idFromName` bildet dieselbe Kennung immer auf dasselbe Objekt ab — ohne dass
  // irgendwo eine Liste der Räume geführt werden müsste.
  const raum = umgebung.RAUM.get(umgebung.RAUM.idFromName(kennung));

  if (anfrage.method === "GET") return holen(raum);
  if (anfrage.method === "PUT") return ablegen(anfrage, raum);
  if (anfrage.method === "DELETE") return loeschen(raum);

  return antwort(405, { fehler: "Methode nicht erlaubt" }, { Allow: "GET, PUT, DELETE, OPTIONS" });
}

/**
 * Erlaubt der Hülle, die Antwort zu lesen — sonst niemandem.
 *
 * Ohne dies käme die Antwort zwar an, aber kein WebView zeigte sie ihrem JavaScript: Die
 * Ursprungsregel des Browsers gilt für das *Lesen* einer Antwort, nicht fürs Abschicken der
 * Anfrage. Der `ETag` muss dabei eigens freigegeben werden — sonst läse `fetch()` jeden
 * anderen Kopf, nur den nicht, an dem der ganze Schutz gegen gleichzeitiges Schreiben hängt.
 */
function mitCors(antwort, anfrage) {
  if (anfrage.headers.get("Origin") !== HUELLEN_URSPRUNG) return antwort;

  const kopfzeilen = new Headers(antwort.headers);
  kopfzeilen.set("Access-Control-Allow-Origin", HUELLEN_URSPRUNG);
  kopfzeilen.set("Access-Control-Allow-Methods", "GET, PUT, DELETE, OPTIONS");
  kopfzeilen.set("Access-Control-Allow-Headers", "Content-Type, If-Match, If-None-Match");
  kopfzeilen.set("Access-Control-Expose-Headers", "ETag");
  kopfzeilen.set("Access-Control-Max-Age", "86400");
  kopfzeilen.append("Vary", "Origin");

  return new Response(antwort.body, { status: antwort.status, statusText: antwort.statusText, headers: kopfzeilen });
}

async function holen(raum) {
  const stand = await raum.lesen();
  if (stand === null) return antwort(404, { fehler: "nichts abgelegt" });

  return new Response(stand.umschlag, {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      // Der Stempel ist die Handhabe gegen gleichzeitiges Schreiben.
      ETag: stand.stempel,
    },
  });
}

/**
 * Legt ab — aber nur, wenn der Ableger vom aktuellen Stand ausgeht.
 *
 * Ohne diese Prüfung überschriebe ein langsames Gerät die Arbeit eines schnellen, und zwar
 * lautlos. `If-Match` und `If-None-Match: *` sind dafür da; wer sie wegzulassen versucht,
 * bekommt eine Abfuhr statt eines stillen Datenverlusts.
 *
 * Entschieden wird das **im Raum**, nicht hier: Nur dort liegen Prüfung und Schreiben im
 * selben Schritt.
 */
async function ablegen(anfrage, raum) {
  const rohtext = await anfrage.text();
  if (rohtext.length > MAX_BYTES) return antwort(413, { fehler: "zu groß" });

  let umschlag;
  try {
    umschlag = JSON.parse(rohtext);
  } catch {
    return antwort(400, { fehler: "kein JSON" });
  }
  if (typeof umschlag?.iv !== "string" || typeof umschlag?.daten !== "string") {
    return antwort(400, { fehler: "kein Umschlag" });
  }

  const ausgang = await raum.ablegen(rohtext, {
    erwartet: anfrage.headers.get("If-Match"),
    nurNeu: anfrage.headers.get("If-None-Match") === "*",
  });

  if (ausgang.fehler) return antwort(ausgang.status ?? 412, { fehler: ausgang.fehler });
  return antwort(200, { abgelegt: true }, { ETag: ausgang.stempel });
}

async function loeschen(raum) {
  await raum.raeumen();
  return antwort(200, { geloescht: true });
}

function antwort(status, koerper, kopfzeilen = {}) {
  return new Response(JSON.stringify(koerper), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...kopfzeilen },
  });
}
