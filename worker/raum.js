/**
 * Ein Raum — der abgelegte Stand einer Losung.
 *
 * Ein Durable Object je Raum, und **das ist der Punkt**: Ein Objekt arbeitet einen Aufruf
 * nach dem anderen ab. Damit ist „lies den Stempel, vergleiche, schreibe“ tatsächlich ein
 * Schritt und nicht drei mit Lücken dazwischen.
 *
 * Vorher lag der Stand in KV. KV ist letztlich-konsistent: Zwei Geräte konnten denselben
 * Stempel lesen, beide für aktuell halten und beide schreiben — genau der lautlose
 * Datenverlust, gegen den der Stempel überhaupt da ist. Die Prüfung stand im Quelltext und
 * hielt nicht, was sie versprach.
 *
 * Der zweite Grund ist schnöder: Für KV muss jemand von Hand im Dashboard eine Namespace
 * anlegen und ihre Kennung eintragen. Wer das nicht tut, hat einen Abgleich, der nie
 * funktioniert hat und es auch nicht sagt. Ein Durable Object entsteht beim Ausrollen von
 * selbst — es gibt keinen Schritt, den man vergessen kann.
 *
 * **Gelesen wird hier nichts.** Was ankommt, ist ein Klumpen Bytes; der Schlüssel dazu
 * verlässt das Gerät nie.
 */

import { DurableObject } from "cloudflare:workers";

import { istAbgelaufen } from "./haltbarkeit.js";

export class Raum extends DurableObject {

  constructor(zustand, umgebung) {
    super(zustand, umgebung);

    // Synchron im Konstruktor: Danach ist die Tabelle für jeden Aufruf da, und keine
    // Methode muss sich fragen, ob sie die erste ist.
    this.ctx.storage.sql.exec(
      `CREATE TABLE IF NOT EXISTS stand (
         nur_eine  INTEGER PRIMARY KEY,
         umschlag  TEXT    NOT NULL,
         stempel   TEXT    NOT NULL,
         abgelegt  INTEGER NOT NULL
       )`,
    );
  }

  /** Der abgelegte Stand, oder `null`. */
  lesen() {
    const zeilen = this.ctx.storage.sql
      .exec("SELECT umschlag, stempel, abgelegt FROM stand WHERE nur_eine = 1")
      .toArray();

    if (zeilen.length === 0) return null;

    // Abgelaufenes gilt als nicht vorhanden — und wird gleich mit weggeräumt, damit ein
    // vergessener Raum nicht ewig Platz belegt.
    if (istAbgelaufen(zeilen[0].abgelegt, Date.now())) {
      this.raeumen();
      return null;
    }

    return { umschlag: zeilen[0].umschlag, stempel: zeilen[0].stempel };
  }

  /**
   * Legt ab — aber nur, wenn der Ableger vom aktuellen Stand ausgeht.
   *
   * Lesen und Schreiben liegen in **einem** Aufruf. Weil das Objekt nur eine Sache
   * gleichzeitig tut, kann zwischen der Prüfung und dem Schreiben nichts dazwischenkommen.
   */
  ablegen(umschlag, { erwartet, nurNeu }) {
    const vorhanden = this.lesen();

    if (nurNeu && vorhanden !== null) return { fehler: "gibt es schon" };
    if (!nurNeu && erwartet === null) return { fehler: "If-Match fehlt", status: 428 };
    if (!nurNeu && erwartet !== (vorhanden?.stempel ?? null)) return { fehler: "veralteter Stand" };

    const stempel = `"${crypto.randomUUID()}"`;
    this.ctx.storage.sql.exec(
      `INSERT INTO stand (nur_eine, umschlag, stempel, abgelegt) VALUES (1, ?, ?, ?)
         ON CONFLICT (nur_eine) DO UPDATE SET umschlag = excluded.umschlag,
                                              stempel  = excluded.stempel,
                                              abgelegt = excluded.abgelegt`,
      umschlag,
      stempel,
      Date.now(),
    );

    return { stempel };
  }

  raeumen() {
    this.ctx.storage.sql.exec("DELETE FROM stand");
  }
}
