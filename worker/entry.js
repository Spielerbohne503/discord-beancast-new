/**
 * Der Einstiegspunkt für Cloudflare.
 *
 * Nur diese Datei kennt `cloudflare:workers` — über `raum.js`. `index.js` bleibt dadurch
 * eine gewöhnliche ES-Datei, die `node --test` laden kann; sonst prüfte niemand die
 * Wegewahl, die Ursprungsfreigabe und die Umschlagprüfung, weil schon der Import scheiterte.
 */

export { default } from "./index.js";
export { Raum } from "./raum.js";
