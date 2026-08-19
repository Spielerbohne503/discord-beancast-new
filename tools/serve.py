#!/usr/bin/env python3
"""Ein Entwicklungsserver ohne Abhängigkeiten.

Statische Dateien reichen: Die App hat keinen Build-Schritt. Wer sie irgendwo
hinstellen will, kopiert den Ordner — mehr braucht es nicht.
"""
import http.server
import socketserver
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".js": "text/javascript",
        ".mjs": "text/javascript",
        ".webmanifest": "application/manifest+json",
    }

    def end_headers(self):
        # Im Entwicklungsbetrieb nichts zwischenspeichern, sonst sieht man Änderungen nicht.
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, *args):
        pass


with socketserver.TCPServer(("", PORT), Handler) as httpd:
    print(f"PeTodo läuft auf http://localhost:{PORT}")
    httpd.serve_forever()
