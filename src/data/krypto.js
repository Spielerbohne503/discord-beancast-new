/**
 * Verschlüsselung für den Abgleich.
 *
 * **Der Server sieht nie etwas.** Er bekommt einen Klumpen Bytes und eine Kennung, die
 * beide aus derselben Losung abgeleitet sind — und kann mit keinem von beidem etwas
 * anfangen. Das ist der Preis dafür, die Gründungsregel „kein Konto, keine Telemetrie“ so
 * weit wie möglich zu halten, obwohl Daten jetzt über ein Netz gehen.
 *
 * Aus der Losung werden **512 Bit** abgeleitet und in der Mitte geteilt: vorn der
 * Schlüssel, hinten die Raumkennung. Beides aus demselben teuren Ableitungsschritt — wer
 * die Kennung erraten will, muss denselben Aufwand treiben wie für den Schlüssel. Würde
 * die Kennung billig aus der Losung folgen, könnte man den Raum finden, ohne ihn zu
 * knacken; das verrät zwar keinen Inhalt, aber sehr wohl, dass es ihn gibt.
 *
 * Nichts davon steht in `domain/`: Das hier braucht `crypto.subtle` und damit einen
 * Browser.
 */

import { SYNC_VERSION } from "../domain/sync.js";

/**
 * Wie teuer die Ableitung ist.
 *
 * Hoch genug, dass Raten weh tut, niedrig genug, dass ein Telefon es in unter einer
 * Sekunde schafft. Gerechnet wird das genau **einmal** je Gerät; danach liegt das Ergebnis
 * in der Datenbank.
 */
const RUNDEN = 310_000;

/** Fest, weil es keinen Ort gäbe, an dem ein zufälliges Salz vor der Anmeldung stünde. */
const SALZ = new TextEncoder().encode(`petodo-sync-v${SYNC_VERSION}`);

/**
 * Leitet Schlüssel und Raumkennung aus der Losung ab.
 *
 * Dauert bewusst spürbar lange. Das Ergebnis wird gespeichert, die Losung nicht — wer das
 * Gerät in die Hand bekommt, hat ohnehin Zugriff auf die Aufgaben selbst.
 */
export async function ausLosung(losung) {
  const roh = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(String(losung)),
    "PBKDF2",
    false,
    ["deriveBits"],
  );

  const bits = new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: "PBKDF2", salt: SALZ, iterations: RUNDEN, hash: "SHA-256" },
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
 * Das ist der übliche Fall, nicht der Ausnahmefall: Auf dem zweiten Gerät wurde die Losung
 * vertippt. Ein Absturz wäre dafür die falsche Antwort — die Oberfläche sagt es lieber.
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
