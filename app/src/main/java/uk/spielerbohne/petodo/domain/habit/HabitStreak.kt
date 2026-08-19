package uk.spielerbohne.petodo.domain.habit

import java.time.LocalDate

/**
 * Serien für Gewohnheiten.
 *
 * Der Unterschied zur Serie im Rückblick: Hier zählen nur die Tage, an denen die
 * Gewohnheit **anstand**. Wer montags, mittwochs und freitags läuft, verliert seine Serie
 * nicht am Dienstag — sonst wäre jeder Zeitplan außer „täglich“ eine eingebaute Niederlage.
 */
object HabitStreak {

    /** Weiter zurück wird nicht gesucht. Schützt vor Endlosschleifen bei kaputten Daten. */
    private const val MAX_LOOKBACK_DAYS = 366L * 5

    /**
     * Die laufende Serie in Terminen.
     *
     * **Der heutige Tag zählt nicht gegen einen** — dieselbe Regel wie beim Rückblick und
     * beim Pet. Steht die Gewohnheit heute an und ist noch nicht abgehakt, läuft die Serie
     * weiter, bis der Tag vorbei ist.
     */
    fun current(checkins: Set<LocalDate>, schedule: HabitSchedule, today: LocalDate): Int {
        if (schedule.isEmpty) return 0

        var tag = lastScheduledOnOrBefore(schedule, today) ?: return 0

        // Heute noch offen: Der Tag ist noch nicht gelaufen, also fängt die Zählung
        // beim vorigen Termin an.
        if (tag == today && today !in checkins) {
            tag = previousScheduled(schedule, today) ?: return 0
        }

        var laenge = 0
        var geprueft = 0L
        while (tag in checkins && geprueft < MAX_LOOKBACK_DAYS) {
            laenge++
            val vorheriger = previousScheduled(schedule, tag) ?: break
            geprueft += java.time.temporal.ChronoUnit.DAYS.between(vorheriger, tag)
            tag = vorheriger
        }
        return laenge
    }

    /** Die längste Serie, die je zustande kam. */
    fun longest(checkins: Set<LocalDate>, schedule: HabitSchedule): Int {
        if (schedule.isEmpty || checkins.isEmpty()) return 0

        val sortiert = checkins.filter { schedule.isDueOn(it) }.sorted()
        if (sortiert.isEmpty()) return 0

        var beste = 1
        var laufend = 1

        for (index in 1 until sortiert.size) {
            val vorheriger = previousScheduled(schedule, sortiert[index])
            laufend = if (vorheriger == sortiert[index - 1]) laufend + 1 else 1
            if (laufend > beste) beste = laufend
        }
        return beste
    }

    /**
     * Wie viele der diese Woche anstehenden Termine schon erledigt sind.
     *
     * Die Woche beginnt am Montag — das ist hier keine Geschmacksfrage, sondern die
     * Wochendefinition, die zu den Wochentagsmasken passt.
     */
    fun weekProgress(
        checkins: Set<LocalDate>,
        schedule: HabitSchedule,
        today: LocalDate,
    ): Pair<Int, Int> {
        val wochenstart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        var erledigt = 0
        var anstehend = 0

        for (versatz in 0L..6L) {
            val tag = wochenstart.plusDays(versatz)
            if (!schedule.isDueOn(tag)) continue
            anstehend++
            if (tag in checkins) erledigt++
        }
        return erledigt to anstehend
    }

    /** Der letzte Termin an oder vor [date] — oder `null`, wenn der Zeitplan leer ist. */
    private fun lastScheduledOnOrBefore(schedule: HabitSchedule, date: LocalDate): LocalDate? {
        var tag = date
        repeat(7) {
            if (schedule.isDueOn(tag)) return tag
            tag = tag.minusDays(1)
        }
        return null
    }

    /** Der Termin davor. */
    private fun previousScheduled(schedule: HabitSchedule, date: LocalDate): LocalDate? {
        var tag = date.minusDays(1)
        repeat(7) {
            if (schedule.isDueOn(tag)) return tag
            tag = tag.minusDays(1)
        }
        return null
    }
}
