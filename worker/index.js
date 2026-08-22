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
 * aus, und nur was dort nicht passt, landet hier. Damit ist der Abgleich am selben
 * Ursprung wie die App — die Sicherheitsrichtlinie braucht keine Ausnahme, und CORS gibt
 * es gar nicht erst.
 *
 * **Ohne KV-Bindung tut er nichts** und sagt das auch. Die Webseite läuft dann wie zuvor,
 * nur ohne Abgleich — eine fehlende Bindung soll nicht die ganze Anwendung mitnehmen.
 */

/** So groß darf ein Bestand höchstens werden. Weit jenseits dessen, was Aufgaben brauchen. */
const MAX_BYTES = 8 * 1024 * 1024;

/** Wird nichts mehr abgelegt, fällt der Raum irgendwann von selbst weg. */
const HALTBARKEIT_TAGE = 400;

export default {
  async fetch(anfrage, umgebung) {
    const adresse = new URL(anfrage.url);

    if (!adresse.pathname.startsWith("/sync/")) {
      // Alles andere gehört den Dateien der Webseite.
      return umgebung.ASSETS ? umgebung.ASSETS.fetch(anfrage) : antwort(404, { fehler: "unbekannt" });
    }

    if (!umgebung.PETODO) {
      return antwort(501, {
        fehler: "kein Speicher gebunden",
        hinweis: "In wrangler.toml die KV-Bindung PETODO eintragen — siehe docs/SYNC.md",
      });
    }

    const raum = adresse.pathname.slice("/sync/".length);
    // Die Kennung ist immer 43 Zeichen Base64url aus 32 Byte. Alles andere ist kein Raum,
    // sondern jemand, der die Ablage als allgemeinen Speicher benutzen möchte.
    if (!/^[A-Za-z0-9_-]{43}$/.test(raum)) return antwort(400, { fehler: "ungültige Kennung" });

    if (anfrage.method === "GET") return holen(umgebung, raum);
    if (anfrage.method === "PUT") return ablegen(anfrage, umgebung, raum);
    if (anfrage.method === "DELETE") return loeschen(umgebung, raum);

    return antwort(405, { fehler: "Methode nicht erlaubt" }, { Allow: "GET, PUT, DELETE" });
  },
};

async function holen(umgebung, raum) {
  const eintrag = await umgebung.PETODO.getWithMetadata(schluessel(raum), { type: "text" });
  if (eintrag.value === null) return antwort(404, { fehler: "nichts abgelegt" });

  return new Response(eintrag.value, {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      // Der Stempel ist die Handhabe gegen gleichzeitiges Schreiben.
      ETag: eintrag.metadata?.stempel ?? '"unbekannt"',
    },
  });
}

/**
 * Legt ab — aber nur, wenn der Ableger vom aktuellen Stand ausgeht.
 *
 * Ohne diese Prüfung überschriebe ein langsames Gerät die Arbeit eines schnellen, und zwar
 * lautlos. `If-Match` und `If-None-Match: *` sind dafür da; wer sie wegzulassen versucht,
 * bekommt eine Abfuhr statt eines stillen Datenverlusts.
 */
async function ablegen(anfrage, umgebung, raum) {
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

  const vorhanden = await umgebung.PETODO.getWithMetadata(schluessel(raum), { type: "text" });
  const stempel = vorhanden.metadata?.stempel ?? null;

  const erwartet = anfrage.headers.get("If-Match");
  const nurNeu = anfrage.headers.get("If-None-Match") === "*";

  if (nurNeu && vorhanden.value !== null) return antwort(412, { fehler: "gibt es schon" });
  if (!nurNeu && erwartet === null) return antwort(428, { fehler: "If-Match fehlt" });
  if (!nurNeu && erwartet !== stempel) return antwort(412, { fehler: "veralteter Stand" });

  const neuerStempel = `"${crypto.randomUUID()}"`;
  await umgebung.PETODO.put(schluessel(raum), rohtext, {
    metadata: { stempel: neuerStempel, abgelegt: Date.now() },
    expirationTtl: HALTBARKEIT_TAGE * 86_400,
  });

  return antwort(200, { abgelegt: true }, { ETag: neuerStempel });
}

async function loeschen(umgebung, raum) {
  await umgebung.PETODO.delete(schluessel(raum));
  return antwort(200, { geloescht: true });
}

const schluessel = (raum) => `raum:${raum}`;

function antwort(status, koerper, kopfzeilen = {}) {
  return new Response(JSON.stringify(koerper), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...kopfzeilen },
  });
}
