/**
 * „Mehr“ — nur auf dem Telefon.
 *
 * Am Schreibtisch stehen dieselben Punkte in der Seitenleiste; dort wäre ein Untermenü
 * eine Schikane. Diese Ansicht ist deshalb bewusst dünn: eine Liste von Verweisen, kein
 * eigener Inhalt.
 */

import { navigieren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S } from "../strings.js";

const PUNKTE = [
  { route: "companion", text: S.nav_companion, symbol: "begleiter" },
  { route: "stats", text: S.nav_stats, symbol: "rueckblick" },
  { route: "settings", text: S.nav_settings, symbol: "einstellungen" },
];

export function moreView() {
  const element = h("div.karte.navi");

  function update() {
    fuellen(
      element,
      PUNKTE.map((punkt) =>
        h(
          "button.navi__punkt",
          { onclick: () => navigieren(punkt.route) },
          icon(punkt.symbol, 18),
          h("span.navi__punkt-name", {}, punkt.text),
          icon("pfeil_rechts", 16),
        ),
      ),
    );
  }

  return { el: element, update };
}

export { state };
