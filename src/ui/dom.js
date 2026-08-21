/**
 * Ein Dutzend Zeilen statt eines Rahmenwerks.
 *
 * Die App hat keinen Bauschritt; ein Rahmenwerk müsste zur Laufzeit geladen werden und
 * wäre die größte Datei im Projekt. Für das, was hier gebraucht wird — Elemente bauen,
 * Ereignisse binden, einen Bereich neu zeichnen — reicht das hier.
 */

/**
 * Baut ein Element.
 *
 * ```js
 * h("button.knopf.knopf--haupt", { onclick: speichern }, "Speichern")
 * ```
 *
 * Aus der Kennung werden Klassen gelesen (`div.karte.abschnitt`), aus den Eigenschaften
 * alles Übrige. `on…` sind Ereignisse, alles mit Bindestrich wird als Attribut gesetzt.
 */
export function h(kennung, eigenschaften = {}, ...kinder) {
  const [tag, ...klassen] = String(kennung).split(".");
  const element = document.createElement(tag || "div");
  if (klassen.length > 0) element.className = klassen.join(" ");

  for (const [name, wert] of Object.entries(eigenschaften ?? {})) {
    if (wert === null || wert === undefined || wert === false) continue;

    if (name.startsWith("on") && typeof wert === "function") {
      element.addEventListener(name.slice(2), wert);
    } else if (name === "class") {
      element.className = [element.className, wert].filter(Boolean).join(" ");
    } else if (name === "style" && typeof wert === "object") {
      for (const [eigenschaft, inhalt] of Object.entries(wert)) {
        element.style.setProperty(eigenschaft, inhalt);
      }
    } else if (name === "dataset") {
      for (const [schluessel, inhalt] of Object.entries(wert)) element.dataset[schluessel] = inhalt;
    } else if (name === "html") {
      element.innerHTML = wert;
    } else if (name in element && !name.includes("-")) {
      element[name] = wert;
    } else {
      element.setAttribute(name, wert === true ? "" : wert);
    }
  }

  anhaengen(element, kinder);
  return element;
}

function anhaengen(element, kinder) {
  for (const kind of kinder.flat(Infinity)) {
    if (kind === null || kind === undefined || kind === false) continue;
    element.append(kind instanceof Node ? kind : document.createTextNode(String(kind)));
  }
}

/** Ersetzt den Inhalt eines Bereichs in einem Rutsch. */
export function fuellen(ziel, ...kinder) {
  ziel.replaceChildren();
  anhaengen(ziel, kinder);
  return ziel;
}

/** Ein SVG-Element — `document.createElement` reicht dafür nicht. */
export function svg(tag, eigenschaften = {}, ...kinder) {
  const element = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [name, wert] of Object.entries(eigenschaften ?? {})) {
    if (wert === null || wert === undefined || wert === false) continue;
    if (name.startsWith("on") && typeof wert === "function") element.addEventListener(name.slice(2), wert);
    else element.setAttribute(name, wert);
  }
  for (const kind of kinder.flat(Infinity)) {
    if (kind === null || kind === undefined || kind === false) continue;
    element.append(kind);
  }
  return element;
}

/** Kurzform für Ereignisse an bereits vorhandenen Elementen. */
export function on(ziel, ereignis, hoerer, optionen) {
  ziel.addEventListener(ereignis, hoerer, optionen);
  return () => ziel.removeEventListener(ereignis, hoerer, optionen);
}
