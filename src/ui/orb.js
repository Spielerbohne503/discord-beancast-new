/**
 * Der Begleiter als leuchtende Kugel.
 *
 * Gezeichnet wird mit Verläufen und einem SVG-Ring, nicht mit einem Bild und **nicht mit
 * einem Emoji**: Ein Emoji sieht in jeder Größe nach Aufkleber aus, und genau der Eindruck
 * soll hier nicht entstehen. Der Ring trägt den Levelfortschritt.
 */

import { levelForXp, levelProgress, stageOf } from "../domain/pet.js";
import { h, svg } from "./dom.js";

const UMFANG = 2 * Math.PI * 46;

export function orb(state, { groesse = 148, ring = true, bahn = true } = {}) {
  const stufe = stageOf(state.values);
  const laune = state.values.mood / 100;
  const fortschritt = levelProgress(state.xp);

  const element = h(
    "div.orb",
    {
      dataset: { stufe },
      style: { "--orb-groesse": `${groesse}px`, "--laune": laune.toFixed(3) },
      role: "img",
      "aria-label": `${Math.round(state.values.mood)} % Laune, Stufe ${levelForXp(state.xp)}`,
    },
    h("div.orb__korona"),
    ring ? fortschrittsring(fortschritt) : null,
    h("div.orb__koerper", {}, h("div.orb__kern")),
    // Ein Trabant auf einer gekippten Bahn. Der Begleiter ist ein Himmelskörper, kein
    // Gesicht — zwei Punkte als Augen hätten aus jeder Größe einen Aufkleber gemacht.
    bahn ? h("div.orb__bahn", {}, h("i.orb__trabant")) : null,
  );

  return element;
}

function fortschrittsring(anteil) {
  return svg(
    "svg",
    { class: "orb__ring", viewBox: "0 0 100 100", "aria-hidden": "true" },
    svg(
      "defs",
      {},
      svg(
        "linearGradient",
        { id: "orbVerlauf", x1: "0", y1: "0", x2: "1", y2: "1" },
        svg("stop", { offset: "0", "stop-color": "var(--orb-a)" }),
        svg("stop", { offset: "1", "stop-color": "var(--orb-b)" }),
      ),
    ),
    svg("circle", { class: "orb__ring-spur", cx: 50, cy: 50, r: 46 }),
    svg("circle", {
      class: "orb__ring-fortschritt",
      cx: 50,
      cy: 50,
      r: 46,
      "stroke-dasharray": UMFANG.toFixed(2),
      "stroke-dashoffset": (UMFANG * (1 - anteil)).toFixed(2),
    }),
  );
}

/**
 * Eine kurze Regung.
 *
 * Die Klasse fliegt nach der Bewegung wieder heraus, damit dieselbe Regung gleich noch
 * einmal ausgelöst werden kann.
 */
export function regung(element, art) {
  if (!element) return;
  const klasse = `orb--${art}`;
  element.classList.remove(klasse);
  // Erzwingt einen Umbruch, sonst sieht der Browser keine Änderung.
  void element.offsetWidth;
  element.classList.add(klasse);
  element.addEventListener("animationend", () => element.classList.remove(klasse), { once: true });
}
