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
import { groupToday } from "../../domain/tasks.js";
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
    orb(state.pet, { groesse: 54, ring: false }),
    h(
      "div.streifen__text",
      {},
      h("span.streifen__stufe", {}, STAGE_NAMES[stageOf(state.pet.values)]),
      state.speechText ? h("span.streifen__satz", {}, state.speechText) : null,
    ),
    tagesstand(),
  );
}

/**
 * Der Tagesstand: geschafft von insgesamt.
 *
 * Die drei Bedürfniswerte standen hier vorher als schmale Balken — sie sagen aber nichts
 * darüber, wie der **Tag** läuft, und dafür ist die Heute-Ansicht da. Wer die Werte sehen
 * will, tippt auf den Streifen.
 */
function tagesstand() {
  const brett = groupToday(state.tasks, state.now);
  const geschafft = brett.doneToday.length;
  const gesamt = geschafft + brett.openCount;

  return h(
    "div.streifen__stand",
    { title: S.today_done_today },
    h("span.streifen__zahl", {}, `${geschafft}/${gesamt}`),
    h(
      "div.wert__balken",
      {},
      h("div.wert__fuellung.wert__fuellung--energie", {
        style: { width: `${gesamt === 0 ? 0 : (geschafft / gesamt) * 100}%` },
      }),
    ),
  );
}

