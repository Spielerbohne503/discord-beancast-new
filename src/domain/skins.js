/**
 * Gestalten des Begleiters, freigeschaltet über Level.
 *
 * Level hatten bisher keine Folge — die Kurve lief ins Leere, und eine Zahl, die nichts
 * bewirkt, ist keine Belohnung, sondern eine Behauptung. Hier bekommt sie eine Folge, und
 * zwar die einzige, die dem Programm nicht schadet: **eine andere Farbe, sonst nichts.**
 *
 * Kein Laden, keine Währung, kein Zubehör. Wer eine Gestalt freischaltet, hat nichts, was
 * ein anderer nicht auch bekommt, indem er seine Aufgaben erledigt.
 *
 * Die Farben stehen hier und nicht in `balance.js`: Sie haben keine fachliche Bedeutung,
 * sie sind Gestaltung. Was sie freischaltet, ist die fachliche Zahl — und die steht drüben.
 */

/**
 * Die Gestalten, aufsteigend nach Level.
 *
 * `von` und `bis` sind die beiden Enden des Verlaufs im Kern; `bahn` die Farbe des
 * Trabanten. Mehr braucht eine Gestalt nicht — der Rest der Zeichnung bleibt gleich, damit
 * der Begleiter derselbe bleibt.
 */
export const SKINS = Object.freeze([
  { id: "violett", abLevel: 1, von: "#7c4dff", bis: "#a78bfa", bahn: "#d8fa4b" },
  { id: "kupfer", abLevel: 3, von: "#e07a3f", bis: "#f2b263", bahn: "#2b2b2b" },
  { id: "tuerkis", abLevel: 5, von: "#0fa3a3", bis: "#5fd6c8", bahn: "#ff8a00" },
  { id: "karmin", abLevel: 8, von: "#c62b45", bis: "#f2748c", bahn: "#ffd166" },
  { id: "tinte", abLevel: 12, von: "#1f2233", bis: "#4a5070", bahn: "#d8fa4b" },
  { id: "gold", abLevel: 18, von: "#b8860b", bis: "#f5d76e", bahn: "#1f2233" },
  { id: "nordlicht", abLevel: 25, von: "#1d976c", bis: "#93f9b9", bahn: "#7c4dff" },
]);

export const STANDARD_SKIN = SKINS[0].id;

/** Welche Gestalten auf diesem Level offenstehen. */
export function verfuegbar(level) {
  return SKINS.filter((skin) => skin.abLevel <= Math.max(1, level));
}

/**
 * Die Gestalt zu einer Kennung — mit Rückfall auf die erste.
 *
 * Rückfall statt Fehler, weil hier zwei Wege hereinführen, die schiefgehen können: eine
 * Sicherung aus einer späteren Fassung mit einer Gestalt, die es hier noch nicht gibt, und
 * ein Level, das durch eine zurückgenommene Belohnung gesunken ist.
 */
export function skinOder(id, level) {
  const offen = verfuegbar(level);
  return offen.find((skin) => skin.id === id) ?? offen[0] ?? SKINS[0];
}

/** Was mit dem nächsten Level dazukommt — `null`, wenn nichts mehr kommt. */
export function naechsteGestalt(level) {
  return SKINS.find((skin) => skin.abLevel > level) ?? null;
}
