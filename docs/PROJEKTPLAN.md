# Projektplan — Android-Aufgabenverwaltung mit Pet-Begleiter

Stand: 16. August 2026 · Version: Planungsstand v1 · Arbeitstitel: (offen)

> Diese Datei ist die Textfassung des übergebenen PDF-Projektplans und gilt als
> verbindliche Spezifikation. Bei Widersprüchen zwischen Prompt und diesem Dokument
> gewinnt dieses Dokument.

## 1. Ziel und Prüfstein

Eine Android-App, die eine ernstzunehmende Aufgabenverwaltung mit einem
Tamagotchi-artigen Begleiter koppelt. Erledigte Arbeit versorgt das Pet; vernachlässigte
Arbeit macht es krank. Beide Schichten tragen sich gegenseitig.

**Prüfstein:** Am Tag nach dem ersten Build kann ich TickTick deinstallieren, ohne etwas zu
vermissen, das ich täglich brauche. Nicht „hat alle Features“, nicht „sieht fertig aus“,
sondern: trägt einen echten Tag.

**Abgrenzung zur Windows-Version:** Kein Code wird übernommen, nur das Verhalten —
Bedürfniswerte, XP-Kurve, Timer mit absolutem Endzeitpunkt, Sprechblasen-Taktung,
Skin-Konzept, Fallback-Philosophie („Ausfall eines Bausteins führt zu eingeschränktem,
nicht zu gestörtem Betrieb“). Auf Android entfallen: Overlay, Klickdurchlässigkeit,
Fensterkanten, Laufen, Fallen, Tragen, Mehrmonitor-Logik, Zustandsautomat mit zehn
Zuständen.

## 2. Produktentscheidungen (festgezurrt)

| Frage | Entscheidung |
| --- | --- |
| Wo lebt das Pet? | Nur In-App + persistente Benachrichtigung |
| Wie viele Bedürfniswerte? | Drei — Energie, Sättigung, Laune |
| Krankheit? | Ja, bei überfälligen Aufgaben, mit sichtbaren Stufen |
| Krankheitsoptik | Eigene Sprite-Varianten je Skin, mit Rückfall auf gesund |
| Nag-Verhalten | Täglich zur selben Uhrzeit, eskalierend, Aufräum-Frage ab Tag 7 |
| Aufräum-Bonus | Verschieben und Löschen heilen genauso wie Erledigen |
| Sync | Erst nach v1 — aber Schema von Tag eins vorbereitet |
| Konto / Netzwerk | Keins. v1 ist vollständig offline. |
| Verteilung | Sideload-APK, kein Play Store |

## 3. Technischer Rahmen

| Baustein | Wahl | Begründung |
| --- | --- | --- |
| Sprache / UI | Kotlin + Jetpack Compose, Material 3 | Exakte Alarme, Foreground-Services und Widgets sind hier Standard statt Krampf |
| Datenbank | Room (SQLite) | Migrationen, Testbarkeit, Sync-Fähigkeit |
| Exakte Erinnerungen | `AlarmManager.setExactAndAllowWhileIdle` | Einziger Weg, der Doze überlebt |
| Timer / Statuszeile | Foreground Service | Pflicht für dauerhafte Hintergrundarbeit |
| Aufräumjobs | WorkManager | Tägliche Neuberechnung, Aufräumen alter Events |
| Einstellungen | DataStore | Ablösung von SharedPreferences |
| DI | Manuell (kein Hilt) | Bei Solo-Projekt völlig ausreichend |
| Mindest-SDK | API 26 (Android 8) | Notification Channels als Untergrenze |
| Ziel-SDK | aktuell | `SCHEDULE_EXACT_ALARM` bzw. `USE_EXACT_ALARM` beachten |

Berechtigungen: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`,
`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## 4. Datenmodell

Alles hier ist eine Einbahnstraße — nachträglich zu ändern kostet Datenmigration. Auch
Felder, die v1 nicht nutzt, kommen jetzt rein.

### 4.1 Aufgaben

```kotlin
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,          // UUID, nie Auto-Increment
    val listId: String,                  // v1: immer "inbox"
    val title: String,
    val note: String? = null,
    val dueAt: Long? = null,             // UTC-Millis
    val dueTimeLocal: String? = null,    // "HH:mm" — Anker für den Nag
    val hasTime: Boolean = false,        // nur Datum vs. Datum + Uhrzeit
    val priority: Int = 1,               // 0 niedrig · 1 normal · 2 hoch · 3 dringend
    val rrule: String? = null,           // RFC 5545, v1 immer null
    val completedAt: Long? = null,
    val sortKey: String,                 // Fractional Index (Lexorank), NICHT Integer
    val nagCount: Int = 0,               // wie oft bereits gemahnt
    val nagLastAt: Long? = null,
    val snoozedUntil: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,                 // Sync-Ticket
    val deletedAt: Long? = null          // Tombstone statt echtem Löschen
)
```

*Warum Fractional Index statt Position:* Bei Integer-Positionen schreibt man beim
Umsortieren die halbe Tabelle neu — und beim späteren Sync ist der Konflikt unauflösbar.

*Warum `dueTimeLocal` zusätzlich:* Der Nag braucht die Uhrzeit, nicht den Zeitstempel. Mit
+86.400.000 ms wandert die 8-Uhr-Erinnerung bei jeder Zeitumstellung. Korrekt ist
`LocalDate.plusDays(1).atTime(dueTimeLocal)`.

### 4.2 Listen

```kotlin
@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int?,
    val sortKey: String,
    val excludeFromNag: Boolean = false,  // "Irgendwann"-Liste = Druckventil
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
```

v1 legt beim ersten Start zwei Listen an: `inbox` und `irgendwann` (mit
`excludeFromNag = true`). Die UI zeigt nur die Inbox.

### 4.3 Belohnungsereignisse (Append-only)

```kotlin
@Entity(tableName = "reward_events")
data class RewardEventEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val type: String,        // TASK_DONE · TASK_CLEANED · FOCUS_DONE · FEED · PLAY · PAT · TASK_CREATED
    val dEnergy: Int, val dSatiety: Int, val dMood: Int, val dXp: Int,
    val refId: String? = null
)
```

*Warum ein Log und kein überschriebener Zustand:* Kostet jetzt eine Tabelle, schenkt später
Statistik, Rückgängig-Machen und Sync. Die Simulation liest das Log, niemand rechnet an
zweiter Stelle nach.

### 4.4 Pet-Zustand und Fokussitzungen

```kotlin
@Entity(tableName = "pet_state")          // genau eine Zeile
data class PetStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastComputedAt: Long,
    val energy: Float, val satiety: Float, val mood: Float,
    val xp: Int, val level: Int,
    val skinId: String = "default",
    val lastFedAt: Long?, val lastPlayedAt: Long?, val lastPattedAt: Long?
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val startedAt: Long,
    val endsAt: Long,                     // absoluter Endzeitpunkt, kein Restwert
    val kind: String,                     // FOCUS · SHORT_BREAK · LONG_BREAK
    val completedAt: Long?,
    val abortedAt: Long?
)
```

## 5. Kernmechanik: Werte, Krankheit, Belohnung

### 5.1 Verfall

Kein tickender Dienst. Beim Öffnen der App und bei jedem Ereignis einmal rechnen:

```
neuerWert = alterWert − (100 / basisStunden) × verstricheneStunden × multiplikator
```

| Wert | Basis Desktop | Basis Android |
| --- | --- | --- |
| Energie | 6 h | 12 h |
| Sättigung | 8 h | 16 h |
| Laune | 12 h | 24 h |

Deckel: Verstrichene Zeit wird auf 24 Stunden begrenzt. Nach zwei Wochen Urlaub ist das
Pet nicht schlechter dran als nach einem Tag.

### 5.2 Überfälligkeitslast

```
L = Σ über offene, überfällige Aufgaben:
      prioFaktor (niedrig 0,5 · normal 1,0 · hoch 1,5 · dringend 2,0)
    + 0,2 × angefangene Überfälligkeitswochen
```

`L` wird bei 10 gedeckelt. Aufgaben in Listen mit `excludeFromNag` zählen nicht. Aufgaben
ohne Fälligkeitsdatum zählen nie.

### 5.3 Multiplikatoren

```
mBasis = 1 + (L / 10) × 2                                     →  1,0 bis 3,0
mLaune = mBasis × (1 + 3 × (1 − (energie + sättigung) / 200))  →  zusätzlich bis ×4
```

### 5.4 Krankheitsstufen

Gemessen am Durchschnitt der drei Werte — nicht direkt an L.

| Ø der drei Werte | Zustand | Darstellung |
| --- | --- | --- |
| > 70 | Gesund | normale Sprites, normale Sprüche |
| 40–70 | Angeschlagen | müdere Animation, gedämpfte Farben |
| 15–40 | Krank | hustet, schläft viel, Benachrichtigungs-Icon wechselt |
| < 15 | Elend | reagiert kaum, spricht fast nicht mehr |

**Harte Regel:** kein Tod, kein Punkt ohne Wiederkehr. Maximum ist ein teilnahmsloses Pet.

### 5.5 Belohnungen

| Anlass | Energie | Sättigung | Laune | XP | Sperre |
| --- | --- | --- | --- | --- | --- |
| Aufgabe erledigt | — | +12 | +4 | 5 | — |
| Aufgabe aufgeräumt (verschoben / gelöscht) | +6 | +6 | +6 | 5 | nur bei überfälligen |
| Fokusrunde beendet | +20 | — | +15 | 25 | — |
| Füttern | — | +35 | +10 | 5 | max. alle 4 h |
| Spielen | −12 | −6 | +30 | 10 | max. alle 2 h |
| Streicheln | — | — | +12 | 1 | max. alle 30 min |
| Aufgabe erfasst | — | — | — | 1 | max. 10 ×/Tag |

Werte werden bei 0 und 100 gekappt.

*Warum Aufräumen genauso heilt:* Ohne diese Regel bestraft man Ehrlichkeit und belohnt
heimliches Löschen. Fünf Leichen wegräumen ergibt +30 auf alle Werte — von Elend auf
Angeschlagen in zwei Minuten. Das ist die Fluchttür.

*Warum Sperrzeiten:* Ohne sie tippt man sich aus jeder Krankheit heraus.

*Warum XP fürs Erfassen:* Wenn Eintragen nur schaden kann, trägt man nichts mehr ein.
Krankheit hängt deshalb ausschließlich an überfälligen, nie an offenen Aufgaben.

### 5.6 Level

Erste Stufe kostet 100 XP, jede weitere das 1,5-fache der vorigen. Level schalten Skins frei
— sonst nichts. Keine Währung, kein Shop, keine Accessoires.

## 6. Erinnerungen und Nag-System

### 6.1 Ablauf

1. Aufgabe mit Fälligkeit bekommt einen exakten Alarm.
2. Alarm feuert → ist die Aufgabe erledigt? Dann nichts tun.
3. Sonst: Benachrichtigung posten, `nagCount++`, nächsten Alarm auf morgen, gleiche
   Uhrzeit setzen (über `LocalDate.plusDays(1)`).
4. Wiederholt sich, bis erledigt, verschoben oder gelöscht.

### 6.2 Eskalation

| Tag | Verhalten |
| --- | --- |
| 1 | Normale Erinnerung, lautlos |
| 2–3 | „Steht immer noch offen“, Pet kommentiert |
| 4–6 | Höhere Priorität, Ton statt lautlos |
| ab 7 | Aufräum-Frage: „Willst du das noch?“ → [Erledigt] [Neu terminieren] [Löschen] |

Jede Antwort auf die Aufräum-Frage löst den Aufräum-Bonus aus. Belohnt wird das
Entscheiden, nicht das Erledigen.

### 6.3 Aktionsknöpfe

Jede Erinnerung trägt: Erledigt · +1 Std · Morgen. `+1 Std` setzt `snoozedUntil`, `Morgen`
schiebt die Fälligkeit.

### 6.4 Sammel-Benachrichtigung

Ab drei überfälligen Aufgaben: eine gruppierte Meldung mit aufklappbarer Liste statt fünf
Einzelvibrationen (Notification Groups mit Summary).

### 6.5 Ruhezeit

Konfigurierbares Fenster, in dem nicht gemahnt wird (Vorgabe 23:00–08:00). Fällige Nags
werden auf das Fensterende geschoben, nicht verworfen.

### 6.6 Kanäle

| Kanal | Priorität | Zweck |
| --- | --- | --- |
| `reminders` | hoch | Fälligkeiten und Nag |
| `focus` | niedrig, still, dauerhaft | Timer und Statuszeile |
| `pet` | niedrig, still | Pet-Meldungen |

### 6.7 Robustheit

- IDs deterministisch aus der Task-UUID ableiten (stabiler Hash).
- `BOOT_COMPLETED`-Receiver, der alle offenen Alarme neu registriert.
- Akku-Ausnahme einmalig beim ersten Start erklären und anfordern.
- Hersteller-Hürde: bei Samsung, Xiaomi, Huawei und OnePlus zusätzlich auf die
  herstellereigene App-Verwaltung hinweisen.
- Täglicher WorkManager-Job, der die Alarmliste gegen die Datenbank abgleicht.

## 7. Fokus-Timer

- Abfolge Fokus / kurze Pause / lange Pause, Vorgabe 25 / 5 / 20 Minuten, lange Pause
  nach vier Runden, alles einstellbar.
- Absoluter Endzeitpunkt in der Datenbank, kein heruntergezählter Rest.
- Foreground Service mit der Statuszeile als Pflicht-Benachrichtigung.
- Nach der Fokusrunde startet die Pause automatisch. Nach der Pause kehrt der Timer in
  den Bereitzustand zurück, statt selbsttätig weiterzulaufen.
- Anhalten, fortsetzen, abbrechen. Abgebrochene Runden zählen nicht als Belohnung.
- Optional mit einer Aufgabe verknüpft.

Statuszeile:

```
Ohne Timer:   🐣  Zufrieden · 3 von 7 heute      [+ Aufgabe]  [Fokus]
Mit Timer:    🍅  Fokus 18:42 · Krafttraining     [Pause]  [Stopp]
Nach Fokus:   ☕  Pause 4:12                       [Überspringen]
```

Nicht wischbar, niedrige Priorität, kein Ton. Das Pet-Icon wechselt mit dem Zustand.

## 8. Darstellung und Skins

### 8.1 v1 zeigt keine Sprites

v1 startet mit einer Statustafel: Name, Level, Zustand, drei Balken. Kein einziges Sprite.

### 8.2 Skin-Format (jetzt festlegen, auch ohne Sprites)

Ein Skin ist ein Ordner mit `skin.json` und PNG-Bildstreifen. Die Krankheitsstufe ist eine
eigene Dimension mit Rückfall auf gesund:

```json
{
  "id": "beispiel",
  "name": "Beispielwesen",
  "frameWidth": 32, "frameHeight": 32, "scale": 4, "fps": 8,
  "animations": {
    "idle":       { "file": "idle.png",       "frames": 4, "loop": true  },
    "idle_sick":  { "file": "idle_sick.png",  "frames": 4, "loop": true  },
    "sleep":      { "file": "sleep.png",      "frames": 2, "loop": true  },
    "sleep_sick": { "file": "sleep_sick.png", "frames": 2, "loop": true  },
    "eat":        { "file": "eat.png",        "frames": 4, "loop": false },
    "happy":      { "file": "happy.png",      "frames": 4, "loop": false }
  }
}
```

Auflösungsregel: `idle_sick` fehlt → `idle`.

### 8.3 Sprechblasen

Alle Texte in einer Ressourcendatei, nach Anlass gruppiert. Leere Kategorie = das Pet sagt
dazu nichts.

- Reaktionen (sofort): Start, Fokus beginnt/endet, Pause vorbei, Aufgabe erledigt, Liste
  leer, Fälligkeit, gefüttert, bespielt, gestreichelt
- Eigene Bemerkungen (getaktet): hungrig < 30 %, müde < 30 %, gut gelaunt > 85 %, sonst
  gelangweilt — plus krank

Bedürfnisse gehen Stimmungen vor. Derselbe Gedanke wiederholt sich nur mit 35 %
Wahrscheinlichkeit unmittelbar. v1: fünf Texte pro Kategorie.

## 9. Bildschirme

```
Untere Leiste:  Heute · Fokus · Pet · Mehr

Heute    Pet-Streifen oben (3 Balken + Zustand)
         Blöcke: überfällig · heute · später
         Schnell-Eingabe unten, Aufgabe per Tippen abhaken
Fokus    Timer · verknüpfte Aufgabe · heutige Runden · Start/Pause/Stopp
Pet      große Statustafel · Zustandsbericht · Level und XP
         Knöpfe: Füttern · Spielen · Streicheln
Mehr     Einstellungen
```

## 10. v1-Umfang

Drin: Aufgabe anlegen (Titel, optional Fälligkeit mit Uhrzeit, optional Notiz); abhaken,
wieder öffnen, löschen, Titel bearbeiten; Heute-Ansicht mit drei Blöcken; Erinnerung +
Nag-Kette mit Eskalation und Aufräum-Frage; Aktionsknöpfe in der Benachrichtigung;
BOOT_COMPLETED-Receiver + Akku-Ausnahme-Dialog; Pomodoro mit Foreground-Service und
absolutem Endzeitpunkt; persistente Statuszeile; drei Werte, XP, Level, Belohnungen,
Krankheitsstufen; Statustafel statt Sprite; alles lokal.

Draußen — aber im Schema vorbereitet: Listen-UI · Tags · Prioritäten-UI · Wiederholungen ·
Unteraufgaben · Filter · Habits · Widgets · Statistik · Kalenderansichten · Datumsparser ·
Share-Sheet-Target · Sprites · Sync.

## 11. Meilensteine

| | Inhalt | Danach ist die App… |
| --- | --- | --- |
| M1 | Room-Schema, Repository, CRUD, Heute-Ansicht, Compose-Grundgerüst | eine dumme Liste |
| M2 | AlarmManager, Nag-Kette, Eskalation, Aufräum-Frage, Boot-Receiver, drei Kanäle, Aktionsknöpfe, Akku-Ausnahme | eine Erinnerungs-App |
| M3 | Foreground-Service, Pomodoro, Statuszeile, Fokussitzungen | zusätzlich ein Timer |
| M4 | Werteberechnung, Event-Log, Krankheit, Level, Statustafel, Sprechblasen | deine App |

Testabdeckung: Werteberechnung, Lastformel, Nag-Eskalation und Wiederholungsregeln sind
reine Funktionen ohne Android-Abhängigkeit — die gehören unter Unit-Tests.

## 12. Checkliste der Einbahnstraßen

- [ ] UUID als Primärschlüssel, nirgends Auto-Increment
- [ ] `updatedAt` und `deletedAt` (Tombstone) auf allen synchronisierbaren Tabellen
- [ ] `listId` existiert, auch wenn v1 nur die Inbox zeigt
- [ ] `priority` als Feld mit Default, ohne UI
- [ ] `rrule` als String-Feld nach RFC 5545, kein Eigenbau
- [ ] Sortierung als Fractional Index, nicht als Integer-Position
- [ ] Fälligkeit als UTC-Instant plus separates `dueTimeLocal`
- [ ] Belohnungen als Append-only-Log, nicht als überschriebener Zustand
- [ ] Genau eine reine Funktion berechnet den Wertestand
- [ ] Benachrichtigungs-IDs deterministisch aus der Task-UUID
- [ ] Drei Notification Channels von Anfang an
- [ ] Skin-Manifest mit Krankheitsstufe als eigener Dimension und Rückfallregel
- [ ] Alle Balancing-Zahlen in einer Konstantendatei, nicht verstreut
- [ ] Alle Texte in `strings.xml`, auch bei nur einer Sprache

## 13. Definition of Done

v1 ist fertig, wenn eine Aufgabe für morgen 8 Uhr auch dann weckt, nachdem man:

1. das Handy neu gestartet hat,
2. die App aus den Letzten weggewischt hat,
3. den Akkusparmodus aktiviert hat,
4. über Nacht im Flugmodus war.

Zusätzliche Abnahmepunkte:

- Pomodoro über 25 Minuten mit gesperrtem Bildschirm läuft korrekt aus
- Werte altern nach zwei Tagen Pause um höchstens 24 Stunden
- Fünf überfällige Aufgaben aufräumen hebt das Pet sichtbar um mindestens eine Stufe
- Nag-Kette erreicht an Tag 7 die Aufräum-Frage
- Beschädigte oder fehlende Dateien verhindern den Start nicht

## 14. Roadmap nach v1

| Version | Inhalt |
| --- | --- |
| v1.1 | Wiederholende Aufgaben nach RRULE — von einer wiederkehrenden Aufgabe existiert immer nur eine offene Instanz; verpasste werden übersprungen und als „verpasst“ gezählt, erzeugen aber keine zusätzliche Last |
| v1.2 | Listen- und Tag-UI, Prioritäten-UI, Smart Lists |
| v1.3 | Schnell-Eingabe mit deutscher Datumserkennung, Share-Sheet-Target |
| v1.4 | Erste echte Sprites samt Krankheitsvarianten, Skin-Auswahl, Level-Freischaltungen |
| v2.0 | Habit-Tracker mit Streaks — Habits werden nie überfällig, nagen nie, machen nie krank |
| v2.1 | Homescreen-Widgets, Quick Settings Tile, Statistik |
| v2.2 | Gespeicherte Filter, Eisenhower-Matrix, Agenda-Kalender |
| v3.0 | Sync gegen einen eigenen Endpunkt: `/sync?since=…`, Last-Write-Wins, Tombstones |

Dauerhaft draußen: Kanban, Timeline/Gantt, Kollaboration, Kalender-Abos, Anhänge, Konten.

## 15. Risiken

| Risiko | Wirkung | Gegenmaßnahme |
| --- | --- | --- |
| Hersteller-Prozesskiller | Timer und Erinnerungen fallen aus | Akku-Ausnahme + Hinweis auf App-Verwaltung, WorkManager als Sicherheitsnetz, früher Test auf echtem Gerät |
| Alarme nach Neustart verloren | App wird stumm | BOOT_COMPLETED-Receiver, täglicher Abgleichjob |
| Benachrichtigungs-Müdigkeit | Nutzer schaltet alles ab | Eskalation statt Wiederholung, Sammelmeldung, Ruhezeit, drei Kanäle |
| Krankheits-Todesspirale | App wird an schlechten Tagen gelöscht | Aufräum-Bonus, 24-Stunden-Deckel, kein Tod |
| Projektumfang wächst | Nie fertig | Vier Meilensteine, v1 hart begrenzt |
| Sideload ohne Signierung | Warnhinweise beim ersten Start | Eigener Signaturschlüssel, Verteilung über GitHub Releases + Obtainium |
| Schema-Nachrüstung für Sync | Wochen Migrationsarbeit | Checkliste in Abschnitt 12 vor dem ersten Commit |

## 16. Offene Punkte

- Name der App und des Pets
- Zielgerät und Hersteller
- Signaturschlüssel und Verteilweg
- Zeichenstil der Sprites (erst ab v1.4 relevant)
