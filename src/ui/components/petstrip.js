/**
 * Der Begleiter-Streifen über der Heute-Liste — nur auf dem Telefon.
 *
 * Am Schreibtisch steht der Begleiter in der Nebenspalte; ihn dort **zusätzlich** über die
 * Liste zu setzen, wäre dieselbe Sache zweimal auf einem Bildschirm.
 *
 * Er ist keine zweite Begleiter-Ansicht: Zustand, ein Satz, drei schmale Balken. Wer mehr
 * will, tippt darauf.
 */

import { stageOf } from "../../domain/pet.js";
import { navigieren, state } from "../store.js";
import { h } from "../dom.js";
import { S, STAGE_NAMES } from "../strings.js";
import { orb } from "../orb.js";

export function petStrip() {
  if (state.pet === null) return null;

  return h(
    "button.streifen",
    {
      type: "button",
      onclick: () => navigieren("companion"),
      "aria-label": S.nav_companion,
    },
    orb(state.pet, { groesse: 56, ring: false, bahn: false }),
    h(
      "div.streifen__text",
      {},
      h("span.begleiter__stufe", {}, STAGE_NAMES[stageOf(state.pet.values)]),
      state.speechText ? h("span.streifen__satz", {}, state.speechText) : null,
    ),
    h(
      "div.streifen__balken",
      {},
      balken("energie", state.pet.values.energy, S.pet_energy),
      balken("saettigung", state.pet.values.satiety, S.pet_satiety),
      balken("laune", state.pet.values.mood, S.pet_mood),
    ),
  );
}

function balken(art, wert, name) {
  return h(
    "div.wert__balken",
    { title: `${name}: ${Math.round(wert)} %` },
    h(`div.wert__fuellung.wert__fuellung--${art}`, { style: { width: `${wert}%` } }),
  );
}
