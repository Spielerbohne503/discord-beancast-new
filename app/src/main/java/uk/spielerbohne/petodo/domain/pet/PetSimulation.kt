package uk.spielerbohne.petodo.domain.pet

import uk.spielerbohne.petodo.domain.Balance
import java.time.Duration
import java.time.Instant

/**
 * Der Stand des Pets zu einem Zeitpunkt.
 *
 * Das ist ein *Zwischenstand*, kein Besitzer der Wahrheit: Die Wahrheit ist das
 * Append-only-Log plus die verstrichene Zeit. Diese Zeile spart nur das Nachrechnen von
 * vorne.
 */
data class PetState(
    val values: PetValues,
    val xp: Int,
    val lastComputedAt: Instant,
    val lastFedAt: Instant? = null,
    val lastPlayedAt: Instant? = null,
    val lastPattedAt: Instant? = null,
) {
    val level: Int get() = Level.forXp(xp)
    val stage: HealthStage get() = values.stage

    fun lastAt(type: RewardType): Instant? = when (type) {
        RewardType.FEED -> lastFedAt
        RewardType.PLAY -> lastPlayedAt
        RewardType.PAT -> lastPattedAt
        else -> null
    }

    companion object {
        fun initial(at: Instant) = PetState(values = PetValues.INITIAL, xp = 0, lastComputedAt = at)
    }
}

/**
 * **Die eine reine Funktion, die den Wertestand berechnet** (Einbahnstraße aus dem
 * Projektplan). Wer an einer zweiten Stelle nachrechnet, hat den Fehler eingebaut, den
 * diese Regel verhindern soll.
 *
 * Kein tickender Dienst: Beim Öffnen der App und bei jedem Ereignis wird einmal
 * gerechnet — letzter Stand + verstrichene Zeit + Ereignisse.
 */
object PetSimulation {

    /**
     * @param previous der zuletzt gespeicherte Stand
     * @param events die Ereignisse seit [PetState.lastComputedAt], aufsteigend
     * @param load die Überfälligkeitslast (siehe [OverdueLoad])
     */
    fun compute(
        previous: PetState,
        events: List<RewardEvent>,
        now: Instant,
        load: Double,
    ): PetState {
        val decayed = decay(previous.values, previous.lastComputedAt, now, load)

        var values = decayed
        var xp = previous.xp
        var lastFed = previous.lastFedAt
        var lastPlayed = previous.lastPlayedAt
        var lastPatted = previous.lastPattedAt

        events.sortedBy { it.at }.forEach { event ->
            values = values.plus(event.dEnergy, event.dSatiety, event.dMood)
            xp += event.dXp
            when (event.type) {
                RewardType.FEED -> lastFed = event.at
                RewardType.PLAY -> lastPlayed = event.at
                RewardType.PAT -> lastPatted = event.at
                else -> Unit
            }
        }

        return PetState(
            values = values,
            xp = xp.coerceAtLeast(0),
            lastComputedAt = now,
            lastFedAt = lastFed,
            lastPlayedAt = lastPlayed,
            lastPattedAt = lastPatted,
        )
    }

    /**
     * Verfall über die verstrichene Zeit.
     *
     * ```
     * neuerWert = alterWert − (100 / basisStunden) × verstricheneStunden × multiplikator
     * ```
     *
     * Die verstrichene Zeit wird auf 24 Stunden gedeckelt: Nach zwei Wochen Urlaub ist
     * das Pet nicht schlechter dran als nach einem Tag. Ohne diesen Deckel kommt man von
     * einer Reise zurück, findet ein elendes Pet vor und löscht die App.
     */
    fun decay(values: PetValues, from: Instant, to: Instant, load: Double): PetValues {
        val hours = elapsedHours(from, to)
        if (hours <= 0.0) return values

        val base = OverdueLoad.baseMultiplier(load)
        val mood = OverdueLoad.moodMultiplier(load, values.energy, values.satiety)

        return PetValues.of(
            energy = values.energy - rate(Balance.DECAY_HOURS_ENERGY) * hours * base,
            satiety = values.satiety - rate(Balance.DECAY_HOURS_SATIETY) * hours * base,
            mood = values.mood - rate(Balance.DECAY_HOURS_MOOD) * hours * mood,
        )
    }

    /** Verstrichene Stunden, gedeckelt und nie negativ. */
    fun elapsedHours(from: Instant, to: Instant): Double {
        val minutes = Duration.between(from, to).toMinutes()
        if (minutes <= 0) return 0.0
        return (minutes / 60.0).coerceAtMost(Balance.DECAY_MAX_ELAPSED_HOURS)
    }

    private fun rate(baseHours: Double): Double = Balance.VALUE_MAX / baseHours
}
