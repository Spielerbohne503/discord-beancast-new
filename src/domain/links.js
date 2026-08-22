/**
 * Verweise in Titeln und Notizen — dieselbe Schreibweise wie in Markdown-Dateien.
 *
 * Erkannt werden `[Text](https://…)`, nackte `http(s)://`-Adressen und `[[Aufgabe]]` als
 * Verweis auf eine andere Aufgabe. Mehr nicht: kein Fettdruck, keine Überschriften, keine
 * Listen. Eine Aufgabenverwaltung, die heimlich zum Markdown-Editor wird, kann am Ende
 * beides halb.
 */

/**
 * `[Text](Adresse)`. Die Adresse darf **eine Ebene Klammern** enthalten — ohne diese
 * Ausnahme endet ein Verweis auf `…/Kotlin_(Programmiersprache)` mitten im Wort.
 */
const MARKDOWN = /\[([^\]\n]*)\]\(\s*((?:[^()\s]|\([^()\s]*\))+)\s*\)/g;

/** Nackte Adresse. Nur http(s): Alles andere fängt zu viele falsche Treffer. */
const BARE = /https?:\/\/\S+/gi;

/**
 * `[[Aufgabe]]` — ein Verweis auf eine andere Aufgabe, über ihren Titel.
 *
 * Über den Titel und nicht über eine Kennung: Man tippt ihn, statt ihn nachzuschlagen.
 * Der Preis ist, dass ein umbenanntes Ziel ins Leere zeigt — die Oberfläche sagt das dann,
 * statt so zu tun, als gäbe es die Aufgabe noch.
 */
const AUFGABE = /\[\[([^\]\n]+)\]\]/g;

/** Zeichen, die am Ende einer nackten Adresse fast immer Satzzeichen sind. */
const TRAILING = ".,;:!?»\"'";

const MAX_SEGMENT = 18;

/**
 * @typedef {{kind: "text", text: string}
 *   | {kind: "link", label: string, url: string}
 *   | {kind: "aufgabe", label: string, titel: string}} Segment
 */

/** Zerlegt einen Text in Stücke. Ohne Verweis kommt genau ein Textstück zurück. */
export function parseLinks(text) {
  const input = String(text ?? "");
  if (input.length === 0) return [{ kind: "text", text: "" }];

  const treffer = [];

  MARKDOWN.lastIndex = 0;
  for (let match; (match = MARKDOWN.exec(input)) !== null; ) {
    treffer.push({
      start: match.index,
      end: match.index + match[0].length,
      label: match[1].trim(),
      url: match[2],
    });
  }

  AUFGABE.lastIndex = 0;
  for (let match; (match = AUFGABE.exec(input)) !== null; ) {
    const titel = match[1].trim();
    // `[[ ]]` verweist auf nichts. Ein Verweis ohne Ziel ist kein Verweis, sondern zwei
    // Klammern — und die sollen dann auch als Klammern dastehen.
    if (titel.length === 0) continue;

    treffer.push({
      start: match.index,
      end: match.index + match[0].length,
      label: titel,
      url: null,
      titel,
    });
  }

  BARE.lastIndex = 0;
  for (let match; (match = BARE.exec(input)) !== null; ) {
    const roh = match[0];
    const gekuerzt = trimTrailing(roh);
    const start = match.index;
    const end = start + gekuerzt.length;

    // Adressen, die schon in einer Markdown-Klammer stecken, nicht doppelt nehmen.
    const ueberschneidet = treffer.some((vorhanden) => start < vorhanden.end && vorhanden.start < end);
    if (!ueberschneidet) treffer.push({ start, end, label: null, url: gekuerzt });
  }

  treffer.sort((a, b) => a.start - b.start);

  const stuecke = [];
  let position = 0;
  for (const fund of treffer) {
    if (fund.start > position) {
      stuecke.push({ kind: "text", text: input.slice(position, fund.start) });
    }
    stuecke.push(
      fund.titel !== undefined
        ? { kind: "aufgabe", label: fund.label, titel: fund.titel }
        : {
            kind: "link",
            label: fund.label && fund.label.length > 0 ? fund.label : shortenUrl(fund.url),
            url: fund.url,
          },
    );
    position = fund.end;
  }
  if (position < input.length) stuecke.push({ kind: "text", text: input.slice(position) });

  return stuecke.length > 0 ? stuecke : [{ kind: "text", text: input }];
}

function trimTrailing(url) {
  let wert = url;
  while (wert.length > 0) {
    const letztes = wert[wert.length - 1];
    const istSatzzeichen = TRAILING.includes(letztes);
    const istUeberzaehligeKlammer = letztes === ")" && !wert.includes("(");
    if (!istSatzzeichen && !istUeberzaehligeKlammer) break;
    wert = wert.slice(0, -1);
  }
  return wert;
}

/** Alle Adressen-Verweise in Reihenfolge ihres Auftretens. */
export function links(text) {
  return parseLinks(text).filter((stueck) => stueck.kind === "link");
}

/** Alle Titel, auf die dieser Text verweist. */
export function aufgabenVerweise(text) {
  return parseLinks(text)
    .filter((stueck) => stueck.kind === "aufgabe")
    .map((stueck) => stueck.titel);
}

export function hasLink(text) {
  return links(text).length > 0;
}

/**
 * Der Text, wie er in einer Liste stehen soll.
 *
 * Aus `[Reel](https://…)` wird „Reel“, aus einer nackten Adresse „instagram.com/reel/…“.
 * Eine Zeile, die drei Zeilen Adresse breit ist, verdrängt alles andere vom Bildschirm.
 */
export function plainText(text) {
  return parseLinks(text)
    .map((stueck) => (stueck.kind === "text" ? stueck.text : stueck.label))
    .join("")
    .trim();
}

/** Kurzform einer Adresse: Rechnername ohne `www.`, dazu höchstens ein Pfadstück. */
export function shortenUrl(url) {
  const ohneSchema = url.includes("://") ? url.slice(url.indexOf("://") + 3) : url;
  // Geschnitten wird am **rohen** Rechnernamen. Nach dem Entfernen von `www.` ist er
  // vier Zeichen kürzer — der Schnitt läge mitten im Namen und das erste Pfadstück
  // käme als „com“ heraus.
  const rohHost = ohneSchema.split("/")[0];
  const host = rohHost.replace(/^www\./, "");
  if (host.length === 0) return url;

  const rest = ohneSchema.slice(rohHost.length + (ohneSchema.includes("/") ? 1 : 0));
  const pfad = rest.split("?")[0].split("#")[0].replace(/^\/+|\/+$/g, "");
  if (pfad.length === 0) return host;

  const erstes = pfad.split("/")[0];
  const zuLang = erstes.length > MAX_SEGMENT;
  const gekuerzt = zuLang ? erstes.slice(0, MAX_SEGMENT) : erstes;

  if (pfad.includes("/")) return `${host}/${gekuerzt}/…`;
  if (zuLang || rest.includes("?") || rest.includes("#")) return `${host}/${gekuerzt}…`;
  return `${host}/${gekuerzt}`;
}
