/**
 * Der erste Start.
 *
 * Ein Bildschirm, ein Satz, ein Knopf. Was hier steht, ist die einzige Regel, die man
 * kennen muss: **Aufschreiben schadet dem Begleiter nie** — nur Liegenlassen tut das.
 * Ohne diesen Satz hört man auf, Dinge einzutragen, und dann ist die App tot.
 *
 * Keine Rechtefrage hier: Die Erlaubnis für Erinnerungen wird erst gestellt, wenn man sie
 * in den Einstellungen einschaltet. Ein Dialog, den man nicht erwartet hat, wird
 * weggeklickt — und danach ist er für immer weg.
 */

import * as repo from "../data/repo.js";
import { h } from "./dom.js";
import { S } from "./strings.js";
import { orb } from "./orb.js";
import { INITIAL_VALUES } from "../domain/pet.js";

export function willkommenZeigen(beiSchluss) {
  const dialog = h(
    "dialog.dialog",
    { onclose: () => dialog.remove() },
    h(
      "div.dialog__koerper",
      { style: { "align-items": "center", "text-align": "center", gap: "24px" } },
      orb({ values: INITIAL_VALUES, xp: 0 }, { groesse: 132 }),
      h("h2", {}, S.onboarding_title),
      h("p.feld__hinweis", {}, S.onboarding_body),
      h(
        "button.knopf.knopf--haupt",
        {
          onclick: async () => {
            await repo.saveSetting("onboardingDone", true);
            dialog.close();
            beiSchluss?.();
          },
        },
        S.onboarding_start,
      ),
    ),
  );

  document.body.append(dialog);
  dialog.showModal();
}
