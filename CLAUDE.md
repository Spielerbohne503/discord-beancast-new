# PeTodo

Aufgabenverwaltung mit einem Begleiter, der davon lebt. Als Webseite, mit einer
mobilen und einer Desktop-Ansicht. Offline, ohne Konto, ohne Telemetrie.

**Maßstab:** Am Tag nach dem ersten Build muss man TickTick deinstallieren
können, ohne etwas zu vermissen, das man täglich braucht.

## Aufbau

Kein Bauschritt. Reine ES-Module, die der Browser direkt lädt; `index.html`
liegt im Wurzelverzeichnis. Wer das Ding irgendwo hinstellen will, kopiert den
Ordner.

```
src/domain/   Fachlogik. Rein: kein Browser, keine Uhr, keine Importe von außen.
src/data/     IndexedDB, Repositories, Sicherung. Liest die Uhr, setzt updatedAt.
src/ui/       Ansichten. Bauen Elemente, halten keinen eigenen Zustand.
src/styles/   Gestaltung. tokens → base → components → layout → orb → views.
test/         node --test. Fachlogik plus zwei Wächter über den Quelltext.
tools/        Entwicklungsserver, Bildschirmfotos, Durchlauf im Browser.
```

## Befehle

```
npm test              Unit-Tests (node --test, keine Abhängigkeit)
npm run serve         Entwicklungsserver auf :8000
npm run browsertest   Durchlauf durch die laufende App in Chromium
npm run shots         Bildschirmfotos beider Anordnungen
npm run check         alles drei
```

Für die Browser-Werkzeuge muss der Server laufen. Chromium liegt unter
`PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers`; **nichts nachladen.**

## Einbahnstraßen

Das hier ist später nicht mehr zu ändern, ohne alles anzufassen:

1. **UUIDs als Schlüssel**, keine fortlaufenden Zahlen — zwei Geräte können ohne
   Absprache anlegen.
2. **`updatedAt` in jeder Zeile, Löschen setzt `deletedAt`** (Tombstone). Ohne
   Tombstones lässt sich ein Löschen nie abgleichen: Die Zeile käme beim nächsten
   Zusammenführen zurück.
3. **Reihenfolge von Hand als Bruchindex** (`sortKey`). Ein Verschieben schreibt
   genau eine Zeile.
4. **Genau eine Funktion berechnet den Wertestand des Begleiters**: `computePet`
   in `src/domain/pet.js`. Wer an einer zweiten Stelle nachrechnet, hat den
   Fehler eingebaut, den diese Regel verhindert.
5. **Fälligkeit ist zweiteilig**: `dueAt` als Zeitpunkt, `dueTimeLocal` als
   lokale Uhrzeit. Termine werden über *Kalendertag + Uhrzeit* fortgeschrieben,
   **nie** über `+ 86.400.000 ms` — sonst wandert die 8-Uhr-Erinnerung bei jeder
   Zeitumstellung.
6. **Wiederholungen als RFC 5545**, kein Eigenbau-Format.
7. **Das Sicherungsformat ist tabellennah und bleibt Fassung 1.** Eine Sicherung
   der früheren Android-Fassung muss hier einlesbar bleiben.
8. **Alle Zahlen mit fachlicher Bedeutung stehen in `src/domain/balance.js`** —
   auch die, die nur an einer Stelle gebraucht werden.
9. **Alle sichtbaren Texte stehen in `src/ui/strings.js`.**

## Fachliche Regeln, die keine Geschmacksfrage sind

- **Krankheit hängt nur an überfälligen Aufgaben.** Etwas aufzuschreiben schadet
  dem Begleiter nie. Wenn Eintragen bestraft wird, trägt man nichts mehr ein.
- **Der heutige Tag zählt nie gegen einen.** Gilt für Serien, Gewohnheiten und
  den Begleiter gleichermaßen.
- **Kein Tod, kein Zustand ohne Rückweg.** Unter „elend“ gibt es nichts.
- **Verfall ist auf 24 Stunden gedeckelt.** Nach zwei Wochen Urlaub ist der
  Begleiter nicht schlechter dran als nach einem Tag.
- **Erledigtes bleibt den ganzen Tag stehen** und rutscht erst am nächsten Tag in
  den eingeklappten Rückblick. Nach `ARCHIVE_DAYS` fällt es auch daraus.
- **Gewohnheiten werden nie überfällig** und mahnen nie. Dass sie in einer
  eigenen Tabelle stehen, macht das strukturell unumgehbar.
- **Von einer wiederkehrenden Aufgabe existiert immer nur eine offene Instanz.**
  Verpasste Termine werden übersprungen und gezählt.
- **Aufräumen zählt wie Erledigen** — auch Löschen. Sonst bestraft man Ehrlichkeit.
- **Erinnerungen eskalieren, sie wiederholen sich nicht.** In der Ruhezeit werden
  sie verschoben, nie verworfen.
- **Erinnerungen laufen nur, solange die Seite offen ist.** Ohne Server kann
  niemand einen geschlossenen Tab wecken; das wird so gesagt und nicht kaschiert.

## Gestaltung

Ein tiefer, fast schwarzer Grund mit Nebel und Sternfeld; darauf Glasflächen mit
schmalen Kanten. Drei Verläufe mit fester Bedeutung:

| Verlauf | Bedeutung |
| --- | --- |
| `--grad-aurora` | Handlung, Fortschritt, der Begleiter |
| `--grad-ember` | Überfällig, dringend — nie dekorativ |
| `--grad-vital` | Geschafft, Serien, Gewohnheiten |

Das Leuchten ist ein Farbschein hinter der Fläche, kein Rahmen. Es gibt bewusst
nur diese eine, dunkle Fassung.

**Der Begleiter ist ein Himmelskörper, kein Gesicht.** Kugel mit Kern und
Umlaufbahn, gezeichnet mit CSS und SVG. Kein Emoji, keine Augen — beides sah in
jeder Größe nach Aufkleber aus.

Zwei Anordnungen, Umbruch bei 960 px: Telefon mit Leiste unten und
Begleiter-Streifen über der Liste; Schreibtisch mit Seitenleiste und
Nebenspalte, auf dem **alle** Funktionen samt Einstellungen ohne Untermenü
erreichbar sind.

## Wächter

Zwei Tests lesen den Quelltext statt das Verhalten. Sie werden nicht
„repariert“, indem man sie lockert:

- `test/domain-purity.test.js` — `src/domain/` kennt keinen Browser, liest die
  Uhr nicht selbst und importiert nichts von außerhalb.
- `test/regex-portability.test.js` — kein `\b` (kennt keine Umlaute), keine
  Rückschau (`(?<=`, ältere Safari-Fassungen übersetzen sie nicht), kein `(?U)`.
  `\p{…}` nur mit `u`-Schalter.

Der Hintergrund: In der Android-Fassung hat ein `(?U)` in einem regulären
Ausdruck das ganze Modul beim Laden mitgenommen — die App startete nicht mehr,
und **kein einziger Unit-Test hat davon etwas gemerkt**, weil er dieselbe
Regex-Maschine benutzte wie der Entwicklungsrechner. Dieselbe Sorte Fehler ist
im Browser jederzeit möglich.

`test/backup-coverage.test.js` vergleicht das Schema mit der Tabellenliste der
Sicherung: Eine neue Tabelle, die niemand aufnimmt, fällt sonst erst auf, wenn
jemand seine Daten schon verloren hat.

## Arbeitsweise

- Keine Bibliotheken ohne Rückfrage. Bisher: null Laufzeitabhängigkeiten.
- Kein Netzzugriff aus der App heraus, kein Konto, keine Telemetrie.
- **Nach jeder Änderung an der Oberfläche `npm run browsertest` laufen lassen.**
  Behauptungen über eine laufende App sind wertlos; hier lässt sie sich starten.
- Fragen statt raten, wenn etwas offenbleibt.
