/**
 * Wie lange ein abgelegter Stand liegen bleibt.
 *
 * Steht hier und nicht in `raum.js`, weil `raum.js` `cloudflare:workers` importiert und
 * damit außerhalb eines Workers nicht ladbar ist. Eine Regel, die sich nur durch Warten von
 * 400 Tagen prüfen ließe, wäre keine geprüfte Regel.
 */

/** Wird nichts mehr abgelegt, fällt der Raum irgendwann von selbst weg. */
export const HALTBARKEIT_MS = 400 * 86_400_000;

/**
 * Länger als ein Jahr nichts abgelegt? Dann gilt der Stand als nicht vorhanden.
 *
 * Großzügiger als die Grabsteine in `domain/sync.js` (ein Jahr): Wer ein Gerät ein Jahr
 * lang nicht anfasst, soll seinen Stand noch vorfinden und nicht bei null anfangen.
 */
export function istAbgelaufen(abgelegt, jetzt) {
  return jetzt - abgelegt > HALTBARKEIT_MS;
}
