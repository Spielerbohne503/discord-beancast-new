/**
 * Verschlüsselung für den Abgleich.
 *
 * **Der Server sieht nie etwas.** Er bekommt einen Klumpen Bytes und eine Kennung, die
 * beide aus demselben Geheimnis abgeleitet sind — und kann mit keinem von beidem etwas
 * anfangen. Das ist der Preis dafür, die Gründungsregel „kein Konto, keine Telemetrie“ so
 * weit wie möglich zu halten, obwohl Daten jetzt über ein Netz gehen.
 *
 * ## Warum keine Losung mehr
 *
 * Vorher tippte man auf jedem Gerät denselben Satz, und daraus wurden mit PBKDF2 über
 * 310 000 Runden Schlüssel und Raumkennung. Der teure Schritt war nötig, weil ein
 * ausgedachter Satz wenig Zufall enthält — man muss das Raten künstlich verteuern.
 *
 * Das Geheimnis kommt jetzt aus `crypto.getRandomValues`. 32 Byte echter Zufall sind nicht
 * zu raten, egal wie billig die Ableitung ist. Damit fällt PBKDF2 weg, und mit ihm die
 * Sekunde Wartezeit, das Tippen auf dem zweiten Gerät und die Aussicht, sich zu vertippen
 * und dann eine Fehlermeldung zu bekommen, die nach einem Serverfehler aussieht.
 *
 * Aufs zweite Gerät kommt das Geheimnis über den Koppel-Link (`domain/koppeln.js`) —
 * einmal einfügen statt zweimal abtippen.
 *
 * Geteilt wird mit HKDF: aus den 32 Byte werden 512 Bit, vorn der Schlüssel, hinten die
 * Raumkennung. Getrennt abgeleitet, damit die Kennung — die im Klartext über die Leitung
 * geht — nichts über den Schlüssel verrät.
 *
 * Nichts davon steht in `domain/`: Das hier braucht `crypto.subtle` und damit einen
 * Browser.
 */

import { SYNC_VERSION } from "../domain/sync.js";

/** Fest, weil es keinen Ort gäbe, an dem ein zufälliges Salz vor dem Koppeln stünde. */
const SALZ = new TextEncoder().encode(`petodo-sync-v${SYNC_VERSION}`);

/**
 * Ein frisches Geheimnis. 32 Byte aus dem Zufallsgenerator des Systems.
 *
 * Dasselbe Maß wie die Raumkennung, die der Server annimmt: 43 Zeichen Base64url.
 */
export function neuesGeheimnis() {
  return alsBase64Url(crypto.getRandomValues(new Uint8Array(32)));
}

/**
 * Leitet Schlüssel und Raumkennung aus dem Geheimnis ab.
 *
 * Geht sofort — im Gegensatz zu früher gibt es hier nichts zu verteuern.
 */
export async function ausGeheimnis(geheimnis) {
  const roh = await crypto.subtle.importKey(
    "raw",
    ausBase64Url(String(geheimnis)),
    "HKDF",
    false,
    ["deriveBits"],
  );

  const bits = new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: "HKDF", hash: "SHA-256", salt: SALZ, info: new TextEncoder().encode("schluessel+raum") },
      roh,
      512,
    ),
  );

  return {
    schluessel: alsBase64(bits.slice(0, 32)),
    raum: alsBase64Url(bits.slice(32, 64)),
  };
}

/** Der gespeicherte Schlüssel zurück in etwas, mit dem `crypto.subtle` rechnen kann. */
async function schluesselOeffnen(schluessel) {
  return crypto.subtle.importKey("raw", ausBase64(schluessel), "AES-GCM", false, [
    "encrypt",
    "decrypt",
  ]);
}

/**
 * Verschlüsselt.
 *
 * Jedes Mal ein neuer Zufallsvektor. Denselben zweimal zu benutzen macht AES-GCM
 * rechnerisch aufbrechbar — deshalb kommt er hier aus `crypto.getRandomValues` und
 * nirgendwo sonst her.
 */
export async function verschluesseln(schluessel, text) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const daten = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    await schluesselOeffnen(schluessel),
    new TextEncoder().encode(text),
  );

  return { iv: alsBase64(iv), daten: alsBase64(new Uint8Array(daten)) };
}

/**
 * Entschlüsselt. Gibt `null` zurück, wenn es nicht aufgeht.
 *
 * Der Fall ist selten geworden, seit das Geheimnis nicht mehr getippt wird — aber nicht
 * unmöglich: ein halb eingefügter Link, ein Raum aus einer älteren Fassung. Ein Absturz
 * wäre dafür die falsche Antwort; die Oberfläche sagt es lieber.
 */
export async function entschluesseln(schluessel, iv, daten) {
  try {
    const klar = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: ausBase64(iv) },
      await schluesselOeffnen(schluessel),
      ausBase64(daten),
    );
    return new TextDecoder().decode(klar);
  } catch {
    return null;
  }
}

function alsBase64(bytes) {
  return btoa(String.fromCharCode(...bytes));
}

function alsBase64Url(bytes) {
  return alsBase64(bytes).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function ausBase64(text) {
  return Uint8Array.from(atob(text), (zeichen) => zeichen.charCodeAt(0));
}

function ausBase64Url(text) {
  const gerade = text.replaceAll("-", "+").replaceAll("_", "/");
  return ausBase64(gerade.padEnd(Math.ceil(gerade.length / 4) * 4, "="));
}
