/**
 * Die Begleiter-Ansicht.
 *
 * Drei Werte, drei Handlungen, ein Satz. Die Knöpfe verschwinden während der Sperrzeit
 * nicht — sie sagen, wie lange noch. Ein Knopf, der einfach weg ist, wirkt kaputt.
 */

import {
  RewardType, SpeechCategory, isRewardAllowed, lastActionAt, levelForXp,
  remainingCooldownMs, stageOf, xpToNextLevel,
} from "../../domain/pet.js";
import { award } from "../../data/petstore.js";
import { aktualisieren, reagieren, state } from "../store.js";
import { fuellen, h } from "../dom.js";
import { icon } from "../icons.js";
import { S, STAGE_NAMES } from "../strings.js";
import { formatDuration } from "../format.js";
import { orb, regung } from "../orb.js";

const HANDLUNGEN = [
  { typ: RewardType.FEED, text: S.pet_feed, symbol: "fuettern", regung: "gefuettert", satz: SpeechCategory.FED },
  { typ: RewardType.PLAY, text: S.pet_play, symbol: "spielen", regung: "gespielt", satz: SpeechCategory.PLAYED },
  { typ: RewardType.PAT, text: S.pet_pat, symbol: "streicheln", regung: "gestreichelt", satz: SpeechCategory.PATTED },
];

export function companionView() {
  const element = h("div.karte.begleiter");
  let orbElement = null;

  async function handeln(handlung) {
    const ergebnis = await award(handlung.typ, Date.now(), state.tasks);
    if (ergebnis === null) return;
    regung(orbElement, handlung.regung);
    await aktualisieren();
    reagieren(handlung.satz);
  }

  function update() {
    if (state.pet === null) return;

    const pet = state.pet;
    orbElement = orb(pet, { groesse: 168 });

    fuellen(
      element,
      h(
        "div.begleiter__kopfzeile",
        {},
        h("span.begleiter__stufe", {}, STAGE_NAMES[stageOf(pet.values)]),
        h(
          "span.begleiter__level",
          {},
          `${S.pet_level(levelForXp(pet.xp))} · ${S.pet_xp_to_next(xpToNextLevel(pet.xp))}`,
        ),
      ),
      orbElement,
      state.speechText ? h("p.blase", {}, state.speechText) : null,
      h(
        "div.begleiter__werte",
        {},
        wert(S.pet_energy, "energie", pet.values.energy),
        wert(S.pet_satiety, "saettigung", pet.values.satiety),
        wert(S.pet_mood, "laune", pet.values.mood),
      ),
      h(
        "div.begleiter__knoepfe",
        {},
        HANDLUNGEN.map((handlung) => knopf(handlung, pet, handeln)),
      ),
      h(
        "div.feld",
        {},
        h(
          "span.feld__beschriftung",
          {},
          `${S.pet_load}: ${state.load.toFixed(1)}`,
        ),
        h("span.feld__hinweis", {}, S.pet_load_hint),
      ),
    );
  }

  return { el: element, update };
}

function wert(name, art, zahl) {
  return h(
    "div.wert",
    {},
    h("span", {}, name),
    h(
      "div.wert__balken",
      {},
      h(`div.wert__fuellung.wert__fuellung--${art}`, { style: { width: `${zahl}%` } }),
    ),
    h("span.wert__zahl", {}, String(Math.round(zahl))),
  );
}

function knopf(handlung, pet, handeln) {
  const zuletzt = lastActionAt(pet, handlung.typ);
  const erlaubt = isRewardAllowed(handlung.typ, zuletzt, state.now);
  const rest = remainingCooldownMs(handlung.typ, zuletzt, state.now);

  return h(
    "button.knopf.begleiter__knopf",
    {
      type: "button",
      disabled: !erlaubt,
      onclick: () => void handeln(handlung),
    },
    icon(handlung.symbol, 20),
    h("span", {}, handlung.text),
    erlaubt ? null : h("span.begleiter__knopf-rest", {}, S.pet_cooldown(formatDuration(rest))),
  );
}
