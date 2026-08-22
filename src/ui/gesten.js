/**
 * Gesten: wischen und lange drücken.
 *
 * Beides mit `pointer`-Ereignissen statt mit `touch` — dieselbe Behandlung für Finger,
 * Stift und Maus, und man muss nicht zwei Wege pflegen, die sich langsam auseinander
 * entwickeln.
 *
 * Die drei Regeln, an denen selbstgebaute Wischgesten scheitern:
 *
 * 1. **Die Richtung wird einmal festgelegt.** Wer schräg ansetzt und dann nach unten
 *    zieht, will scrollen, nicht wischen. Danach wird die Entscheidung nicht revidiert.
 * 2. **Erst ab einer Schwelle passiert etwas.** Ohne sie löst jedes Antippen mit einem
 *    Pixel Versatz eine Handlung aus.
 * 3. **Losgelassen wird immer sauber.** Auch bei `pointercancel`, das beim Scrollen
 *    kommt — sonst bleibt die Zeile schief stehen.
 *
 * Und die vierte, die man erst merkt, wenn es einmal falsch war: **Nach einem Wisch darf
 * kein Klick durchgehen.** Der Browser schickt nach `pointerup` noch ein `click` — ohne
 * Riegel öffnet jeder Wisch zusätzlich die Einzelheiten.
 */

/** Ab hier gilt die Bewegung als Wischen und nicht mehr als Scrollen. */
const SCHWELLE = 12;

/** So weit muss es gehen, damit die Handlung ausgelöst wird. */
const AUSLOESER = 88;

/** So lange muss gedrückt werden. Kurz genug zum Entdecken, lang genug zum Vermeiden. */
const DRUCKDAUER = 450;

/**
 * Hängt Wischen an eine Zeile.
 *
 * @param element die Zeile
 * @param handlungen `{ rechts, links }` — jeweils `{ text, symbol, art, tun }`
 * @returns eine Funktion, die alles wieder löst
 */
export function wischen(element, handlungen) {
  let start = null;
  let richtung = null;
  let versatz = 0;

  const spur = document.createElement("div");
  spur.className = "wischspur";
  element.prepend(spur);

  const zeigen = (weite) => {
    const handlung = weite > 0 ? handlungen.rechts : handlungen.links;
    if (!handlung) return 0;

    spur.dataset.art = handlung.art;
    spur.dataset.seite = weite > 0 ? "rechts" : "links";
    spur.textContent = handlung.text;
    spur.classList.toggle("wischspur--reif", Math.abs(weite) >= AUSLOESER);

    element.style.transform = `translateX(${weite}px)`;
    return weite;
  };

  const aufraeumen = () => {
    element.classList.remove("zeile--wischt");
    element.style.transform = "";
    spur.classList.remove("wischspur--reif");
    spur.removeAttribute("data-art");
    start = null;
    richtung = null;
    versatz = 0;
  };

  const runter = (ereignis) => {
    // Nur der Hauptzeiger, und nicht auf einem Knopf: Der Haken hat sein eigenes Ziel.
    if (!ereignis.isPrimary || ereignis.target.closest("button, a, input, select, textarea")) return;
    start = { x: ereignis.clientX, y: ereignis.clientY };
    richtung = null;
  };

  const bewegen = (ereignis) => {
    if (start === null) return;

    const dx = ereignis.clientX - start.x;
    const dy = ereignis.clientY - start.y;

    if (richtung === null) {
      if (Math.abs(dx) < SCHWELLE && Math.abs(dy) < SCHWELLE) return;
      // Einmal entschieden, bleibt es dabei — sonst kippt die Zeile beim Scrollen.
      richtung = Math.abs(dx) > Math.abs(dy) ? "quer" : "laengs";
      if (richtung === "quer") element.classList.add("zeile--wischt");
    }
    if (richtung !== "quer") return;

    const handlung = dx > 0 ? handlungen.rechts : handlungen.links;
    if (!handlung) return;

    ereignis.preventDefault();
    // Über dem Auslöser wird es zäh: Man spürt, dass mehr nichts mehr bringt.
    versatz = zeigen(Math.sign(dx) * Math.min(Math.abs(dx), AUSLOESER + (Math.abs(dx) - AUSLOESER) / 4));
  };

  const hoch = async () => {
    if (start === null) return;

    const gewischt = richtung === "quer";
    const reif = Math.abs(versatz) >= AUSLOESER;
    const handlung = versatz > 0 ? handlungen.rechts : handlungen.links;
    aufraeumen();

    // Der Klick, der gleich kommt, gehört zum Wisch und nicht zur Zeile.
    if (gewischt) klickSchlucken(element);

    if (reif && handlung) await handlung.tun();
  };

  element.addEventListener("pointerdown", runter);
  element.addEventListener("pointermove", bewegen);
  element.addEventListener("pointerup", hoch);
  element.addEventListener("pointercancel", aufraeumen);
  element.addEventListener("pointerleave", aufraeumen);

  return aufraeumen;
}

/**
 * Schluckt genau einen Klick.
 *
 * In der Einfangphase, damit er gar nicht erst bei den Zuhörern ankommt, und einmalig —
 * ein dauerhafter Riegel machte die Zeile unanklickbar. Der Zeitgeber räumt ihn weg,
 * falls gar kein Klick mehr kommt (bei `pointercancel` etwa).
 */
function klickSchlucken(element) {
  const riegel = (ereignis) => {
    ereignis.stopPropagation();
    ereignis.preventDefault();
  };

  element.addEventListener("click", riegel, { capture: true, once: true });
  setTimeout(() => element.removeEventListener("click", riegel, { capture: true }), 400);
}

/**
 * Langes Drücken.
 *
 * Bricht ab, sobald sich der Finger bewegt — sonst löst jeder Scrollversuch aus, bei dem
 * man einen Moment zu lange liegen bleibt.
 */
export function langDruecken(element, tun) {
  let zeitgeber = null;
  let start = null;

  const abbrechen = () => {
    clearTimeout(zeitgeber);
    zeitgeber = null;
    start = null;
  };

  element.addEventListener("pointerdown", (ereignis) => {
    if (!ereignis.isPrimary || ereignis.target.closest("button, a, input, select, textarea")) return;
    start = { x: ereignis.clientX, y: ereignis.clientY };

    zeitgeber = setTimeout(() => {
      zeitgeber = null;
      // Ein kurzer Ruck als Rückmeldung, wo das Gerät ihn kann.
      navigator.vibrate?.(12);
      tun();
    }, DRUCKDAUER);
  });

  element.addEventListener("pointermove", (ereignis) => {
    if (start === null) return;
    const weit = Math.abs(ereignis.clientX - start.x) > 10 || Math.abs(ereignis.clientY - start.y) > 10;
    if (weit) abbrechen();
  });

  for (const ereignis of ["pointerup", "pointercancel", "pointerleave"]) {
    element.addEventListener(ereignis, abbrechen);
  }

  return abbrechen;
}
