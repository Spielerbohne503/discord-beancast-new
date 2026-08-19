/**
 * Schlüssel und Sortierung.
 *
 * Beides ist eine Einbahnstraße: UUIDs statt fortlaufender Zahlen, damit zwei Geräte
 * später ohne Absprache anlegen können; Sortierung als Bruchindex, damit ein Verschieben
 * genau eine Zeile schreibt statt der halben Liste.
 */

/** Eine UUID v4. Nimmt die Browser-Funktion, wenn es sie gibt. */
export function uuid() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // Rückfall für ältere Umgebungen und für Tests unter Node ohne Web-Crypto.
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (character) => {
    const random = (Math.random() * 16) | 0;
    const value = character === "x" ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

/** Base-36, aufsteigend sortierbar und ASCII-konform. */
const ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
const MIN_CHAR = ALPHABET[0];

export const FractionalIndex = {
  ALPHABET,

  isValid(key) {
    if (typeof key !== "string" || key.length === 0) return false;
    if (key.endsWith(MIN_CHAR)) return false; // Endnullen wären mehrdeutig.
    return [...key].every((character) => ALPHABET.includes(character));
  },

  /** Schlüssel für das erste Element einer leeren Liste. */
  initial() {
    return this.between(null, null);
  },

  /**
   * Ein Schlüssel echt zwischen [prev] und [next].
   *
   * `null` heißt „kein Nachbar“: [prev] = null ist der Anfang, [next] = null das Ende.
   */
  between(prev, next) {
    if (prev !== null && !this.isValid(prev)) throw new Error(`Ungültiger Schlüssel: ${prev}`);
    if (next !== null && !this.isValid(next)) throw new Error(`Ungültiger Schlüssel: ${next}`);
    if (prev !== null && next !== null && prev >= next) {
      throw new Error(`prev muss kleiner als next sein, war: ${prev} >= ${next}`);
    }

    const lower = prev ?? "";
    let upper = next;
    let result = "";
    let index = 0;

    for (;;) {
      const a = lower[index] ?? MIN_CHAR;
      const b = upper?.[index] ?? null;

      if (b !== null && a === b) {
        result += a;
        index++;
        continue;
      }

      const aIndex = ALPHABET.indexOf(a);
      const bIndex = b !== null ? ALPHABET.indexOf(b) : ALPHABET.length;

      if (bIndex - aIndex > 1) {
        return result + ALPHABET[Math.floor((aIndex + bIndex) / 2)];
      }

      // Benachbarte Zeichen: das untere übernehmen, die Obergrenze entfällt damit.
      result += a;
      index++;
      upper = null;
    }
  },

  after(last) {
    return this.between(last ?? null, null);
  },

  before(first) {
    return this.between(null, first ?? null);
  },

  /**
   * Wie [after], aber robust gegen beschädigte Schlüssel.
   *
   * An der Grenze zur Datenbank kann ein unbrauchbarer Wert liegen — etwa aus einer von
   * Hand bearbeiteten Sicherung. Das darf die Reihenfolge kosten, nicht die Fähigkeit,
   * überhaupt etwas anzulegen.
   */
  afterOrInitial(last) {
    return last && this.isValid(last) ? this.after(last) : this.initial();
  },
};

/**
 * Der neue Schlüssel für ein Element, das von [from] nach [to] wandert.
 *
 * Gibt `null` zurück, wenn sich nichts ändert oder die Nachbarn unbrauchbar sind — dann
 * wird auch nichts geschrieben.
 */
export function keyForMove(keys, from, to) {
  if (from === to) return null;
  if (from < 0 || from >= keys.length) return null;
  if (to < 0 || to >= keys.length) return null;

  const remaining = keys.slice();
  remaining.splice(from, 1);
  const previous = to - 1 >= 0 ? remaining[to - 1] ?? null : null;
  const next = remaining[to] ?? null;

  if (previous !== null && !FractionalIndex.isValid(previous)) return null;
  if (next !== null && !FractionalIndex.isValid(next)) return null;
  if (previous !== null && next !== null && previous >= next) return null;

  return FractionalIndex.between(previous, next);
}

/** Verschiebung auf eine Liste anwenden — für die Vorschau beim Ziehen. */
export function moveItem(items, from, to) {
  if (from === to) return items;
  if (from < 0 || from >= items.length || to < 0 || to >= items.length) return items;

  const copy = items.slice();
  const [moved] = copy.splice(from, 1);
  copy.splice(to, 0, moved);
  return copy;
}
