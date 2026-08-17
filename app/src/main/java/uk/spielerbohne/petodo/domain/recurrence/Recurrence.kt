package uk.spielerbohne.petodo.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Was passiert, wenn eine wiederkehrende Aufgabe abgehakt oder überfällig wird.
 *
 * Die Regel aus dem Projektplan (v1.1): **Von einer wiederkehrenden Aufgabe existiert
 * immer nur eine offene Instanz.** Verpasste Termine werden übersprungen und gezählt,
 * erzeugen aber keine zusätzliche Last — sonst tötet eine tägliche Aufgabe das Pet,
 * während man im Urlaub ist.
 */
data class RecurrenceAdvance(
    /** Der nächste Termin, oder `null`, wenn die Serie zu Ende ist. */
    val nextDue: LocalDate?,
    /** Wie viele Termine dabei übersprungen wurden. */
    val skipped: Int,
    /** Die verbleibende Regel; `null`, wenn die Serie aufgebraucht ist. */
    val remainingRule: RecurrenceRule?,
) {
    val isFinished: Boolean get() = nextDue == null
}

object Recurrence {

    /** Obergrenze gegen Endlosschleifen bei absurden Regeln. */
    private const val MAX_STEPS = 5_000

    /**
     * Der nächste Termin nach [current] gemäß [rule].
     *
     * Monatsende wird abgeschnitten statt übersprungen: Der 31. Januar wird im Februar
     * zum 28. (bzw. 29.), nicht ausgelassen. Für eine Aufgabenverwaltung ist das die
     * nützlichere Auslegung — eine Monatsaufgabe soll auch im Februar erscheinen.
     */
    fun nextAfter(rule: RecurrenceRule, current: LocalDate): LocalDate = when (rule.frequency) {
        RecurrenceRule.Frequency.DAILY -> current.plusDays(rule.interval.toLong())

        RecurrenceRule.Frequency.WEEKLY -> nextWeekly(rule, current)

        RecurrenceRule.Frequency.MONTHLY -> current.plusMonths(rule.interval.toLong())

        RecurrenceRule.Frequency.YEARLY -> current.plusYears(rule.interval.toLong())
    }

    /**
     * Rückt eine Serie auf den ersten Termin **nach** [today] vor.
     *
     * Beim Abhaken am Fälligkeitstag ist das schlicht der nächste Termin. Nach zwei
     * Wochen Urlaub werden die dazwischenliegenden Termine übersprungen und in
     * [RecurrenceAdvance.skipped] gemeldet.
     */
    fun advance(rule: RecurrenceRule, currentDue: LocalDate, today: LocalDate): RecurrenceAdvance {
        var remaining: RecurrenceRule? = rule
        var candidate = currentDue
        var skipped = -1 // der erste Schritt ist der reguläre, kein verpasster

        repeat(MAX_STEPS) {
            val active = remaining ?: return RecurrenceAdvance(null, skipped.coerceAtLeast(0), null)
            candidate = nextAfter(active, candidate)
            skipped++

            // Serienende erreicht?
            active.until?.let { until ->
                if (candidate.isAfter(until)) {
                    return RecurrenceAdvance(null, skipped.coerceAtLeast(0), null)
                }
            }
            remaining = active.consumeOne()

            if (candidate.isAfter(today)) {
                return RecurrenceAdvance(candidate, skipped.coerceAtLeast(0), remaining)
            }
            if (remaining == null) {
                return RecurrenceAdvance(null, skipped.coerceAtLeast(0), null)
            }
        }

        // Sollte nie eintreten; lieber die Serie beenden als endlos rechnen.
        return RecurrenceAdvance(null, skipped.coerceAtLeast(0), null)
    }

    private fun nextWeekly(rule: RecurrenceRule, current: LocalDate): LocalDate {
        if (rule.byDay.isEmpty()) return current.plusWeeks(rule.interval.toLong())

        // Innerhalb derselben Woche den nächsten gewählten Wochentag suchen …
        val ordered = rule.byDay.sortedBy { it.value }
        val currentValue = current.dayOfWeek.value
        ordered.firstOrNull { it.value > currentValue }?.let { day ->
            return current.plusDays((day.value - currentValue).toLong())
        }

        // … sonst in die nächste zulässige Woche springen.
        val startOfWeek = current.minusDays((currentValue - DayOfWeek.MONDAY.value).toLong())
        val targetWeek = startOfWeek.plusWeeks(rule.interval.toLong())
        val first = ordered.first()
        return targetWeek.plusDays((first.value - DayOfWeek.MONDAY.value).toLong())
    }
}
