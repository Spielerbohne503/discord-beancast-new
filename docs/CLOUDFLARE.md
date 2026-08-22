# Die Webseite ins Netz stellen

Die App wird nicht übersetzt und nicht gebündelt: reine ES-Module, die der Browser direkt
lädt. Zum Hinstellen reicht ein Ort, der statische Dateien ausliefert.

## Cloudflare

Cloudflare legt beim Import eines Repositorys heute einen **Worker** an, keine
Pages-Anwendung. Die Voreinstellungen passen; nötig sind zwei Felder:

| Feld | Wert |
| --- | --- |
| Build command | `npm run build` *(darf auch leer bleiben, siehe unten)* |
| Deploy command | `npx wrangler deploy` |

Alles Weitere steht in `wrangler.toml`. Dort hängt ein `[build]`-Schritt, den `wrangler`
selbst ausführt — das Feld in der Oberfläche kann deshalb leer bleiben.

**Eine Angabe muss stimmen:** `name` in `wrangler.toml` muss so heißen wie der Worker im
Dashboard. Heißt er dort anders, gehört derselbe Name in die Datei; sonst legt der Deploy
einen zweiten daneben.

### Warum `dist/` und nicht die Wurzel

Cloudflare liefert **alles** aus, was im angegebenen Verzeichnis liegt. Zeigte es auf das
Wurzelverzeichnis, stünde `.git` mit der vollständigen Projektgeschichte unter
`/.git/…` im Netz — dazu das Android-Projekt, die Tests und die Werkzeuge.
`.assetsignore` hilft an dieser Stelle nicht: Bei `directory = "."` greift es nicht.

`node tools/publish.mjs` legt deshalb genau die Dateien der Webseite nach `dist/`. Das ist
**kein Bauschritt im eigentlichen Sinn** — es wird kopiert, nicht übersetzt. `index.html`
im Wurzelverzeichnis bleibt unverändert lauffähig; wer die App nur benutzen will, braucht
das nie. Dieselbe Rolle hat `webseiteKopieren` im Android-Projekt: eine Fassung der
Webseite, mehrere Ziele.

Der Packer prüft sich dabei selbst: Er verfolgt jeden Verweis von `index.html` über
Skripte und Stilvorlagen bis zum letzten Modulimport. Was angefordert wird und nicht
mitgekommen ist, bricht den Lauf ab — statt erst im Netz aufzufallen.

## Was `_headers` tut

Cloudflare liest `_headers` aus dem ausgelieferten Verzeichnis. Dort stehen drei Dinge:

**Eine enge Sicherheitsrichtlinie.** Die App holt nichts von außen — kein Skript, kein
Bild, keine Schrift, keine Verbindung. Deshalb kann die Richtlinie mit `default-src 'none'`
anfangen und nur das Eigene erlauben. Kein `'unsafe-inline'`: Dafür stehen die beiden
Startskripte in `src/ui/fassung.js` und `src/ui/start.js` statt im HTML.

**`Cache-Control: no-cache` für `sw.js`.** Ein zwischengespeicherter Dienstarbeiter zeigt
nach einer Änderung tagelang die alte Fassung, und niemand versteht, warum nichts ankommt.

**Die richtigen Inhaltstypen**, zusammen mit `X-Content-Type-Options: nosniff` — ein Modul
mit falschem Typ wird von modernen Browsern nicht ausgeführt.

`tools/serve.py` liest **dieselbe Datei**. Der Entwicklungsserver verhält sich damit wie
Cloudflare; sonst liefe die App beim Entwickeln ohne Richtlinie und im Netz mit ihr, und
der Unterschied fiele erst dem Nutzer auf.

## Vorher prüfen, was Cloudflare bekommt

```
npm run build                       # legt dist/ an und prüft es auf Vollständigkeit
python3 tools/serve.py 8001 dist    # liefert dist/ aus, mit denselben Kopfzeilen
node tools/browsertest.mjs http://localhost:8001/
```

Der Durchlauf fährt dann gegen genau die Dateien, die im Netz landen — einschließlich der
Probe, ob die App **ohne Netz** startet.

## Was man wissen sollte

**Die Adresse ist öffentlich, die Daten sind es nicht.** Wer den Link kennt, sieht die App
— aber nichts von dem, was drinsteht: Das liegt in IndexedDB im Browser des jeweiligen
Geräts. Es gibt keinen Server, der etwas speichern könnte, und kein Konto.

**Die Daten hängen an der Adresse.** IndexedDB gehört zum Ursprung. Wer heute auf
`…workers.dev` arbeitet und morgen auf eine eigene Domain umzieht, fängt dort bei null an.
Der Umzug geht über eine Sicherung: Einstellungen → herunterladen, an der neuen Adresse
einlesen.

**Die Android-Hülle ist davon unberührt.** Sie lädt die Dateien aus dem Paket, nicht aus
dem Netz — sie hat dafür nicht einmal eine Berechtigung. Ein Deploy ändert an der App auf
dem Telefon nichts, und umgekehrt sind es zwei getrennte Datenbestände.

## Ohne Cloudflare

Jeder statische Ort tut es: GitHub Pages, ein Ordner hinter nginx, ein USB-Stick. Dort den
Inhalt von `dist/` hinlegen — oder gleich das Wurzelverzeichnis, wenn es niemanden stört,
dass mehr darin liegt als die Webseite. Zwei Dinge sollten stimmen:

- **HTTPS**, sonst gibt es kein IndexedDB und keinen Dienstarbeiter.
- **Inhaltstypen** für `.js` (`text/javascript`) und `.webmanifest`.

Die Kopfzeilen aus `_headers` sind kein Muss, aber alles darin ist ein Gewinn.
