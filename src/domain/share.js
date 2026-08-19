/**
 * „Teilen an PeTodo“ — aus dem, was eine andere App schickt, wird eine Aufgabe.
 *
 * Geteilt wird mit zwei bis drei Feldern: Betreff, Text und (im Web) eine Adresse. Was
 * darin steht, ist von App zu App verschieden — mal nur eine Adresse, mal Titel plus
 * Adresse, mal mehrere Absätze. Hier steht die Entscheidung, was davon Titel und was Notiz
 * wird; die Oberfläche führt sie nur aus.
 *
 * Der schöne Fall: Betreff **und** eine Adresse ergeben `[Betreff](Adresse)`. In der Liste
 * steht dann der Titel des Videos und nicht dreißig Zeichen Kennung, und angetippt geht er
 * trotzdem auf.
 */

import { parseLinks } from "./links.js";

function clean(text) {
  const value = String(text ?? "").replace(/\s+/g, " ").trim();
  return value.length === 0 ? null : value;
}

/** Gibt die Adresse zurück, wenn der Text aus nichts anderem besteht. */
function standaloneUrl(text) {
  const segments = parseLinks(text);
  if (segments.length !== 1 || segments[0].kind !== "link") return null;
  return segments[0].url;
}

export function captureShare(subject, text, url = null) {
  const title = clean(subject);
  // Die Web-Share-Ziel-Schnittstelle liefert die Adresse getrennt; angehängt verhält sie
  // sich wie derselbe Fall aus Android.
  const joined = [String(text ?? "").trim(), String(url ?? "").trim()].filter(Boolean).join("\n");
  const body = joined.length === 0 ? null : joined;

  if (title === null && body === null) return null;
  if (body === null) return { title, note: null };

  const address = standaloneUrl(body);
  if (address !== null) {
    return title !== null && title !== address
      ? { title: `[${title}](${address})`, note: null }
      : { title: address, note: null };
  }

  // Freitext mit Betreff: Der Betreff ist der Titel, der Rest die Notiz.
  if (title !== null) return { title, note: body };

  // Freitext ohne Betreff: Die erste Zeile trägt, der Rest wandert in die Notiz.
  const lines = body.split("\n");
  const firstIndex = lines.findIndex((line) => line.trim().length > 0);
  if (firstIndex < 0) return null;

  const first = clean(lines[firstIndex]);
  if (first === null) return null;

  const rest = lines.slice(firstIndex + 1).join("\n").trim();
  return { title: first, note: rest.length === 0 ? null : rest };
}
