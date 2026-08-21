/**
 * Die Symbole, als Pfade im Quelltext.
 *
 * Eine Symbolschrift oder eine Bibliothek wäre eine weitere Datei, die geladen werden
 * muss — und bis sie da ist, steht die Leiste leer. So ist alles sofort da.
 *
 * Alle Symbole sitzen im selben 24er-Raster und zeichnen mit `currentColor`.
 */

import { svg } from "./dom.js";

const PFADE = {
  heute: "M8 2v3M16 2v3M3.5 9h17M5 5.5h14a1.5 1.5 0 0 1 1.5 1.5v12A1.5 1.5 0 0 1 19 20.5H5A1.5 1.5 0 0 1 3.5 19V7A1.5 1.5 0 0 1 5 5.5Z",
  listen: "M4 6h1M4 12h1M4 18h1M9 6h11M9 12h11M9 18h11",
  fokus: "M12 7v5l3 2M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17Z",
  begleiter: "M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17ZM8.5 11h.01M15.5 11h.01M9 15c.9.8 1.9 1.2 3 1.2s2.1-.4 3-1.2",
  gewohnheiten: "M20 6 9 17l-5-5",
  rueckblick: "M4 20V10M10 20V4M16 20v-7M22 20H2",
  einstellungen:
    "M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4ZM19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1h.2a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1Z",
  mehr: "M5 12h.01M12 12h.01M19 12h.01",
  plus: "M12 5v14M5 12h14",
  haken: "M20 6 9 17l-5-5",
  suchen: "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14ZM20 20l-4-4",
  schliessen: "M6 6l12 12M18 6 6 18",
  pfeil_rechts: "m9 6 6 6-6 6",
  pfeil_links: "m15 6-6 6 6 6",
  griff: "M9 6h.01M9 12h.01M9 18h.01M15 6h.01M15 12h.01M15 18h.01",
  papierkorb: "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3",
  stift: "M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17v3Z",
  uhr: "M12 7v5l3 2M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17Z",
  wiederholen: "M4 10a8 8 0 0 1 13.7-5.7L20 6M20 4v3h-3M20 14a8 8 0 0 1-13.7 5.7L4 18M4 20v-3h3",
  start: "M8 5.5v13l11-6.5-11-6.5Z",
  pause: "M9 5v14M15 5v14",
  stopp: "M7 7h10v10H7z",
  ueberspringen: "M6 5.5v13l9-6.5-9-6.5ZM18 5v14",
  fuettern: "M7 3v8a3 3 0 0 0 6 0V3M10 11v10M18 3c-1.5 2-2 4-2 6s.5 3 2 3 2-1 2-3-.5-4-2-6ZM18 12v9",
  spielen: "M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17ZM3.7 8.5h16.6M3.7 15.5h16.6M12 3.5c2.5 2.4 2.5 14.6 0 17M12 3.5c-2.5 2.4-2.5 14.6 0 17",
  streicheln: "M12 20s-7-4.4-7-9.2A4 4 0 0 1 12 8a4 4 0 0 1 7 2.8c0 4.8-7 9.2-7 9.2Z",
  verweis: "M10 13a4 4 0 0 0 5.7.3l3-3A4 4 0 0 0 13 4.6l-1.7 1.7M14 11a4 4 0 0 0-5.7-.3l-3 3A4 4 0 0 0 11 19.4l1.7-1.7",
};

export function icon(name, groesse = 20) {
  const pfad = PFADE[name] ?? PFADE.mehr;
  return svg(
    "svg",
    {
      viewBox: "0 0 24 24",
      width: groesse,
      height: groesse,
      fill: "none",
      stroke: "currentColor",
      "stroke-width": 1.6,
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
      "aria-hidden": "true",
    },
    svg("path", { d: pfad }),
  );
}

/** Gefüllte Fassung für Haken und Startknopf. */
export function iconGefuellt(name, groesse = 20) {
  const element = icon(name, groesse);
  element.setAttribute("fill", "currentColor");
  element.setAttribute("stroke", "none");
  return element;
}
