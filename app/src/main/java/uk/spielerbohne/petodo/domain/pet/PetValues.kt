package uk.spielerbohne.petodo.domain.pet

import uk.spielerbohne.petodo.domain.Balance
import kotlin.math.roundToInt

/**
 * Die drei Bedürfniswerte, immer zwischen 0 und 100.
 *
 * Das Kappen passiert im Konstruktor, nicht an den Aufrufstellen: So kann kein Weg durch
 * den Code einen Wert von 137 oder −12 erzeugen, auch kein künftiger.
 */
data class PetValues(
    val energy: Double,
    val satiety: Double,
    val mood: Double,
) {
    init {
        require(energy in Balance.VALUE_MIN..Balance.VALUE_MAX) { "Energie außerhalb 0..100: $energy" }
        require(satiety in Balance.VALUE_MIN..Balance.VALUE_MAX) { "Sättigung außerhalb 0..100: $satiety" }
        require(mood in Balance.VALUE_MIN..Balance.VALUE_MAX) { "Laune außerhalb 0..100: $mood" }
    }

    /** Der Durchschnitt entscheidet über die Krankheitsstufe. */
    val average: Double get() = (energy + satiety + mood) / 3.0

    val stage: HealthStage get() = HealthStage.of(average)

    fun plus(dEnergy: Int, dSatiety: Int, dMood: Int): PetValues = of(
        energy = energy + dEnergy,
        satiety = satiety + dSatiety,
        mood = mood + dMood,
    )

    fun rounded(): Triple<Int, Int, Int> =
        Triple(energy.roundToInt(), satiety.roundToInt(), mood.roundToInt())

    companion object {
        /** Der einzige Weg, Werte zu bauen — kappt bei 0 und 100. */
        fun of(energy: Double, satiety: Double, mood: Double): PetValues = PetValues(
            energy = energy.coerceIn(Balance.VALUE_MIN, Balance.VALUE_MAX),
            satiety = satiety.coerceIn(Balance.VALUE_MIN, Balance.VALUE_MAX),
            mood = mood.coerceIn(Balance.VALUE_MIN, Balance.VALUE_MAX),
        )

        /** Ein frisch geschlüpftes Pet startet satt und zufrieden. */
        val INITIAL = of(
            energy = Balance.VALUE_MAX,
            satiety = Balance.VALUE_MAX,
            mood = Balance.VALUE_MAX,
        )
    }
}

/**
 * Krankheitsstufe am Durchschnitt der drei Werte (Projektplan, Abschnitt 5.4).
 *
 * **Harte Regel: kein Tod, kein Zustand ohne Rückweg.** Die unterste Stufe ist ein
 * teilnahmsloses Pet — eine Leiche im Startbildschirm ist kein Ansporn, sondern ein
 * Deinstallationsgrund.
 */
enum class HealthStage {
    /** > 70 */
    HEALTHY,

    /** 40–70 */
    WEAKENED,

    /** 15–40 */
    SICK,

    /** < 15 */
    MISERABLE;

    /** Für Vergleiche "um mindestens eine Stufe besser". */
    val rank: Int get() = entries.size - ordinal

    companion object {
        fun of(average: Double): HealthStage = when {
            average > Balance.HEALTH_THRESHOLD_HEALTHY -> HEALTHY
            average >= Balance.HEALTH_THRESHOLD_WEAKENED -> WEAKENED
            average >= Balance.HEALTH_THRESHOLD_SICK -> SICK
            else -> MISERABLE
        }
    }
}
