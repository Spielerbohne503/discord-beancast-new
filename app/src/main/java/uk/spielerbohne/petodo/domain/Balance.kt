package uk.spielerbohne.petodo.domain

/**
 * Alle Balancing-Zahlen der App an genau einer Stelle.
 *
 * Einbahnstraße aus dem Projektplan (Abschnitt 12): Eine Zahl mit fachlicher Bedeutung
 * gehört hierher, nirgendwo sonst. Auch Werte, die eine spätere Phase erst benutzt,
 * stehen bereits hier — verstreute Konstanten sind später nicht mehr einzusammeln.
 *
 * Diese Datei enthält keinen einzigen Android-Import und ist bewusst frei von Logik.
 */
object Balance {

    // -------------------------------------------------------------------- Heute-Ansicht

    /**
     * Wie lange eine erledigte Aufgabe im Archiv unter der Heute-Liste sichtbar bleibt.
     *
     * Am Tag des Abhakens steht sie im Block „Heute erledigt“ — sonst verschwände die
     * gerade abgehakte Zeile sofort und man könnte sie nicht zurückholen. Danach rutscht
     * sie in das eingeklappte Archiv ganz unten und fällt nach einer Woche auch dort
     * heraus; wer länger zurückschauen will, geht auf „Erledigt“ in der Aufgabenliste.
     */
    const val ARCHIVE_DAYS = 7L

    /** Höchstens so viele Zeilen im Archiv — es ist ein Rückblick, keine zweite Liste. */
    const val ARCHIVE_MAX_ROWS = 50

    // ---------------------------------------------------------------- Verfall (Phase 4)

    /** Basisstunden, in denen ein Wert ohne Ereignisse von 100 auf 0 fällt. */
    const val DECAY_HOURS_ENERGY = 12.0
    const val DECAY_HOURS_SATIETY = 16.0
    const val DECAY_HOURS_MOOD = 24.0

    /** Verstrichene Zeit wird gedeckelt: nach zwei Wochen Urlaub altert das Pet um einen Tag. */
    const val DECAY_MAX_ELAPSED_HOURS = 24.0

    const val VALUE_MIN = 0.0
    const val VALUE_MAX = 100.0

    // ------------------------------------------------------- Überfälligkeitslast (Phase 4)

    /** Beitrag je überfälliger Aufgabe nach Priorität (Index = priority). */
    val LOAD_PRIORITY_FACTOR = doubleArrayOf(0.5, 1.0, 1.5, 2.0)

    /** Zuschlag je angefangener Überfälligkeitswoche. */
    const val LOAD_PER_STARTED_WEEK = 0.2

    /** Obergrenze der Last. */
    const val LOAD_CAP = 10.0

    // ---------------------------------------------------------- Multiplikatoren (Phase 4)

    /** mBasis = 1 + (L / LOAD_CAP) * MULTIPLIER_LOAD_SPAN  →  1,0 bis 3,0 */
    const val MULTIPLIER_LOAD_SPAN = 2.0

    /** mLaune = mBasis * (1 + MOOD_COUPLING * (1 - (energie + sättigung) / 200)) */
    const val MOOD_COUPLING = 3.0

    // ------------------------------------------------------------ Krankheitsstufen (Phase 4)

    /** Grenzen am Durchschnitt der drei Werte. */
    const val HEALTH_THRESHOLD_HEALTHY = 70.0
    const val HEALTH_THRESHOLD_WEAKENED = 40.0
    const val HEALTH_THRESHOLD_SICK = 15.0

    // ------------------------------------------------------------------ Belohnungen (Phase 4)

    // Aufgabe erledigt
    const val REWARD_TASK_DONE_ENERGY = 0
    const val REWARD_TASK_DONE_SATIETY = 12
    const val REWARD_TASK_DONE_MOOD = 4
    const val REWARD_TASK_DONE_XP = 5

    // Aufgabe aufgeräumt (verschoben ODER gelöscht) — nur bei überfälligen Aufgaben
    const val REWARD_TASK_CLEANED_ENERGY = 6
    const val REWARD_TASK_CLEANED_SATIETY = 6
    const val REWARD_TASK_CLEANED_MOOD = 6
    const val REWARD_TASK_CLEANED_XP = 5

    // Fokusrunde beendet
    const val REWARD_FOCUS_DONE_ENERGY = 20
    const val REWARD_FOCUS_DONE_SATIETY = 0
    const val REWARD_FOCUS_DONE_MOOD = 15
    const val REWARD_FOCUS_DONE_XP = 25

    // Füttern
    const val REWARD_FEED_ENERGY = 0
    const val REWARD_FEED_SATIETY = 35
    const val REWARD_FEED_MOOD = 10
    const val REWARD_FEED_XP = 5
    const val COOLDOWN_FEED_MINUTES = 4 * 60L

    // Spielen
    const val REWARD_PLAY_ENERGY = -12
    const val REWARD_PLAY_SATIETY = -6
    const val REWARD_PLAY_MOOD = 30
    const val REWARD_PLAY_XP = 10
    const val COOLDOWN_PLAY_MINUTES = 2 * 60L

    // Streicheln
    const val REWARD_PAT_ENERGY = 0
    const val REWARD_PAT_SATIETY = 0
    const val REWARD_PAT_MOOD = 12
    const val REWARD_PAT_XP = 1
    const val COOLDOWN_PAT_MINUTES = 30L

    // Aufgabe erfasst
    const val REWARD_TASK_CREATED_XP = 1
    const val REWARD_TASK_CREATED_MAX_PER_DAY = 10

    // ------------------------------------------------------------------------ Level (Phase 4)

    /** Die erste Stufe kostet 100 XP … */
    const val LEVEL_FIRST_STEP_XP = 100.0

    /** … jede weitere das 1,5-fache der vorigen. */
    const val LEVEL_GROWTH = 1.5

    // -------------------------------------------------------------- Nag / Eskalation (Phase 2)

    /** Ab diesem Nag-Tag kommentiert das Pet ("steht immer noch offen"). */
    const val NAG_DAY_REMINDER_AGAIN = 2

    /** Ab diesem Nag-Tag: höhere Priorität, Ton statt lautlos. */
    const val NAG_DAY_LOUD = 4

    /** Ab diesem Nag-Tag: Aufräum-Frage "Willst du das noch?". */
    const val NAG_DAY_CLEANUP_QUESTION = 7

    /** Ab so vielen überfälligen Aufgaben wird gruppiert gemeldet statt einzeln. */
    const val NAG_GROUP_THRESHOLD = 3

    /** Vorgabe der Ruhezeit (lokale Uhrzeit, HH:mm). */
    const val QUIET_HOURS_DEFAULT_START = "23:00"
    const val QUIET_HOURS_DEFAULT_END = "08:00"

    /** Vorgabe-Uhrzeit, wenn zu einem Tagestermin eine Uhrzeit gewählt wird. */
    const val DEFAULT_DUE_HOUR = 9

    /**
     * Ungefähre Tageszeiten für die Schnell-Eingabe: „morgens“, „mittags“, „nachmittags“,
     * „abends“, „nachts“.
     *
     * Zahlen mit fachlicher Bedeutung — sie entscheiden, wann gemahnt wird, und gehören
     * deshalb hierher und nicht in den Parser.
     */
    const val VAGUE_MORNING_HOUR = 8
    const val VAGUE_NOON_HOUR = 12
    const val VAGUE_AFTERNOON_HOUR = 15
    const val VAGUE_EVENING_HOUR = 18
    const val VAGUE_NIGHT_HOUR = 21

    /** Aufschub durch den "+1 Std"-Knopf. */
    const val SNOOZE_MINUTES = 60L

    // ------------------------------------------------------------------ Fokus-Timer (Phase 3)

    const val FOCUS_DEFAULT_MINUTES = 25L
    const val SHORT_BREAK_DEFAULT_MINUTES = 5L
    const val LONG_BREAK_DEFAULT_MINUTES = 20L

    /** Nach so vielen Fokusrunden folgt die lange Pause. */
    const val ROUNDS_BEFORE_LONG_BREAK = 4

    // --------------------------------------------------------------- Sprechblasen (Phase 4)

    const val BUBBLE_HUNGRY_BELOW = 30.0
    const val BUBBLE_TIRED_BELOW = 30.0
    const val BUBBLE_HAPPY_ABOVE = 85.0

    /** Wahrscheinlichkeit, denselben Gedanken unmittelbar zu wiederholen. */
    const val BUBBLE_REPEAT_CHANCE = 0.35
}
