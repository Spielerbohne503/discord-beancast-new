package uk.spielerbohne.petodo.domain.stats

import java.time.LocalDate

/** Ein Tag im Rückblick. */
data class DayCount(val date: LocalDate, val count: Int)

/** Was der Rückblick zeigt. */
data class Stats(
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val days: List<DayCount> = emptyList(),
    val totalCompleted: Int = 0,
    val focusRounds: Int = 0,
) {
    val busiestDay: DayCount? get() = days.maxByOrNull { it.count }?.takeIf { it.count > 0 }
    val periodCompleted: Int get() = days.sumOf { it.count }
}

/**
 * Der Rückblick (Projektplan, v2.1).
 *
 * Reine Rechnung über Tage, keine Zeitpunkte: Was an welchem Tag erledigt wurde, hat die
 * aufrufende Schicht schon in Ortszeit umgerechnet. Sonst hinge die Serie an der
 * Zeitzone, und eine Reise nach Osten würde sie zerreißen.
 */
object Statistics {

    /**
     * Die laufende Serie in Tagen.
     *
     * **Der heutige Tag zählt nicht gegen einen.** Wer gestern etwas geschafft hat und
     * heute um neun Uhr morgens in die App schaut, hat seine Serie noch — sonst stünde
     * jeden Morgen eine Null da, und eine App, die einen jeden Morgen bei null anfangen
     * lässt, macht keine Lust.
     */
    fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        val start = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }

        var tag = start
        var laenge = 0
        while (tag in days) {
            laenge++
            tag = tag.minusDays(1)
        }
        return laenge
    }

    /** Die längste Serie, die je zustande kam. */
    fun longestStreak(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0

        var beste = 0
        var laufend = 0
        var vorheriger: LocalDate? = null

        days.sorted().forEach { tag ->
            laufend = if (vorheriger != null && vorheriger!!.plusDays(1) == tag) laufend + 1 else 1
            if (laufend > beste) beste = laufend
            vorheriger = tag
        }
        return beste
    }

    /**
     * Ein Balken je Tag im Zeitraum — auch für Tage ohne Eintrag.
     *
     * Die Lücken gehören dazu: Ein Diagramm, das nur die guten Tage zeigt, ist kein
     * Rückblick, sondern eine Werbebroschüre.
     */
    fun perDay(dates: List<LocalDate>, from: LocalDate, to: LocalDate): List<DayCount> {
        if (from.isAfter(to)) return emptyList()

        val gezaehlt = dates.groupingBy { it }.eachCount()
        val ergebnis = mutableListOf<DayCount>()
        var tag = from
        while (!tag.isAfter(to)) {
            ergebnis += DayCount(tag, gezaehlt[tag] ?: 0)
            tag = tag.plusDays(1)
        }
        return ergebnis
    }

    /**
     * Alles zusammen.
     *
     * @param completedDates ein Eintrag je erledigter Aufgabe, in Ortszeit
     * @param windowDays wie viele Tage das Diagramm zeigt, heute eingeschlossen
     */
    fun of(
        completedDates: List<LocalDate>,
        today: LocalDate,
        windowDays: Long,
        focusRounds: Int = 0,
    ): Stats {
        val tage = completedDates.toSet()
        return Stats(
            streak = streak(tage, today),
            longestStreak = longestStreak(tage),
            days = perDay(completedDates, today.minusDays(windowDays - 1), today),
            totalCompleted = completedDates.size,
            focusRounds = focusRounds,
        )
    }
}
