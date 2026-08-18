package uk.spielerbohne.petodo.domain.pet

import uk.spielerbohne.petodo.domain.Balance
import kotlin.math.roundToInt

/**
 * Die Levelkurve: Die erste Stufe kostet 100 XP, jede weitere das 1,5-fache der vorigen.
 *
 * Level schalten Skins frei — sonst nichts. Keine Währung, kein Shop, keine Accessoires.
 */
object Level {

    /** Obergrenze gegen Endlosschleifen bei absurden XP-Zahlen. */
    private const val MAX_LEVEL = 200

    /** Was die Stufe von [level] auf [level] + 1 kostet. */
    fun costOf(level: Int): Int {
        var cost = Balance.LEVEL_FIRST_STEP_XP
        repeat((level - 1).coerceAtLeast(0)) { cost *= Balance.LEVEL_GROWTH }
        return cost.roundToInt()
    }

    /** Gesamte XP, die man für [level] gebraucht hat. */
    fun totalXpFor(level: Int): Int {
        var total = 0
        for (step in 1 until level.coerceAtLeast(1)) total += costOf(step)
        return total
    }

    /** Das Level zu einem XP-Stand. Level 1 ab 0 XP. */
    fun forXp(xp: Int): Int {
        var level = 1
        var remaining = xp.coerceAtLeast(0)
        while (level < MAX_LEVEL) {
            val cost = costOf(level)
            if (remaining < cost) break
            remaining -= cost
            level++
        }
        return level
    }

    /** Fortschritt innerhalb des aktuellen Levels, 0.0 bis 1.0. */
    fun progressWithin(xp: Int): Double {
        val level = forXp(xp)
        val into = (xp - totalXpFor(level)).coerceAtLeast(0)
        return (into.toDouble() / costOf(level)).coerceIn(0.0, 1.0)
    }

    /** Wie viele XP bis zur nächsten Stufe fehlen. */
    fun xpToNextLevel(xp: Int): Int {
        val level = forXp(xp)
        return (totalXpFor(level) + costOf(level) - xp).coerceAtLeast(0)
    }
}
