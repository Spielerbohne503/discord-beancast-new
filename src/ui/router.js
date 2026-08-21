/**
 * Der Weg durch die App steht in der Adresse.
 *
 * Eine Webseite, deren Zurück-Knopf nichts tut, fühlt sich kaputt an — und ein Neuladen
 * darf einen nicht aus der Ansicht werfen, in der man gerade war. Beides kostet hier
 * genau einen Hash.
 *
 * `#/listen/l/<kennung>` trägt zusätzlich die gewählte Liste: Ein Verweis auf eine Liste
 * lässt sich damit ablegen und wieder aufrufen.
 */

import { Scope } from "../domain/filter.js";

const ZU_HASH = {
  today: "heute",
  browse: "listen",
  focus: "fokus",
  companion: "begleiter",
  habits: "gewohnheiten",
  stats: "rueckblick",
  settings: "einstellungen",
  more: "mehr",
};

const ZU_ROUTE = Object.fromEntries(Object.entries(ZU_HASH).map(([route, teil]) => [teil, route]));

const BEREICH_ZU_HASH = {
  [Scope.ALL_OPEN]: "offen",
  [Scope.NEXT_SEVEN_DAYS]: "7tage",
  [Scope.COMPLETED]: "erledigt",
};

const HASH_ZU_BEREICH = Object.fromEntries(
  Object.entries(BEREICH_ZU_HASH).map(([kind, teil]) => [teil, kind]),
);

export function hashFuer(route, scope = null) {
  const teil = ZU_HASH[route] ?? ZU_HASH.today;
  if (route !== "browse" || scope === null) return `#/${teil}`;
  if (scope.kind === Scope.LIST) return `#/${teil}/l/${scope.listId}`;
  return `#/${teil}/${BEREICH_ZU_HASH[scope.kind] ?? "offen"}`;
}

/** Liest die Adresse. Unbekanntes landet auf „Heute“ statt auf einer leeren Seite. */
export function ausHash(hash) {
  const teile = String(hash ?? "")
    .replace(/^#\/?/, "")
    .split("/")
    .filter(Boolean);

  const route = ZU_ROUTE[teile[0]] ?? "today";
  if (route !== "browse") return { route, scope: null };

  if (teile[1] === "l" && teile[2]) return { route, scope: { kind: Scope.LIST, listId: teile[2] } };
  return { route, scope: { kind: HASH_ZU_BEREICH[teile[1]] ?? Scope.ALL_OPEN } };
}
