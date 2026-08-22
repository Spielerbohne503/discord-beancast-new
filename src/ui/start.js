/**
 * Der Einstiegspunkt.
 *
 * Steht als eigene Datei da und nicht als `<script>` im HTML, damit die
 * Sicherheitsrichtlinie ohne `'unsafe-inline'` auskommt: Erlaubt ist ausschließlich
 * `script-src 'self'`. Ein Inline-Skript würde diese Regel entweder aufweichen oder einen
 * Hash verlangen, der bei jeder Änderung still bricht — und dann bleibt die Seite weiß,
 * ohne dass jemand weiß, warum.
 */

import { starten } from "./app.js";
import { h, fuellen } from "./dom.js";
import { S } from "./strings.js";

const wurzel = document.getElementById("wurzel");

starten(wurzel).catch((fehler) => {
  // Ein Fehler beim Start darf nicht in einer weißen Seite enden — dann weiß niemand, ob
  // die App kaputt ist oder nur langsam. Gebaut wird das aus Elementen, nicht aus
  // zusammengeklebtem HTML: In der Meldung steckt fremder Text.
  console.error(fehler);
  fuellen(
    wurzel,
    h(
      "div.leer",
      {},
      h("p.leer__titel", {}, S.error_generic),
      h("p", {}, String(fehler?.message ?? fehler)),
    ),
  );
});

/**
 * Der Dienstarbeiter macht die Seite offline benutzbar.
 *
 * In der Android-Hülle liegen die Dateien schon im Paket. Ein Dienstarbeiter würde dort
 * nur eine zweite, ältere Kopie zwischenspeichern — und nach einem Update der App bekäme
 * man tagelang die alte Fassung zu sehen.
 */
if ("serviceWorker" in navigator && !globalThis.Petodo) {
  addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));
}
