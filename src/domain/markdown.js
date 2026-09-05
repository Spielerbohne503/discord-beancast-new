/**
 * Der Bestand als Markdown — für ein Obsidian-Vault.
 *
 * Geschrieben wird, was Obsidian ohnehin versteht, und nichts darüber hinaus: `- [ ]` für
 * offen, `- [x]` für erledigt, `#etikett` als Etikett, `[[Titel]]` als Verweis auf eine
 * andere Aufgabe. Genau die Schreibweise, die `domain/links.js` in der App schon liest —
 * ein `[[Steuer]]` in einer Notiz wird in Obsidian derselbe Verweis wie hier.
 *
 * Oben steht ein YAML-Vorspann. Der ist der eigentliche Zweck: Er macht die Datei für
 * Dataview abfragbar und, wichtiger, **wiedererkennbar**. Eine KI, die das Vault pflegt,
 * darf nicht raten müssen, welche Datei sie zuletzt geschrieben hat.
 *
 * Rein: kein Netz, keine Uhr, keine Datenbank. Der Zeitpunkt wird hereingereicht.
 */

import { isCompleted, isDeleted, isOpen } from "./tasks.js";
import { Priority } from "./balance.js";
import { isoDate, dayOf } from "./time.js";
import { tagIn } from "./zonen.js";

/** Steht im Vorspann, damit eine ältere Datei erkennbar bleibt. */
export const MARKDOWN_VERSION = 1;

const PRIORITAET = Object.freeze({
  [Priority.LOW]: "niedrig",
  [Priority.NORMAL]: "normal",
  [Priority.HIGH]: "hoch",
  [Priority.URGENT]: "dringend",
});

/**
 * Zeichen, die eine Überschrift oder einen Dateinamen kaputtmachen.
 *
 * Obsidian legt Verweise über den Titel an — ein `#` oder `[[` mitten im Titel zeigt sonst
 * irgendwohin. Ersetzt wird, nicht entfernt: Ein Titel, aus dem Zeichen verschwinden, ist
 * beim Zurücklesen ein anderer.
 */
const HEIKEL = /[[\]#|^]/g;

/**
 * Das ganze Vault als **eine** Datei.
 *
 * Eine Datei und nicht eine je Aufgabe: Wer sie in ein Vault legt, will einen Stand
 * überschreiben können, ohne vorher aufzuräumen. Eine Datei je Aufgabe hinterlässt bei
 * jedem Umbenennen eine Leiche, und die räumt niemand weg.
 */
export function alsMarkdown({ tasks, lists, tags, tagLinks, habits }, jetzt, zone = null) {
  // Auf einem Gerät ist die Ortszeit die richtige, im Worker ist sie UTC. Ohne die Zone
  // rutschte dort ein Termin um 00:30 auf den Vortag.
  const tagVon = zone === null ? dayOf : (millis) => tagIn(zone, millis);
  const offen = tasks.filter((task) => isOpen(task));
  const erledigt = tasks.filter((task) => isCompleted(task) && !isDeleted(task));

  const teile = [
    vorspann({ offen: offen.length, erledigt: erledigt.length }, jetzt),
    `# PeTodo`,
    "",
    `Erzeugt am ${isoDate(tagVon(jetzt))}. **Diese Datei wird überschrieben** — was hier`,
    `von Hand hineingeschrieben wird, ist beim nächsten Export weg.`,
    "",
  ];

  for (const liste of sortiert(lists.filter((liste) => !isDeleted(liste)))) {
    const dazu = offen.filter((task) => task.listId === liste.id && task.parentId === null);
    const fertig = erledigt.filter((task) => task.listId === liste.id && task.parentId === null);
    if (dazu.length === 0 && fertig.length === 0) continue;

    teile.push(`## ${sauber(liste.name)}`, "");
    for (const task of sortiert(dazu)) teile.push(...zeile(task, tasks, tags, tagLinks, tagVon));

    if (fertig.length > 0) {
      teile.push("", `### Erledigt`, "");
      for (const task of sortiert(fertig)) teile.push(...zeile(task, tasks, tags, tagLinks, tagVon));
    }
    teile.push("");
  }

  const lebendeGewohnheiten = (habits ?? []).filter((habit) => !isDeleted(habit));
  if (lebendeGewohnheiten.length > 0) {
    teile.push(`## Gewohnheiten`, "");
    for (const habit of sortiert(lebendeGewohnheiten)) teile.push(`- ${sauber(habit.name)}`);
    teile.push("");
  }

  // Genau ein abschließender Zeilenumbruch: Wer die Datei zweimal exportiert und
  // vergleicht, soll keinen Unterschied im Leerraum finden.
  return `${teile.join("\n").replace(/\n{3,}/g, "\n\n").trimEnd()}\n`;
}

/**
 * Eine Aufgabe samt Unteraufgaben.
 *
 * Unteraufgaben stehen eingerückt darunter — in Obsidian ist das dieselbe Liste, nur eine
 * Ebene tiefer, und bleibt damit abhakbar statt bloß lesbar.
 */
function zeile(task, alle, tags, tagLinks, tagVon, tiefe = 0) {
  const zeilen = [
    `${"    ".repeat(tiefe)}- [${isCompleted(task) ? "x" : " "}] ${beschriftung(task, tags, tagLinks, tagVon)}`,
  ];

  if (task.note) {
    for (const stueck of String(task.note).split("\n")) {
      if (stueck.trim().length > 0) zeilen.push(`${"    ".repeat(tiefe + 1)}${stueck.trim()}`);
    }
  }

  const kinder = alle.filter((anderer) => anderer.parentId === task.id && !isDeleted(anderer));
  for (const kind of sortiert(kinder)) zeilen.push(...zeile(kind, alle, tags, tagLinks, tagVon, tiefe + 1));

  return zeilen;
}

function beschriftung(task, tags, tagLinks, tagVon) {
  const stuecke = [sauber(task.title)];

  if (task.dueAt !== null && task.dueAt !== undefined) {
    const tag = isoDate(tagVon(task.dueAt));
    stuecke.push(task.hasTime ? `📅 ${tag} ${uhrzeit(task.dueTimeLocal)}` : `📅 ${tag}`);
  }

  if (task.priority !== Priority.NORMAL) stuecke.push(`⏫ ${PRIORITAET[task.priority] ?? task.priority}`);
  if (task.rrule) stuecke.push(`🔁 ${task.rrule}`);

  for (const name of etiketten(task.id, tags, tagLinks)) stuecke.push(`#${name.replace(/\s+/g, "-")}`);

  return stuecke.join(" ");
}

function etiketten(taskId, tags, tagLinks) {
  const kennungen = (tagLinks ?? [])
    .filter((verweis) => verweis.taskId === taskId && !isDeleted(verweis))
    .map((verweis) => verweis.tagId);

  return kennungen
    .map((id) => (tags ?? []).find((tag) => tag.id === id))
    .filter((tag) => tag && !isDeleted(tag))
    .map((tag) => tag.name);
}

function uhrzeit(minuten) {
  if (minuten === null || minuten === undefined) return "";
  const stunde = String(Math.floor(minuten / 60)).padStart(2, "0");
  const rest = String(minuten % 60).padStart(2, "0");
  return `${stunde}:${rest}`;
}

/**
 * Der YAML-Vorspann.
 *
 * Keine Anführungszeichen und keine Doppelpunkte in den Werten — sonst braucht es einen
 * YAML-Schreiber, und der wäre die erste Abhängigkeit des Projekts.
 */
function vorspann(zahlen, jetzt) {
  return [
    "---",
    "quelle: petodo",
    `fassung: ${MARKDOWN_VERSION}`,
    `stand: ${new Date(jetzt).toISOString()}`,
    `offen: ${zahlen.offen}`,
    `erledigt: ${zahlen.erledigt}`,
    "---",
    "",
  ].join("\n");
}

function sauber(text) {
  return String(text ?? "").replace(HEIKEL, " ").replace(/\s+/g, " ").trim();
}

function sortiert(zeilen) {
  return [...zeilen].sort((a, b) => {
    const links = a.sortKey ?? "";
    const rechts = b.sortKey ?? "";
    return links < rechts ? -1 : links > rechts ? 1 : 0;
  });
}
