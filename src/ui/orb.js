/**
 * Der Begleiter als Bauzeichnung.
 *
 * Eine Scheibe mit hartem Rand, eine gestrichelte Umlaufbahn, ein Trabant, außen der
 * Fortschrittsring. Kein Emoji, kein Gesicht — beides sah in jeder Größe nach Aufkleber
 * aus. Der Kern ist das einzige Licht im ganzen Programm; der Begleiter ist auch das
 * einzige, was hier lebt.
 */

import { levelForXp, levelProgress, stageOf } from "../domain/pet.js";
import { skinOder } from "../domain/skins.js";
import { h, svg } from "./dom.js";
import { state } from "./store.js";

const UMFANG = 2 * Math.PI * 46;

export function orb(pet, { groesse = 148, ring = true, bahn = true } = {}) {
  const stufe = stageOf(pet.values);
  const laune = pet.values.mood / 100;
  const fortschritt = levelProgress(pet.xp);
  const gestalt = skinOder(state.settings?.skin, levelForXp(pet.xp));

  return h(
    "div.orb",
    {
      dataset: { stufe },
      style: {
        "--orb-groesse": `${groesse}px`,
        "--laune": laune.toFixed(3),
        // Die Gestalt färbt nur; Form, Bahn und Ring bleiben gleich, damit der Begleiter
        // derselbe bleibt. Bei Krankheit übernimmt der Zustand — wer krank ist, sieht
        // nicht golden aus.
        "--gestalt-von": gestalt.von,
        "--gestalt-bis": gestalt.bis,
        "--gestalt-bahn": gestalt.bahn,
      },
      role: "img",
      "aria-label": `${Math.round(pet.values.mood)} % Laune, Stufe ${levelForXp(pet.xp)}`,
    },
    ring ? fortschrittsring(fortschritt) : null,
    // Die Bahn liegt **hinter** der Scheibe. Davor gezogen sähe sie aus wie ein
    // Drahtkäfig über der Kugel; dahinter liest sie sich als Ring.
    bahn ? h("div.orb__bahn", {}, h("i.orb__trabant")) : null,
    h("div.orb__scheibe", {}, h("div.orb__kern")),
  );
}

/**
 * Der Fortschrittsring.
 *
 * Zwei Linien übereinander: erst eine dicke in Tinte, dann eine dünnere in Acid. Acid
 * allein verschwindet auf hellem Papier — die Tintenkante hält es zusammen.
 */
function fortschrittsring(anteil) {
  const rest = (UMFANG * (1 - anteil)).toFixed(2);

  return svg(
    "svg",
    { class: "orb__ring", viewBox: "0 0 100 100", "aria-hidden": "true" },
    svg("circle", { class: "orb__ring-spur", cx: 50, cy: 50, r: 46 }),
    svg("circle", {
      class: "orb__ring-kante",
      cx: 50,
      cy: 50,
      r: 46,
      "stroke-dasharray": UMFANG.toFixed(2),
      "stroke-dashoffset": rest,
    }),
    svg("circle", {
      class: "orb__ring-fortschritt",
      cx: 50,
      cy: 50,
      r: 46,
      "stroke-dasharray": UMFANG.toFixed(2),
      "stroke-dashoffset": rest,
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
