package uk.spielerbohne.petodo.domain.pet

import uk.spielerbohne.petodo.domain.Balance

/**
 * Sprechblasen-Kategorien (Projektplan, Abschnitt 8.3).
 *
 * `domain/` kennt keine Texte — es entscheidet nur, **welche** Kategorie dran ist. Die
 * Texte liegen in `strings.xml`, wie alle anderen auch.
 */
enum class SpeechCategory(val key: String) {

    // Sofortige Reaktionen
    START("start"),
    FOCUS_BEGINS("focus_begins"),
    FOCUS_ENDS("focus_ends"),
    BREAK_OVER("break_over"),
    TASK_DONE("task_done"),
    LIST_EMPTY("list_empty"),
    TASK_DUE("task_due"),
    FED("fed"),
    PLAYED("played"),
    PATTED("patted"),

    // Getaktete Bemerkungen
    HUNGRY("hungry"),
    TIRED("tired"),
    SICK("sick"),
    HAPPY("happy"),
    BORED("bored");

    companion object {
        val REACTIONS = listOf(
            START, FOCUS_BEGINS, FOCUS_ENDS, BREAK_OVER, TASK_DONE,
            LIST_EMPTY, TASK_DUE, FED, PLAYED, PATTED,
        )

        val MOODS = listOf(HUNGRY, TIRED, SICK, HAPPY, BORED)
    }
}

object PetSpeech {

    /**
     * Welche getaktete Bemerkung gerade passt.
     *
     * **Bedürfnisse gehen Stimmungen vor**: Ein hungriges Pet erzählt nicht, wie gut
     * gelaunt es ist. Reihenfolge: hungrig → müde → krank → gut gelaunt → gelangweilt.
     */
    fun moodCategory(values: PetValues): SpeechCategory = when {
        values.satiety < Balance.BUBBLE_HUNGRY_BELOW -> SpeechCategory.HUNGRY
        values.energy < Balance.BUBBLE_TIRED_BELOW -> SpeechCategory.TIRED
        values.stage == HealthStage.SICK || values.stage == HealthStage.MISERABLE -> SpeechCategory.SICK
        values.mood > Balance.BUBBLE_HAPPY_ABOVE -> SpeechCategory.HAPPY
        else -> SpeechCategory.BORED
    }

    /**
     * Wählt einen Text aus einer Kategorie.
     *
     * Derselbe Gedanke wiederholt sich unmittelbar nur mit 35 % Wahrscheinlichkeit —
     * sonst klingt das Pet wie eine kaputte Schallplatte. Eine leere Kategorie bedeutet:
     * Das Pet sagt dazu nichts.
     */
    fun pick(
        texts: List<String>,
        previous: String?,
        roll: Double,
        chooser: (Int) -> Int,
    ): String? {
        if (texts.isEmpty()) return null
        if (texts.size == 1) return texts.first()

        val candidate = texts[chooser(texts.size).coerceIn(texts.indices)]
        if (candidate != previous) return candidate

        // Wiederholung nur mit der erlaubten Wahrscheinlichkeit.
        if (roll < Balance.BUBBLE_REPEAT_CHANCE) return candidate

        val alternatives = texts.filter { it != previous }
        // Stehen in einer Kategorie nur gleiche Texte, gibt es nichts zu wechseln —
        // dann lieber wiederholen als aus einer leeren Liste zu würfeln.
        if (alternatives.isEmpty()) return candidate

        return alternatives[chooser(alternatives.size).coerceIn(alternatives.indices)]
    }
}
