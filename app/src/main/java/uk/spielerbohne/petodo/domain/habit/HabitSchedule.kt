package uk.spielerbohne.petodo.domain.habit

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * An welchen Wochentagen eine Gewohnheit ansteht.
 *
 * Als Bitmaske und nicht als Liste: Sieben Wochentage passen in eine Zahl, die sich ohne
 * eigene Tabelle speichern und ohne Umweg vergleichen lässt. Bit 0 ist Montag.
 *
 * Ein Zeitplan sagt **nur**, wann etwas ansteht — nie, dass etwas fällig ist. Gewohnheiten
 * werden nie überfällig, mahnen nie und machen das Pet nie krank (Projektplan, v2.0). Das
 * ist der ganze Unterschied zu einer Aufgabe: Eine verpasste Gewohnheit ist ein Tag ohne
 * Haken, kein Versäumnis, das sich anhäuft.
 */
@JvmInline
value class HabitSchedule(val mask: Int) {

    /** Ob die Gewohnheit an diesem Tag ansteht. */
    fun isDueOn(date: LocalDate): Boolean = isDueOn(date.dayOfWeek)

    fun isDueOn(day: DayOfWeek): Boolean = (mask shr (day.value - 1)) and 1 == 1

    /** Wie oft in der Woche — zugleich das Wochenziel. */
    val timesPerWeek: Int get() = ALL_DAYS.count { isDueOn(it) }

    val isEmpty: Boolean get() = mask and DAILY.mask == 0

    val isDaily: Boolean get() = mask and DAILY.mask == DAILY.mask

    /** Einen Wochentag an- oder abschalten. */
    fun toggle(day: DayOfWeek): HabitSchedule =
        HabitSchedule(mask xor (1 shl (day.value - 1)))

    fun days(): List<DayOfWeek> = ALL_DAYS.filter { isDueOn(it) }

    companion object {
        val ALL_DAYS: List<DayOfWeek> = DayOfWeek.entries.toList()

        val DAILY = HabitSchedule(0b111_1111)

        /** Montag bis Freitag. */
        val WEEKDAYS = HabitSchedule(0b001_1111)

        val WEEKEND = HabitSchedule(0b110_0000)

        val NONE = HabitSchedule(0)

        /**
         * Aus der Datenbank gelesene Masken werden beschnitten.
         *
         * Ein Wert mit gesetzten Bits jenseits der sieben Tage käme aus einer
         * beschädigten Zeile — er darf keine Endlosschleife bei der Seriensuche auslösen.
         */
        fun of(mask: Int): HabitSchedule = HabitSchedule(mask and DAILY.mask)
    }
}
