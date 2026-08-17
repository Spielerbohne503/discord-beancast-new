package uk.spielerbohne.petodo.domain.nag

import uk.spielerbohne.petodo.domain.Balance

/**
 * Eskalationsstufe einer Erinnerung (Projektplan, Abschnitt 6.2).
 *
 * Eskalation statt Wiederholung: Die immer gleiche Meldung wird nach drei Tagen zu
 * Hintergrundrauschen, und dann schaltet man den Kanal ab — samt der Fälligkeiten, auf
 * die es ankommt.
 */
enum class NagStage(
    /** Ton statt lautlos. */
    val makesSound: Boolean,
    /** Das Pet meldet sich zu Wort. */
    val petComments: Boolean,
    /** "Willst du das noch?" mit [Erledigt] [Neu terminieren] [Löschen]. */
    val asksCleanupQuestion: Boolean,
) {
    /** Tag 1: normale Erinnerung, lautlos. */
    FIRST(makesSound = false, petComments = false, asksCleanupQuestion = false),

    /** Tag 2–3: "steht immer noch offen", das Pet kommentiert. */
    AGAIN(makesSound = false, petComments = true, asksCleanupQuestion = false),

    /** Tag 4–6: höhere Priorität, Ton statt lautlos. */
    LOUD(makesSound = true, petComments = true, asksCleanupQuestion = false),

    /** Ab Tag 7: die Aufräum-Frage. */
    CLEANUP(makesSound = true, petComments = true, asksCleanupQuestion = true),
}

object NagEscalation {

    /**
     * Der wievielte Nag-Tag ansteht.
     *
     * [nagCount] zählt, wie oft bereits gemahnt wurde — beim allerersten Alarm ist er 0,
     * und das ist Tag 1.
     */
    fun dayFor(nagCount: Int): Int = nagCount.coerceAtLeast(0) + 1

    /** Stufe für den anstehenden Nag. */
    fun stageFor(nagCount: Int): NagStage = when {
        dayFor(nagCount) >= Balance.NAG_DAY_CLEANUP_QUESTION -> NagStage.CLEANUP
        dayFor(nagCount) >= Balance.NAG_DAY_LOUD -> NagStage.LOUD
        dayFor(nagCount) >= Balance.NAG_DAY_REMINDER_AGAIN -> NagStage.AGAIN
        else -> NagStage.FIRST
    }
}
