/**
 * Die offene Seite.
 *
 * **Was das heißt, in einem Satz:** Wer die Adresse aufruft, sieht die Aufgaben und kann
 * sie ändern — ohne Koppeln, ohne Link, ohne irgendetwas. Die Seite gibt das Geheimnis
 * jedem heraus, der danach fragt. Das ist keine Schwachstelle, sondern der Zweck; wer sie
 * anschaltet, hat sich dafür entschieden.
 *
 * **Ausgeschaltet, solange `OEFFENTLICH` leer ist.** Ohne Eintrag antwortet dieser Weg mit
 * `{ offen: false }`, und alles bleibt beim Koppel-Link. Es gibt keinen Weg, das aus
 * Versehen einzuschalten.
 *
 * Der Wert gehört als **Secret** hinterlegt (`wrangler secret put OEFFENTLICH`) und nicht
 * in `wrangler.toml`: Herausgegeben wird er ohnehin an jeden Besucher, aber in der Datei
 * stünde er zusätzlich in der Projektgeschichte — und die ist auch dann noch da, wenn die
 * Seite längst wieder zu ist.
 */

/** 32 Byte als Base64url, dasselbe Maß wie jedes andere Geheimnis. */
const GEHEIMNIS = /^[A-Za-z0-9_-]{43}$/;

/**
 * Beantwortet `/offen`.
 *
 * @returns eine `Response`, oder `null` wenn der Pfad nicht hierher gehört
 */
export function offen(umgebung, adresse) {
  if (adresse.pathname.replace(/\/+$/, "") !== "/offen") return null;

  const geheimnis = String(umgebung.OEFFENTLICH ?? "").trim();
  const gueltig = GEHEIMNIS.test(geheimnis);

  return new Response(JSON.stringify(gueltig ? { offen: true, geheimnis } : { offen: false }), {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      // Nicht zwischenspeichern: Wird die Seite wieder zugemacht, soll das sofort gelten
      // und nicht erst, wenn irgendwo ein Zwischenspeicher abläuft.
      "Cache-Control": "no-store",
    },
  });
}
