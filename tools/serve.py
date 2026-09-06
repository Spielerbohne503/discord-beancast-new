#!/usr/bin/env python3
"""Ein Entwicklungsserver ohne Abhängigkeiten.

Statische Dateien reichen: Die App hat keinen Build-Schritt. Wer sie irgendwo
hinstellen will, kopiert den Ordner — mehr braucht es nicht.

Der Server liest ``_headers`` — dieselbe Datei, die Cloudflare Pages ausliest.
Sonst läuft die App beim Entwickeln ohne Sicherheitsrichtlinie und im Netz mit
ihr, und der Unterschied fällt erst auf, wenn die Seite dort weiß bleibt.
"""
import fnmatch
import http.server
import socketserver
import sys
from pathlib import Path

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000

# Zweites Argument: welches Verzeichnis ausgeliefert wird. Ohne Angabe das
# Wurzelverzeichnis — das **ist** die Webseite. Mit ``dist`` lässt sich prüfen, was
# Cloudflare tatsächlich bekommt, samt derselben Kopfzeilen.
WURZEL = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else Path(__file__).resolve().parent.parent


def kopfzeilen_lesen(pfad):
    """``_headers`` als Liste aus (Muster, [(Name, Wert), …]).

    Das Format ist bewusst simpel: Eine Zeile, die mit ``/`` anfängt, ist ein
    Pfadmuster; eingerückte Zeilen darunter sind Kopfzeilen dazu. ``#`` ist ein
    Kommentar. Mehr kann Cloudflare an dieser Stelle auch nicht.
    """
    if not pfad.exists():
        return []

    regeln = []
    aktuelle = None

    for zeile in pfad.read_text(encoding="utf-8").splitlines():
        ohne_kommentar = zeile.split("#", 1)[0]
        if not ohne_kommentar.strip():
            continue

        if not ohne_kommentar[0].isspace():
            aktuelle = (ohne_kommentar.strip(), [])
            regeln.append(aktuelle)
        elif aktuelle is not None and ":" in ohne_kommentar:
            name, _, wert = ohne_kommentar.partition(":")
            aktuelle[1].append((name.strip(), wert.strip()))

    return regeln


REGELN = kopfzeilen_lesen(WURZEL / "_headers")


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WURZEL), **kwargs)

    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".js": "text/javascript",
        ".mjs": "text/javascript",
        ".webmanifest": "application/manifest+json",
    }

    def do_GET(self):
        """``/offen`` beantwortet sonst der Worker — hier heißt die Antwort „zu“.

        Ohne das gäbe es beim Entwickeln bei jedem Laden einen 404 im Protokoll, solange
        das Gerät nicht gekoppelt ist. Genau die Sorte Rauschen, in der ein echter Fehler
        später untergeht. Ausgeliefert wird dasselbe, was ``tools/publish.mjs`` nach
        ``dist/`` legt — der Entwicklungsbetrieb verhält sich damit wie ein Server ohne
        die offene Seite.
        """
        if self.path.split("?", 1)[0].rstrip("/") == "/offen":
            koerper = b'{"offen":false}\n'
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(koerper)))
            self.end_headers()
            self.wfile.write(koerper)
            return

        super().do_GET()

    def send_header(self, keyword, value):
        """Kopfzeilen sammeln statt sofort schreiben.

        Cloudflare **ersetzt** eine Kopfzeile aus ``_headers``. Würde hier nur
        angehängt, stünde ``Content-Type`` zweimal in der Antwort — und ein Browser
        mit ``nosniff`` weist ein Modul mit widersprüchlichem Typ zurück. Der Fehler
        träte nur im Entwicklungsbetrieb auf, also genau dort, wo man ihn für einen
        echten hält.
        """
        self._gesammelt.append((keyword, value))

    def end_headers(self):
        pfad = self.path.split("?", 1)[0]
        eigene = {}

        # Rückwärts, damit die spezielle Regel die allgemeine schlägt: In ``_headers``
        # steht ``/*`` oben, die genauen Pfade darunter.
        for muster, kopfzeilen in reversed(REGELN):
            if fnmatch.fnmatch(pfad, muster):
                for name, wert in kopfzeilen:
                    eigene.setdefault(name.lower(), (name, wert))

        # Im Entwicklungsbetrieb nichts zwischenspeichern, sonst sieht man Änderungen nicht.
        eigene.setdefault("cache-control", ("Cache-Control", "no-store"))

        gesammelt, self._gesammelt = self._gesammelt, []
        for keyword, value in gesammelt:
            if keyword.lower() not in eigene:
                super().send_header(keyword, value)
        for keyword, value in eigene.values():
            super().send_header(keyword, value)

        super().end_headers()

    def handle_one_request(self):
        self._gesammelt = []
        super().handle_one_request()

    def log_message(self, *args):
        pass


class Server(socketserver.TCPServer):
    """Nach einem Neustart sofort wieder lauschen.

    Ohne ``allow_reuse_address`` bleibt der Port nach dem Beenden noch eine Minute
    belegt, und der nächste Start scheitert mit „Address already in use“ — beim
    Entwickeln genau der Moment, in dem man gerade etwas ausprobieren wollte.
    """

    allow_reuse_address = True


with Server(("", PORT), Handler) as httpd:
    print(f"PeTodo läuft auf http://localhost:{PORT}")
    httpd.serve_forever()
