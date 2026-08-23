/**
 * Der Koppel-Link.
 *
 * Ein Gerät koppeln heißt: ihm sagen, **wo** die Ablage liegt und **womit** sie
 * aufgeschlossen wird. Beides steckt in einer Zeile, damit es ein Handgriff bleibt und
 * nicht zwei Felder, von denen man das zweite falsch abtippt.
 *
 *     https://petodo.beispiel.workers.dev/#koppeln=<43 Zeichen>
 *
 * Das Geheimnis steht hinter der Raute. **Das ist keine Kosmetik:** Was hinter der Raute
 * steht, schickt ein Browser nie an einen Server — es taucht in keinem Zugriffsprotokoll
 * auf, in keinem `Referer` und in keinem Zwischenspeicher unterwegs.
 *
 * Gelesen wird großzügig: Ein Link, der durch eine Nachrichten-App gelaufen ist, hat
 * Leerzeichen, Zeilenumbrüche oder spitze Klammern um sich. Wer koppeln will, soll
 * einfügen können, was in der Zwischenablage liegt, statt vorher zu putzen.
 */

/** Die Marke im Link. Ändert sich nie — alte Links sollen weiter aufgehen. */
const MARKE = "#koppeln=";

/**
 * 32 Byte als Base64url — 43 Zeichen ohne Auffüllzeichen.
 *
 * Dasselbe Maß wie die Raumkennung, die der Server annimmt (`worker/index.js`). Wer das
 * hier ändert, muss es dort mitändern.
 */
const GEHEIMNIS = /^[A-Za-z0-9_-]{43}$/;

/** Zeichen, die eine eingefügte Zeile umklammern, ohne dazuzugehören. */
const BEIWERK = "<>\"'()[[]],;";

export function istGeheimnis(text) {
  return GEHEIMNIS.test(String(text ?? ""));
}

/** Baut den Link, den das andere Gerät bekommt. */
export function koppelLink(adresse, geheimnis) {
  return `${String(adresse ?? "").replace(/\/+$/, "")}/${MARKE}${geheimnis}`;
}

/**
 * Liest Adresse und Geheimnis aus dem, was jemand eingefügt hat.
 *
 * Gibt `null` zurück, wenn nichts Brauchbares drinsteht — das ist der übliche Fall beim
 * Einfügen des Falschen und kein Grund für einen Absturz.
 *
 * Die Adresse darf fehlen: Dann steht dort nur das Geheimnis, und der Aufrufer setzt
 * seine eigene Adresse ein. Im Browser ist das der Normalfall, weil der Link dort ohnehin
 * schon an der richtigen Stelle geöffnet wurde.
 */
export function ausKoppelLink(text) {
  const zeile = putzen(text);
  if (zeile.length === 0) return null;

  const marke = zeile.indexOf(MARKE);
  if (marke === -1) {
    // Nur das Geheimnis, ohne Link drumherum.
    return istGeheimnis(zeile) ? { adresse: null, geheimnis: zeile } : null;
  }

  const geheimnis = putzen(zeile.slice(marke + MARKE.length).split("&")[0]);
  if (!istGeheimnis(geheimnis)) return null;

  const vorne = zeile.slice(0, marke).replace(/\/+$/, "");
  return { adresse: istAdresse(vorne) ? vorne : null, geheimnis };
}

/**
 * Nur `http` und `https`.
 *
 * Ohne diese Prüfung schriebe ein eingefügtes `javascript:…` in die Einstellungen, und die
 * Adresse der Ablage ist genau die Zeichenkette, an die später Anfragen gehen.
 */
function istAdresse(text) {
  const klein = text.toLowerCase();
  return (klein.startsWith("http://") || klein.startsWith("https://")) && text.length > 8;
}

function putzen(text) {
  let wert = String(text ?? "").trim();

  while (wert.length > 0 && BEIWERK.includes(wert[0])) wert = wert.slice(1);
  while (wert.length > 0 && BEIWERK.includes(wert[wert.length - 1])) wert = wert.slice(0, -1);

  return wert.trim();
}
