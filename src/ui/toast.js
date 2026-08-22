/**
 * Eine kurze Meldung mit „Rückgängig“.
 *
 * Sie ersetzt jede Rückfrage vor dem Löschen: Ein Dialog, der jedes Mal fragt, wird nach
 * drei Tagen blind weggeklickt — eine Meldung, die man zurücknehmen kann, nicht.
 */

import { h } from "./dom.js";
import { S } from "./strings.js";

let vorhandene = null;
let zeitgeber = null;

export function meldung(text, { rueckgaengig = null, dauer = 5200 } = {}) {
  verstecken();

  const element = h(
    "div.meldung",
    { role: "status", "aria-live": "polite" },
    h("span.meldung__text", { title: text }, text),
    rueckgaengig
      ? h(
          "button.meldung__knopf",
          {
            onclick: async () => {
              verstecken();
              await rueckgaengig();
            },
          },
          S.action_undo,
        )
      : null,
  );

  document.body.append(element);
  vorhandene = element;
  zeitgeber = setTimeout(verstecken, dauer);
}

export function verstecken() {
  clearTimeout(zeitgeber);
  vorhandene?.remove();
  vorhandene = null;
}
