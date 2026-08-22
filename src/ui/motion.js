/**
 * Bewegung.
 *
 * Die Vorlage (reactbits.dev) ist eine Sammlung fertiger React-Bausteine. React kommt hier
 * nicht in Frage — die App hat null Laufzeitabhängigkeiten und keinen Bauschritt, und in
 * der Android-Hülle gibt es nicht einmal eine Netzwerkerlaubnis, um irgendetwas
 * nachzuladen. Übernommen ist deshalb die **Idee** der Effekte, nachgebaut mit den
 * Bordmitteln des Browsers:
 *
 * | Vorlage        | Hier                                                        |
 * | -------------- | ----------------------------------------------------------- |
 * | Split Text     | [aufteilen] — Überschriften laufen zeichenweise ein          |
 * | Count Up       | [hochzaehlen] — Kennzahlen zählen hoch statt zu erscheinen    |
 * | Decrypt Text   | [entschluesseln] — der Begleiter „findet“ seinen Satz         |
 * | Click Spark    | [funken] — ein Tintenstern beim Abhaken                       |
 *
 * **Jede Bewegung fragt vorher, ob sie darf.** Wer „Bewegung reduzieren“ eingestellt hat,
 * bekommt sofort das Endergebnis — nicht dieselbe Bewegung, nur schneller.
 */

const ZEICHEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#*+-<>/";

/** Ob Bewegung erwünscht ist. Zwei Quellen, eine Antwort. */
export function bewegungErlaubt() {
  if (document.body.classList.contains("ruhig")) return false;
  return !globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
}

/**
 * Zeichenweiser Einlauf.
 *
 * Der Text wird in `<span>` je Zeichen zerlegt und mit steigender Verzögerung eingeblendet.
 * Leerzeichen bleiben Leerzeichen, damit der Zeilenumbruch stimmt.
 */
export function aufteilen(element, text, { schritt = 18, start = 0 } = {}) {
  element.textContent = "";

  if (!bewegungErlaubt()) {
    element.textContent = text;
    return element;
  }

  const zeichen = [...text];
  for (const [index, buchstabe] of zeichen.entries()) {
    const span = document.createElement("span");
    span.className = "aufgeteilt__zeichen";
    span.textContent = buchstabe === " " ? " " : buchstabe;
    span.style.animationDelay = `${start + index * schritt}ms`;
    element.append(span);
  }
  return element;
}

/**
 * Eine Zahl zählt hoch.
 *
 * Nicht linear, sondern ausklingend: Der Sprung von 0 auf 90 % passiert schnell, der Rest
 * langsam. Das liest sich wie ein Zählwerk, das einrastet.
 */
export function hochzaehlen(element, ziel, { dauer = 700, formatieren = String } = {}) {
  const zahl = Number(ziel) || 0;

  if (!bewegungErlaubt() || zahl === 0) {
    element.textContent = formatieren(zahl);
    return;
  }

  const beginn = performance.now();

  const schritt = (jetzt) => {
    const anteil = Math.min(1, (jetzt - beginn) / dauer);
    const weich = 1 - (1 - anteil) ** 3;
    element.textContent = formatieren(Math.round(zahl * weich));
    if (anteil < 1) requestAnimationFrame(schritt);
  };

  element.textContent = formatieren(0);
  requestAnimationFrame(schritt);
}

/**
 * Ein Text „findet“ sich.
 *
 * Von links nach rechts rasten die richtigen Zeichen ein, der Rest würfelt weiter. Der
 * Begleiter wirkt damit, als überlege er — ohne dass irgendetwas rechnet.
 *
 * Gibt eine Funktion zurück, die den Lauf abbricht: Ein zweiter Satz darf den ersten nicht
 * überschreiben, während der noch läuft.
 */
export function entschluesseln(element, text, { dauerJeZeichen = 26 } = {}) {
  if (!bewegungErlaubt()) {
    element.textContent = text;
    return () => {};
  }

  const zeichen = [...text];
  let abgebrochen = false;
  let index = 0;

  const takt = setInterval(() => {
    if (abgebrochen) return;

    element.textContent = zeichen
      .map((buchstabe, stelle) => {
        if (stelle < index || buchstabe === " ") return buchstabe;
        return ZEICHEN[Math.floor(Math.random() * ZEICHEN.length)];
      })
      .join("");

    index++;
    if (index > zeichen.length) {
      clearInterval(takt);
      element.textContent = text;
    }
  }, dauerJeZeichen);

  return () => {
    abgebrochen = true;
    clearInterval(takt);
    element.textContent = text;
  };
}

/**
 * Ein kurzer Funke am Ort des Klicks.
 *
 * Acht Striche in Tinte, die nach außen laufen und verschwinden — die Rückmeldung fürs
 * Abhaken. Sie räumt sich selbst weg; sonst sammeln sich nach hundert Aufgaben hundert
 * unsichtbare Elemente im Baum.
 */
export function funken(element) {
  if (!bewegungErlaubt() || !element?.isConnected) return;

  const rechteck = element.getBoundingClientRect();
  const funke = document.createElement("div");
  funke.className = "funke";
  funke.style.left = `${rechteck.left + rechteck.width / 2}px`;
  funke.style.top = `${rechteck.top + rechteck.height / 2}px`;

  for (let strich = 0; strich < 8; strich++) {
    const strahl = document.createElement("i");
    strahl.style.transform = `rotate(${strich * 45}deg)`;
    funke.append(strahl);
  }

  document.body.append(funke);
  setTimeout(() => funke.remove(), 620);
}


/**
 * Ansichtswechsel mit Übergang.
 *
 * Wo der Browser die View-Transitions-Schnittstelle hat, wischt der Inhalt mechanisch
 * durch — passend zur harten Kante, keine Weichblende. Wo nicht, wird schlicht gezeichnet:
 * Der Übergang ist Zierrat, das Zeichnen ist die Aufgabe.
 *
 * Das Zeichnen läuft in beiden Fällen **genau einmal**. Ein Übergang, der die Arbeit
 * doppelt macht oder verschluckt, wäre schlimmer als gar keiner.
 */
export function mitUebergang(zeichnen) {
  if (!bewegungErlaubt() || typeof document.startViewTransition !== "function") {
    zeichnen();
    return;
  }

  try {
    document.startViewTransition(zeichnen);
  } catch {
    zeichnen();
  }
}

/**
 * Der Stempel: „alles weg“.
 *
 * Kommt nur, wenn heute wirklich nichts mehr offen ist **und** etwas geschafft wurde. Ohne
 * die zweite Bedingung stempelte eine frisch installierte App den ersten Tag ab, an dem
 * man noch gar nichts eingetragen hat — und der Stempel wäre nichts mehr wert.
 */
export function stempeln(element) {
  if (!bewegungErlaubt() || !element) return;
  element.classList.add("stempel--faellt");
}
