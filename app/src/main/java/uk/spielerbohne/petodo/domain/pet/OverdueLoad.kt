package uk.spielerbohne.petodo.domain.pet

import uk.spielerbohne.petodo.domain.Balance
import uk.spielerbohne.petodo.domain.model.Priority
import uk.spielerbohne.petodo.domain.model.Task
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Die Überfälligkeitslast L (Projektplan, Abschnitt 5.2).
 *
 * ```
 * L = Σ über offene, überfällige Aufgaben:
 *       prioFaktor (0,5 · 1,0 · 1,5 · 2,0)
 *     + 0,2 × angefangene Überfälligkeitswochen
 * ```
 *
 * Gedeckelt bei 10. Aufgaben in Listen mit `excludeFromNag` zählen nicht, Aufgaben ohne
 * Fälligkeitsdatum zählen nie — Krankheit hängt an überfälligen, nie an offenen Aufgaben.
 */
object OverdueLoad {

    fun of(
        tasks: List<Task>,
        excludedListIds: Set<String>,
        now: Instant,
        zone: ZoneId,
    ): Double {
        val load = tasks
            .filter { it.isOpen && it.listId !in excludedListIds && it.isOverdue(now, zone) }
            .sumOf { contributionOf(it, now, zone) }

        return load.coerceIn(0.0, Balance.LOAD_CAP)
    }

    /** Beitrag einer einzelnen Aufgabe — ohne Deckelung. */
    fun contributionOf(task: Task, now: Instant, zone: ZoneId): Double {
        val priorityFactor = Balance.LOAD_PRIORITY_FACTOR[Priority.coerce(task.priority)]
        val weeks = startedOverdueWeeks(task, now, zone)
        return priorityFactor + Balance.LOAD_PER_STARTED_WEEK * weeks
    }

    /** Angefangene Wochen: Ein Tag überfällig ist bereits die erste Woche. */
    fun startedOverdueWeeks(task: Task, now: Instant, zone: ZoneId): Int {
        val days = task.overdueDays(now, zone)
        if (days <= 0) return 0
        return ceil(days / 7.0).toInt()
    }

    /** mBasis = 1 + (L / 10) × 2  →  1,0 bis 3,0 */
    fun baseMultiplier(load: Double): Double =
        1.0 + (load.coerceIn(0.0, Balance.LOAD_CAP) / Balance.LOAD_CAP) * Balance.MULTIPLIER_LOAD_SPAN

    /**
     * mLaune = mBasis × (1 + 3 × (1 − (energie + sättigung) / 200))
     *
     * Die Laune hängt an den beiden anderen Werten: Versäumnisse verstärken sich
     * gegenseitig, statt dass jeder Wert für sich vor sich hin driftet.
     */
    fun moodMultiplier(load: Double, energy: Double, satiety: Double): Double {
        val base = baseMultiplier(load)
        val wellFed = (energy + satiety) / (2 * Balance.VALUE_MAX)
        return base * (1.0 + Balance.MOOD_COUPLING * (1.0 - wellFed.coerceIn(0.0, 1.0)))
    }
}
