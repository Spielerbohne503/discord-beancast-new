# Die Webseite ins Netz stellen

Es gibt keinen Bauschritt. Die Webseite **ist** das Wurzelverzeichnis: reine ES-Module,
die der Browser direkt lädt. Zum Hinstellen reicht ein Ort, der statische Dateien
ausliefert.

## Cloudflare Pages

In der Weboberfläche: **Workers & Pages → Create → Pages → Connect to Git**, das
Repository wählen, und dann:

| Feld | Wert |
| --- | --- |
| Framework preset | `None` |
| Build command | *leer lassen* |
| Build output directory | `/` |

Mehr nicht. `wrangler.toml` im Wurzelverzeichnis nimmt einem die letzte Angabe ab; kommt
Cloudflare damit nicht zurecht, kann die Datei ersatzlos gelöscht und das Verzeichnis von
Hand eingetragen werden.

Jeder Push auf den Zweig baut neu. Es gibt nichts zu übersetzen, also dauert es Sekunden.

## Was `_headers` tut

Cloudflare liest `_headers` aus dem Wurzelverzeichnis. Dort stehen drei Dinge:

**Eine enge Sicherheitsrichtlinie.** Die App holt nichts von außen — kein Skript, kein
Bild, keine Schrift, keine Verbindung. Deshalb kann die Richtlinie mit `default-src 'none'`
anfangen und nur das Eigene erlauben. Kein `'unsafe-inline'`: Dafür stehen die beiden
Startskripte in `src/ui/fassung.js` und `src/ui/start.js` statt im HTML.

**`Cache-Control: no-cache` für `sw.js`.** Ein zwischengespeicherter Dienstarbeiter zeigt
nach einer Änderung tagelang die alte Fassung, und niemand versteht, warum nichts ankommt.

**Die richtigen Inhaltstypen.** Zusammen mit `X-Content-Type-Options: nosniff` — ein Modul
mit falschem Typ wird von modernen Browsern nicht ausgeführt.

`tools/serve.py` liest **dieselbe Datei**. Der Entwicklungsserver verhält sich damit wie
Cloudflare; sonst liefe die App beim Entwickeln ohne Richtlinie und im Netz mit ihr, und
der Unterschied fiele erst dem Nutzer auf.

## Was man wissen sollte

**Die Adresse ist öffentlich.** Wer sie kennt, sieht die App — aber nicht die Daten: Die
liegen in IndexedDB im Browser des jeweiligen Geräts. Es gibt keinen Server, der etwas
speichern könnte, und kein Konto.

**Die Daten hängen an der Adresse.** IndexedDB gehört zum Ursprung. Wer heute auf
`petodo.pages.dev` arbeitet und morgen auf eine eigene Domain umzieht, fängt dort bei null
an. Der Umzug geht über eine Sicherung: Einstellungen → herunterladen, an der neuen Adresse
einlesen.

**Die Android-Hülle ist davon unberührt.** Sie lädt die Dateien aus dem Paket, nicht aus
dem Netz — sie hat dafür nicht einmal eine Berechtigung. Ein Deploy auf Cloudflare ändert
an der App auf dem Telefon nichts, und umgekehrt sind es zwei getrennte Datenbestände.

## Ohne Cloudflare

Jeder statische Ort tut es: GitHub Pages, ein Ordner hinter nginx, ein USB-Stick. Zwei
Dinge sollten stimmen, sonst fehlt etwas:

- **HTTPS**, sonst gibt es kein IndexedDB und keinen Dienstarbeiter.
- **Inhaltstypen** für `.js` (`text/javascript`) und `.webmanifest`.

Die Kopfzeilen aus `_headers` sind kein Muss, aber alles darin ist ein Gewinn.
