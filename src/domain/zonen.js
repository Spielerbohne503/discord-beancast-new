/**
 * Wandzeit in einer Zeitzone → Zeitpunkt.
 *
 * Auf einem Gerät braucht man das nie: Dort ist die Ortszeit die des Nutzers, und
 * `time.js` rechnet damit. Im Worker ist die Ortszeit **UTC** — er steht nirgends, wo
 * jemand wohnt. Wer dort „Freitag um 18:30“ über `atTime` rechnet, legt den Termin auf
 * 18:30 UTC. In Berlin klingelt der dann um 20:30, und niemand versteht, warum.
 *
 * Deshalb steht die Zone ausdrücklich in `wrangler.toml` und wandert hier durch. Gerechnet
 * wird über `Intl` — dieselbe Zonendatenbank, die auch das Telefon benutzt, also derselbe
 * Sprung zur Sommerzeit.
 *
 * Rein: keine Uhr, kein Netz. Der Tag und die Minute kommen herein.
 */

import { isoDate } from "./time.js";

/**
 * Der Zeitpunkt, an dem in [zone] am Kalendertag [tag] die Minute [minuten] schlägt.
 *
 * Zweimal gerechnet, und das ist kein Zierrat: Der Versatz einer Zone hängt vom Zeitpunkt
 * ab, den man erst sucht. Der erste Durchgang schätzt, der zweite trifft — außer in der
 * einen Stunde, die es bei der Umstellung nicht gibt, und dort gibt es keine richtige
 * Antwort, nur eine nachvollziehbare.
 */
export function zeitpunktIn(zone, tag, minuten = 0) {
  const [jahr, monat, tagImMonat] = isoDate(tag).split("-").map(Number);
  const wand = Date.UTC(jahr, monat - 1, tagImMonat, Math.floor(minuten / 60), minuten % 60);

  const geschaetzt = wand - versatzVon(zone, wand);
  return wand - versatzVon(zone, geschaetzt);
}

/** Der Kalendertag, den [zeitpunkt] in [zone] hat. */
export function tagIn(zone, zeitpunkt) {
  const teile = felder(zone, zeitpunkt);
  return Math.floor(Date.UTC(teile.jahr, teile.monat - 1, teile.tag) / 86_400_000);
}

/**
 * Der Versatz der Zone zu UTC an diesem Zeitpunkt, in Millisekunden.
 *
 * Gerechnet, indem der Zeitpunkt in der Zone formatiert und wieder als UTC gelesen wird —
 * die Differenz ist der Versatz. Umständlich, aber die einzige Rechnung, die ohne eine
 * eigene Zonendatenbank auskommt.
 */
function versatzVon(zone, zeitpunkt) {
  const teile = felder(zone, zeitpunkt);
  const alsUtc = Date.UTC(teile.jahr, teile.monat - 1, teile.tag, teile.stunde, teile.minute, teile.sekunde);
  return alsUtc - Math.floor(zeitpunkt / 1000) * 1000;
}

function felder(zone, zeitpunkt) {
  const teile = new Intl.DateTimeFormat("en-US", {
    timeZone: zone,
    hour12: false,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(new Date(zeitpunkt));

  const wert = (name) => Number(teile.find((teil) => teil.type === name)?.value ?? 0);

  return {
    jahr: wert("year"),
    monat: wert("month"),
    tag: wert("day"),
    // Mitternacht kommt je nach Umgebung als „24“ heraus statt als „0“.
    stunde: wert("hour") % 24,
    minute: wert("minute"),
    sekunde: wert("second"),
  };
}

/**
 * Ist das überhaupt eine Zone?
 *
 * Ein Tippfehler in der Einstellung darf nicht dazu führen, dass jeder Termin still eine
 * Stunde danebenliegt — `Intl` wirft dann, und das soll hier auffallen und nicht dort.
 */
export function istZone(zone) {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: String(zone) });
    return true;
  } catch {
    return false;
  }
}
