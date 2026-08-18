# PeTodo — Arbeitsregeln für Claude

Android-App: Aufgabenverwaltung (TickTick-artig) gekoppelt mit einem Tamagotchi-artigen
Begleiter. Verbindliche Spezifikation: `docs/PROJEKTPLAN.md`. Bei Widerspruch gewinnt der
Projektplan.

**Prüfstein für jede Entscheidung:** Am Tag nach dem ersten Build muss TickTick
deinstallierbar sein, ohne dass etwas fehlt, das täglich gebraucht wird.

## Technischer Rahmen

- Kotlin, Jetpack Compose, Material 3
- Ein Gradle-Modul `:app`, `minSdk 26`, `compileSdk`/`targetSdk` aktuell, Bytecode-Ziel 17
- Versionen ausschließlich über `gradle/libs.versions.toml` (Version Catalog)
- Room + KSP, Navigation Compose, DataStore Preferences, WorkManager
- Paketname `uk.spielerbohne.petodo`
- **Kein Hilt.** Manuelle DI über `di/AppContainer`
- **Keine weiteren Bibliotheken ohne Rückfrage.**
- Kein Netzwerkzugriff, kein Konto, keine Telemetrie. Die App ist vollständig offline.

## Paketstruktur

```
uk.spielerbohne.petodo
  domain/   reine Kotlin-Logik, KEIN android.*- oder androidx.*-Import
  data/     Room-Entities, DAOs, Repositories
  ui/       Compose-Screens und ViewModels
  di/       AppContainer
```

### Die Regel für domain/

`domain/` enthält **keinen einzigen** `android.*`- oder `androidx.*`-Import. Erlaubt sind
Kotlin-Stdlib, `java.time`, `kotlinx.coroutines`. Alles in `domain/` läuft ohne Emulator als
JVM-Unit-Test.

Durchgesetzt von `DomainPurityTest` — der Test scannt die Quelldateien unter
`domain/` und schlägt fehl, sobald dort ein Android-Import auftaucht. Der Test wird
nicht „repariert“, indem man ihn lockert.

Faustregel: Alles, was `Context`, `Intent`, `SharedPreferences`, `Notification` oder
`SystemClock` braucht, gehört nicht in `domain/`. Entscheidungslogik (welche Stufe, welcher
Text, welcher nächste Termin, Werteberechnung) gehört dorthin — der Receiver/Service/das
ViewModel ruft sie nur auf und führt das Ergebnis aus.

## Einbahnstraßen (vor dem ersten Commit festgelegt, nicht nachträglich änderbar)

| Punkt | Ort im Code |
| --- | --- |
| UUID als Primärschlüssel, nirgends Auto-Increment | `data/db/entity/*.kt` |
| `updatedAt` + `deletedAt` (Tombstone) auf allen Tabellen | `data/db/entity/*.kt` |
| `listId` existiert, auch wenn nur Inbox gezeigt wird | `TaskEntity.listId`, `SeedData` |
| `priority` mit Default 1, ohne UI | `TaskEntity.priority`, `domain/model/Priority.kt` |
| `rrule` als RFC-5545-String, v1 immer null | `TaskEntity.rrule` |
| Sortierung als Fractional Index | `domain/sort/FractionalIndex.kt` |
| Fälligkeit als UTC-Instant **plus** `dueTimeLocal` | `TaskEntity.dueAt` / `dueTimeLocal` |
| Belohnungen als Append-only-Log | `reward_events`, nur INSERT |
| Genau EINE reine Funktion berechnet den Wertestand | `domain/pet/PetSimulation.kt` |
| Benachrichtigungs-IDs deterministisch aus Task-UUID | `domain/notify/NotificationIds.kt` |
| Drei Notification Channels von Anfang an | `data/notify/Channels.kt` |
| Alle Balancing-Zahlen in EINER Datei | `domain/Balance.kt` |
| Alle Texte in `strings.xml` | `app/src/main/res/values/strings.xml` |
| Unteraufgaben: `parentId` ohne UI | `TaskEntity.parentId` |
| Ordner: `parentId` auf Listen, ohne UI | `TaskListEntity.parentId` |
| Tags als Mehrfachzuordnung, ohne UI | `TagEntity`, `TaskTagEntity` |
| Mehrere Erinnerungen je Aufgabe | `ReminderEntity` |

Die Felder `parentId`, `tags`/`task_tags` und `priority` haben seit v0.3 eine UI
(Detailseite, Schnell-Eingabe, Mehr-Screen). `rrule` hat seit v0.4 eine UI
(Wiederholungs-Chip auf der Detailseite); `reminders` liegt weiterhin ohne UI im Schema.

## Gestaltung

Das Designsystem liegt vollständig in `ui/theme/` und besteht aus vier Dateien:
`Palette.kt` (Farbwerte), `Theme.kt` (Schemata, Radien), `Type.kt` (Schriftskala),
`Surfaces.kt` (Bausteine). Eine Farbe, ein Radius oder eine Kartenfläche irgendwo anders
im Code ist ein Fehler, auch wenn sie gut aussieht — dieselbe Regel wie für `Balance.kt`,
nur fürs Bild.

- **Kein Dynamic Color.** Die Systemfarben des Geräts würden die drei Bereichsverläufe
  überschreiben, an denen man Pet, Fokus und Überfälliges auseinanderhält.
- **Drei Verläufe, nicht mehr.** `Brand.Pet` (Magenta), `Brand.Focus` (Indigo),
  `Brand.Overdue` (Bernstein). Wer einen vierten einführt, nimmt den drei vorhandenen
  ihre Bedeutung. `Brand.Cool` ist kein vierter Bereich, sondern der neutrale Verlauf für
  Zahlen und Fortschritt.
- **Farbe steckt in den Karten, nicht in der Fläche.** Der Grund ist fast schwarz und
  bleibt es; eine graue Erhöhung (`tonalElevation`) nähme den Verläufen die Leuchtkraft.
  Höhe entsteht durch einen hellen Rand, nicht durch einen helleren Ton.
- **Der Verlauf der Statustafel ist die Krankheitsstufe** (`HealthStage.gradient()`).
  Dieselbe Farbe erscheint im Heute-Streifen, im Lichtschein und in der Navigationsleiste
  — man erkennt den Zustand, bevor man ein Wort gelesen hat.
- Bausteine statt Einzelbau: `GradientCard`, `GlassCard`, `SectionLabel`, `CountBadge`,
  `CircleIconButton`, `ValueTrack`, `ScreenGlow`. Sobald eine Fläche „nur ein bisschen
  anders“ gebaut wird, zerfällt das Bild in Einzelteile.

`values/themes.xml` und `values-night/themes.xml` setzen die Hintergrundfarbe des
Startfensters. Fehlt sie, blitzt beim Öffnen Weiß auf.

## Erledigtes und Verweise

Eine abgehakte Aufgabe bleibt den **ganzen Tag** im Block „Heute erledigt“ stehen — sonst
verschwindet die gerade abgehakte Zeile sofort und man kann sie nicht zurückholen. Am
nächsten Tag rutscht sie in den eingeklappten Rückblick unter der Liste und fällt nach
`Balance.ARCHIVE_DAYS` auch daraus heraus. Der Rückblick ist bewusst leise gebaut: keine
Karten, keine Farbe, kleinere Schrift. Erledigtes ist kein offener Punkt.

Verweise in Titeln und Notizen benutzen die Markdown-Schreibweise (`domain/text/`).
Erkannt werden `[Text](https://…)` und nackte `http(s)://`-Adressen — mehr nicht; eine
Aufgabenverwaltung, die heimlich zum Markdown-Editor wird, kann am Ende beides halb.
In Listen steht die Kurzform (`MarkdownLinks.plainText`), angetippt wird dort die Aufgabe.
Anklickbar sind Verweise auf der Detailseite über `LinkAnnotation` — nicht über einen
selbstgebauten Klickbereich, sonst fehlen die Bedienungshilfen.

## Datenbankmigrationen

`fallbackToDestructiveMigration` ist verboten — ein Schemafehler darf keine Aufgaben
löschen. Jede Schemaänderung bekommt eine Migration in `data/db/Migrations.kt` und wird
gegen die exportierten Schemadateien geprüft:

```
python3 tools/verify_migrations.py
```

Das Werkzeug baut die alte Datenbank aus `app/schemas/…/n.json`, wendet die
`execSQL`-Anweisungen aus `Migrations.kt` an und vergleicht Tabellen, Spalten und
Indizes mit `n+1.json`. Weicht etwas ab, würde Room beim Start des Nutzers abbrechen.
`app/schemas` gehört ins Repository.

Balancing-Zahlen stehen ausschließlich in `domain/Balance.kt`. Eine Zahl mit fachlicher
Bedeutung irgendwo anders im Code ist ein Fehler, auch wenn sie stimmt.

## Fokus-Timer

Der Timer speichert einen **absoluten Endzeitpunkt**, nie einen heruntergezählten Rest.
Anhalten merkt sich `pausedAt`; der Rest ergibt sich daraus. Der Foreground Service zählt
nichts mit — er zeichnet nur neu, was `domain/focus/FocusTimer` aus dem Endzeitpunkt
errechnet. Ein abgelaufener Endzeitpunkt heißt **fertig**, nicht "läuft noch".

Die Statuszeile ist dauerhaft und zeigt auch ohne Timer den Tagesstand.

## Das Pet

Es tickt nichts. Gerechnet wird beim Start der App und bei jedem Ereignis — einmal, in
`PetSimulation.compute`: erst der Verfall über die verstrichene Zeit, dann die Ereignisse
in Zeitfolge. Die Zeile in `pet_state` ist ein **Zwischenstand**, kein zweiter Besitzer
der Wahrheit; die Wahrheit ist das Append-only-Log plus die verstrichene Zeit.

Wer eine zweite Stelle einbaut, an der Werte verändert werden, hat genau den Fehler
eingebaut, den diese Regel verhindert.

Belohnungen werden **im Repository** verbucht, nicht im ViewModel: Sonst zahlt der Weg
über die Benachrichtigung („Erledigt“, „Morgen“) nicht ein. Damit dabei kein Ring
entsteht — das Pet braucht die Aufgaben, die Aufgaben nicht das Pet — kommt die
Belohnungssenke als faules Lambda in `TaskRepository` und `FocusRepository` herein
(`data/pet/RewardSink.kt`).

Zwei Regeln, die leicht kaputtgehen:

- **Aufräumen zählt wie Erledigen.** Eine überfällige Aufgabe zu verschieben oder zu
  löschen gibt Werte. Ohne diese Regel bestraft die App Ehrlichkeit.
- **Sperrzeiten.** Ohne sie tippt man sich aus jeder Krankheit heraus, und die Kopplung
  an die Arbeit ist wertlos. Der Knopf verschwindet dabei nie — er sagt, wie lange noch.

Krankheit hängt ausschließlich an **überfälligen** Aufgaben, nie an offenen. Sonst wird
Erfassen bestraft, und wer nichts mehr einträgt, benutzt die App nicht mehr. Es gibt
keine Stufe unter „elend“: kein Tod, kein Punkt ohne Wiederkehr.

## Sicherung

`domain/backup/` enthält einen eigenen JSON-Codec — keine Bibliothek (nicht erlaubt) und
kein `org.json` (wäre in `domain/` verboten und im JVM-Test nur eine Attrappe). Zahlen
werden als Literal gehalten, damit Millisekunden-Zeitstempel nicht über einen `Double`
laufen.

Das Sicherungsformat ist tabellennah und enthält Tombstones und `updatedAt`. Beim
Wiederherstellen gilt Last-Write-Wins je Zeile — dieselbe Regel wie beim späteren Sync
(Projektplan v3.0). Eine alte Sicherung überschreibt damit keine neuere Arbeit.

## Testkonventionen

- Unit-Tests (JVM, `app/src/test/java/...`) für **alles** in `domain/`. Keine UI-Tests.
- Ein Test pro Regel, benannt nach der Regel, nicht nach der Methode
  (`nag_termin_bleibt_ueber_zeitumstellung_bei_gleicher_uhrzeit`).
- Zeit wird nie aus `System.currentTimeMillis()` gelesen, sondern als Parameter
  (`Instant`, `Clock`) hereingereicht — sonst ist die Logik nicht testbar.
- Nach jedem größeren Schritt: `./gradlew assembleDebug` **und** `./gradlew test`.

## Build

Das Android SDK liegt in dieser Umgebung unter `/opt/android-sdk` (`local.properties`,
nicht eingecheckt). Befehle:

```
./gradlew assembleDebug
./gradlew test
```

## Arbeitsweise

- In nachvollziehbaren Schritten committen, nicht alles am Ende in einem Klumpen.
- Diese Datei aktuell halten, wenn sich Architekturregeln ändern.
- Nachfragen statt raten, wenn etwas offenbleibt.
