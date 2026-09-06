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
worker/       Der Ablageort für den Abgleich und die Schnittstelle für eine KI.
              `index.js` und `api.js` sind ohne Cloudflare prüfbar; nur `entry.js`
              zieht `cloudflare:workers` herein.
```

## Befehle

```
npm test              Unit-Tests (node --test, keine Abhängigkeit)
npm run serve         Entwicklungsserver auf :8000
npm run browsertest   Durchlauf durch die laufende App in Chromium
npm run featuretest   Gesten und die neueren Ansichten, mit echtem Zeiger auf dem Telefon
npm run bruecketest   Der Vertrag zur Android-Hülle, gegen eine nachgebaute Hülle
npm run synctest      Zwei Browser-Kontexte gegen den echten Worker (siehe docs/SYNC.md)
npm run kitest        Die Schnittstelle für die KI, gegen Worker und App (siehe docs/KI.md)
npm run offentest     Die offene Seite: fremder Browser, kein Koppeln (siehe docs/SYNC.md)
npm run build         Webseite nach dist/ legen und auf Vollständigkeit prüfen
npm run shots         Bildschirmfotos beider Anordnungen und beider Fassungen
npm run check         alles zusammen

cd android && ./gradlew assembleRelease    # die Hülle als APK
```

Ins Netz gestellt wird mit `npm run build`: Das legt die Dateien der Webseite nach `dist/`
und prüft dabei, ob jeder Verweis aus `index.html` auch mitgekommen ist. **Übersetzt wird
nichts** — das Wurzelverzeichnis bleibt unverändert lauffähig. Nötig ist der Schritt nur,
weil Cloudflare alles ausliefert, was im angegebenen Verzeichnis liegt, und `.git` dort
nichts zu suchen hat. Einzelheiten in `docs/CLOUDFLARE.md`.

`_headers` gilt für beide Seiten — Cloudflare liest es, und `tools/serve.py` liest es auch,
damit sich der Entwicklungsbetrieb nicht anders verhält als das Netz. Prüfen lässt sich das
mit `python3 tools/serve.py 8001 dist` plus `node tools/browsertest.mjs http://localhost:8001/`.

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
10. **Der Abgleich führt je Zeile zusammen, nie je Datei.** Wer das ändert, verliert bei
    jedem Zusammentreffen zweier Geräte die Arbeit eines der beiden.
    Der Stand liegt je Raum in einem **Durable Object** (`worker/raum.js`) und nicht in KV:
    Nur dort liegen „lies den Stempel, vergleiche, schreibe“ in einem Schritt. In KV war
    die Prüfung gegen gleichzeitiges Schreiben Zierde — und nebenbei brauchte KV einen
    Schritt im Dashboard, den zu vergessen einen Abgleich ergab, der nie lief.
11. **Die offene Seite ist eine ausdrückliche Entscheidung, kein Zustand.** Ohne das
    Secret `OEFFENTLICH` ist sie aus, und es gibt keinen Weg, sie aus Versehen
    anzuschalten. Ist sie an, gibt `/offen` das Geheimnis **jedem** heraus — das ist der
    Zweck und keine Lücke; `test/offen.test.js` nagelt beide Richtungen fest, damit
    niemand die eine später für einen Fehler hält. Die Einstellungen sagen es sichtbar.
12. **Der Abgleich hat kein Passwort und keine Losung.** Das Geheimnis ist Zufall aus
    `crypto.getRandomValues` und wandert über den Koppel-Link aufs nächste Gerät —
    hinter der Raute, weil das ein Browser nie an einen Server schickt. Wer daraus wieder
    etwas Getipptes macht, holt sich PBKDF2, die Wartezeit und die Vertipper zurück.
    Der Preis steht dafür in der Oberfläche: Wer den Link hat, hat die Aufgaben.
13. **Verweise zeigen auf den Titel, nicht auf eine Kennung.** `[[Steuer]]` tippt man,
    statt es nachzuschlagen. Der Preis ist ein Verweis, der nach dem Umbenennen ins Leere
    zeigt — dann sagt die Oberfläche das (`.verweis--leer`) und tut nicht so, als ginge er
    noch irgendwohin.
14. **`/api/` ist die eine Stelle, an der der Server entschlüsselt** — und sie steht
    **neben** dem Abgleich, nie darin. `/sync/…` bleibt Ende zu Ende verschlüsselt; wer
    das vermischt, nimmt der ganzen App die Zusage. Der Schlüssel kommt je Anfrage im
    Kopf und wird nirgends abgelegt. Einzelheiten in `docs/KI.md`.
15. **Der Worker rechnet Termine in einer angegebenen Zeitzone**, nie in seiner eigenen:
    Seine Ortszeit ist UTC. `domain/zonen.js` ist die einzige Stelle dafür; `time.js` gilt
    weiter auf den Geräten, wo die Ortszeit die des Nutzers ist.
16. **Zeit je Aufgabe wird aus den Fokusrunden gerechnet, nie mitgeschrieben.** Es gibt
    keine zweite Stoppuhr; `jeAufgabe` in `src/domain/zeit.js` ist die einzige Stelle.
    Abgebrochene Runden zählen null — sonst wird Abbrechen zur Leistung.

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
- **Sind die Erinnerungen aus, geht ein leerer Zeitplan an die Hülle** — und der löscht dort
  alle Wecker. Ein Schalter, der nichts abschaltet, kostet nicht die Erinnerung, sondern
  die ganze App: Wer trotzdem geweckt wird, schaltet sie im System stumm.
- **Ein Wochenziel bricht die Serie erst, wenn die Woche vorbei ist.** Die laufende Woche
  zählt nie gegen einen — dieselbe Regel wie beim heutigen Tag, nur eine Ebene höher.
- **Gestalten schaltet die Stufe frei, nichts anderes.** Kein Kauf, keine Kiste, kein
  Zufall. Was gesperrt ist, steht sichtbar da und nennt die Stufe.
- **Erinnerungen laufen nur, solange die Seite offen ist.** Ohne Server kann
  niemand einen geschlossenen Tab wecken; das wird so gesagt und nicht kaschiert.

## Gestaltung

**Blaupause.** Papiergrund mit feinem Raster, tiefschwarze Tinte, harte Kanten, kein
Weichzeichner. Jede Fläche hat einen sichtbaren Rand und einen **harten** Schatten — einen
versetzten Block in Tintenfarbe. Was gedrückt wird, fährt in seinen Schatten hinein.

Drei Farben mit fester Bedeutung, und nur drei:

| Farbe | Bedeutung |
| --- | --- |
| `--acid` | Handlung. Der eine Knopf, der etwas auslöst. |
| `--violett` | Der Begleiter, Fortschritt, Fokus |
| `--rot` | Überfällig, dringend — nie dekorativ |

Dazu die technischen Beigaben, die den Ton tragen: gestrichelte Hilfslinien,
Zählmarken in Klammern („(3)“), Beschriftungen in versaler Schreibmaschinenschrift,
Auszeichnung schwer, schmal und versal.

**Keine Webschrift.** Die App lädt nichts nach — auch die Hülle nicht, deren Netzerlaubnis
ausschließlich dem Abgleich dient. Was zählt, ist die Anmutung, und die stellt jedes System
aus dem her, was es hat.

**Zwei Fassungen, hell und dunkel** — dieselbe Sprache, getauschte Rollen. Das ist keine
Bequemlichkeit: Die Acidfarbe leuchtet auf schwarzem Papier stärker als auf weißem. Die
Wahl steht in der Datenbank; im `localStorage` liegt eine Kopie, damit beim Laden nichts
aufblitzt.

**Der Begleiter ist ein Himmelskörper, kein Gesicht.** Scheibe mit hartem Rand,
gestrichelte Umlaufbahn dahinter, Trabant, außen der Fortschrittsring. Kein Emoji, keine
Augen — beides sah in jeder Größe nach Aufkleber aus. Sein Kern ist ein echter Verlauf und
damit das einzige Stück Licht im ganzen Programm.

Zwei Anordnungen, Umbruch bei 960 px: Telefon mit Leiste unten und Begleiter-Streifen über
der Liste; Schreibtisch mit Seitenleiste und Nebenspalte, auf dem **alle** Funktionen samt
Einstellungen ohne Untermenü erreichbar sind.

### Bewegung

Die Effekte sind der Idee nach von reactbits.dev übernommen und mit Bordmitteln
nachgebaut — React kommt nicht in Frage: null Laufzeitabhängigkeiten, kein Bauschritt, und
die Hülle lädt ohnehin nichts von außen nach, auch mit Netzerlaubnis nicht. Sie stehen in
`src/ui/motion.js`:

| Vorlage | Hier |
| --- | --- |
| Split Text | Überschriften laufen zeichenweise ein |
| Count Up | Kennzahlen zählen hoch statt zu erscheinen |
| Decrypt Text | Der Begleiter „findet“ seinen Satz |
| Click Spark | Ein Tintenstern beim Abhaken |

**Jede Bewegung fragt vorher, ob sie darf.** Wer „Bewegung reduzieren“ eingestellt hat,
bekommt sofort das Endergebnis — nicht dieselbe Bewegung, nur schneller.

Ansichten werden **nur bei echten Änderungen** neu gebaut, nicht bei jedem Sekundentakt.
Wer das vergisst, startet jede Einlauf-Animation im Sekundentakt neu, und die Zeilen werden
nie ganz sichtbar. Ausnahmen tragen `taktet: true` in `src/ui/app.js` — Fokus und
Begleiter, weil dort eine Uhr läuft.

## Die Android-Hülle

Eine App, die nichts weiter tut, als die Webseite anzuzeigen und einen Wecker zu stellen.
Sie kennt keine Aufgaben, keine Datenbank und keine Regeln. Einzelheiten in
`docs/ANDROID.md`; die Kurzfassung:

- **Kein Push.** Es gibt keinen Server und kein Konto. Das Telefon weckt sich selbst zu
  Zeitpunkten, die die Webseite ausgerechnet hat (`plannedNags` in `src/domain/nag.js`).
- **`INTERNET` ausschließlich für den Geräteübergreifend-Abgleich.** Der eigene Ursprung
  geht nie ins Netz — `Vermittler.kt` beantwortet jede Anfrage dorthin aus dem Paket. Die
  Berechtigung öffnet nur den Weg für den verschlüsselten Abgleich, und auch den nur, wenn
  er in den Einstellungen eingeschaltet ist. Ohne eingetragene Adresse der Ablage findet er
  dort nichts — der Ursprung der Hülle ist keine echte Adresse im Netz.
- Der Ursprung `https://appassets.androidplatform.net/web/…` **darf sich nie ändern** — er
  ist der Schlüssel, unter dem IndexedDB liegt.
- Der Vertrag zwischen beiden Seiten steht in `src/ui/bruecke.js` und `Bruecke.kt` und wird
  von `tools/bruecketest.mjs` im Browser gegen eine nachgebaute Hülle geprüft. Ein Emulator
  läuft hier nicht.

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
- **Kein Inline-Skript und kein `style`-Attribut.** Die Sicherheitsrichtlinie erlaubt nur
  `'self'`; eine Ausnahme dafür wäre das Loch, durch das später alles andere passt.
- Kein Netzzugriff aus der App heraus außer dem Geräteübergreifend-Abgleich, wenn er
  eingeschaltet ist — an den eigenen Server, mit einem Schlüssel, der die Geräte nie verlässt.
  Kein Konto, keine Telemetrie.
- **Nach jeder Änderung an der Oberfläche `npm run browsertest` und `npm run featuretest`
  laufen lassen.** Der zweite fährt einen echten Zeiger über die Zeilen — eine Geste, die
  nur aus erfundenen Ereignissen besteht, beweist nichts über einen Daumen.
  Behauptungen über eine laufende App sind wertlos; hier lässt sie sich starten.
- Fragen statt raten, wenn etwas offenbleibt.
