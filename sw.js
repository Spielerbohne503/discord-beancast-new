/**
 * Der Dienstarbeiter — damit die App ohne Netz startet.
 *
 * Zwei Regeln:
 *
 * 1. **Netz zuerst, Zwischenspeicher als Auffangnetz.** Umgekehrt sieht man nach einer
 *    Änderung tagelang die alte Fassung und weiß nicht, warum.
 * 2. **Es wird nichts hochgeladen.** Der Dienstarbeiter kennt nur `GET` auf eigene
 *    Adressen; alles andere geht ihn nichts an.
 *
 * Die Daten liegen in IndexedDB, nicht hier. Ein geleerter Zwischenspeicher kostet einen
 * Ladevorgang, keine Aufgabe.
 */

const FASSUNG = "petodo-v1";

const GERUEST = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./icon.svg",
  "./src/styles/tokens.css",
  "./src/styles/base.css",
  "./src/styles/components.css",
  "./src/styles/layout.css",
  "./src/styles/orb.css",
  "./src/styles/views.css",
];

self.addEventListener("install", (ereignis) => {
  ereignis.waitUntil(
    caches
      .open(FASSUNG)
      // Einzeln, damit eine fehlende Datei nicht die ganze Installation scheitern lässt.
      .then((speicher) => Promise.allSettled(GERUEST.map((pfad) => speicher.add(pfad))))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (ereignis) => {
  ereignis.waitUntil(
    caches
      .keys()
      .then((namen) => Promise.all(namen.filter((name) => name !== FASSUNG).map((name) => caches.delete(name))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (ereignis) => {
  const anfrage = ereignis.request;
  if (anfrage.method !== "GET") return;
  if (new URL(anfrage.url).origin !== self.location.origin) return;

  ereignis.respondWith(
    fetch(anfrage)
      .then((antwort) => {
        // Nur brauchbare Antworten aufheben — eine 404 im Zwischenspeicher ist schlimmer
        // als gar keine.
        if (antwort.ok) {
          const kopie = antwort.clone();
          caches.open(FASSUNG).then((speicher) => speicher.put(anfrage, kopie));
        }
        return antwort;
      })
      .catch(async () => {
        const gespeichert = await caches.match(anfrage);
        if (gespeichert) return gespeichert;
        // Ein Seitenaufruf ohne Netz landet auf der Startseite — die App findet sich
        // danach selbst zurecht.
        if (anfrage.mode === "navigate") return caches.match("./index.html");
        return Response.error();
      }),
  );
});
