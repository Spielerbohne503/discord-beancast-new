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
| Genau EINE reine Funktion berechnet den Wertestand | `domain/pet/PetSimulation.kt` (Phase 4) |
| Benachrichtigungs-IDs deterministisch aus Task-UUID | `domain/notify/NotificationIds.kt` (Phase 2) |
| Drei Notification Channels von Anfang an | `data/notify/Channels.kt` (Phase 2) |
| Alle Balancing-Zahlen in EINER Datei | `domain/Balance.kt` |
| Alle Texte in `strings.xml` | `app/src/main/res/values/strings.xml` |
| Unteraufgaben: `parentId` ohne UI | `TaskEntity.parentId` |
| Ordner: `parentId` auf Listen, ohne UI | `TaskListEntity.parentId` |
| Tags als Mehrfachzuordnung, ohne UI | `TagEntity`, `TaskTagEntity` |
| Mehrere Erinnerungen je Aufgabe | `ReminderEntity` |

Die Felder `parentId`, `tags`/`task_tags` und `priority` haben seit v0.3 eine UI
(Detailseite, Schnell-Eingabe, Mehr-Screen). `rrule` und `reminders` liegen weiterhin
ohne UI im Schema.

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
