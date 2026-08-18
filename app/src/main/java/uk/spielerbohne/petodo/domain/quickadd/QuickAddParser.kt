package uk.spielerbohne.petodo.domain.quickadd

import uk.spielerbohne.petodo.domain.Balance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Was aus einer eingetippten Zeile herausgelesen wurde.
 *
 * [title] ist die Zeile ohne die erkannten Wörter — wer „morgen 9 Uhr Zahnarzt“ tippt,
 * bekommt die Aufgabe „Zahnarzt“ und nicht „morgen 9 Uhr Zahnarzt“ mit einem Termin
 * daneben.
 */
data class ParsedQuickAdd(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
) {
    val hasDue: Boolean get() = date != null
}

/**
 * Deutsche Datums- und Zeitangaben in der Schnell-Eingabe (Projektplan, v1.3).
 *
 * Erkannt wird eine überschaubare Liste — mehr nicht. Ein Parser, der alles versucht,
 * verschluckt irgendwann einen Aufgabentitel: Wer „Freitagsessen planen“ schreibt, meint
 * keinen Freitag. Deshalb greifen die Regeln nur an Wortgrenzen und nur bei den
 * Schreibweisen, die man tatsächlich tippt.
 *
 * Reine Funktion: [now] kommt herein, nichts wird aus der Systemuhr gelesen.
 */
object QuickAddParser {

    fun parse(input: String, now: LocalDateTime): ParsedQuickAdd {
        if (input.isBlank()) return ParsedQuickAdd(input.trim())

        val treffer = mutableListOf<IntRange>()

        val datum = findDate(input, now.toLocalDate(), treffer)
        val zeit = findTime(input, treffer)

        // Eine Uhrzeit ohne Tag meint heute — es sei denn, sie ist schon vorbei.
        val endgueltigesDatum = when {
            datum != null -> datum
            zeit == null -> null
            zeit > now.toLocalTime() -> now.toLocalDate()
            else -> now.toLocalDate().plusDays(1)
        }

        return ParsedQuickAdd(
            title = removeAll(input, treffer),
            date = endgueltigesDatum,
            time = zeit,
        )
    }

    // ------------------------------------------------------------------------ Datum

    private fun findDate(input: String, today: LocalDate, treffer: MutableList<IntRange>): LocalDate? {
        RELATIVE_DAYS.forEach { (muster, tage) ->
            muster.find(input)?.let { fund ->
                treffer += fund.range
                return today.plusDays(tage)
            }
        }

        IN_N.find(input)?.let { fund ->
            val menge = fund.groupValues[1].toLongOrNull() ?: return@let
            val einheit = fund.groupValues[2].lowercase()
            treffer += fund.range
            return if (einheit.startsWith("woche")) today.plusWeeks(menge) else today.plusDays(menge)
        }

        NEXT_WEEK.find(input)?.let { fund ->
            treffer += fund.range
            return today.plusWeeks(1)
        }

        WEEKDAY.find(input)?.let { fund ->
            val tag = WEEKDAYS[fund.groupValues[2].lowercase()] ?: return@let
            treffer += fund.range
            return nextWeekday(today, tag)
        }

        NUMERIC_DATE.find(input)?.let { fund ->
            val tag = fund.groupValues[1].toIntOrNull() ?: return@let
            val monat = fund.groupValues[2].toIntOrNull() ?: return@let
            val jahr = fund.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
            buildDate(tag, monat, jahr, today)?.let { datum ->
                treffer += fund.range
                return datum
            }
        }

        MONTH_NAME_DATE.find(input)?.let { fund ->
            val tag = fund.groupValues[1].toIntOrNull() ?: return@let
            val monat = MONTHS[fund.groupValues[2].lowercase()] ?: return@let
            val jahr = fund.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
            buildDate(tag, monat, jahr, today)?.let { datum ->
                treffer += fund.range
                return datum
            }
        }

        return null
    }

    /**
     * Ein Datum ohne Jahr meint das nächste Vorkommen.
     *
     * Wer am 30. Dezember „2.1.“ tippt, meint den Januar danach und nicht den vergangenen.
     */
    private fun buildDate(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (month !in 1..12) return null

        val vollesJahr = when {
            year == null -> today.year
            year < 100 -> 2000 + year
            else -> year
        }
        if (day !in 1..lengthOfMonth(month, vollesJahr)) return null

        val datum = LocalDate.of(vollesJahr, month, day)
        return if (year == null && datum.isBefore(today)) datum.plusYears(1) else datum
    }

    private fun lengthOfMonth(month: Int, year: Int): Int =
        LocalDate.of(year, month, 1).lengthOfMonth()

    /** Der nächste solche Wochentag — nie heute: „Montag“ am Montag meint den nächsten. */
    private fun nextWeekday(today: LocalDate, target: DayOfWeek): LocalDate {
        var datum = today.plusDays(1)
        while (datum.dayOfWeek != target) datum = datum.plusDays(1)
        return datum
    }

    // ---------------------------------------------------------------------- Uhrzeit

    private fun findTime(input: String, treffer: MutableList<IntRange>): LocalTime? {
        CLOCK_TIME.find(input)?.let { fund ->
            if (!fund.range.overlapsAny(treffer)) {
                val stunde = fund.groupValues[1].toIntOrNull() ?: return@let
                val minute = fund.groupValues[2].toIntOrNull() ?: 0
                if (stunde in 0..23 && minute in 0..59) {
                    treffer += fund.range
                    return LocalTime.of(stunde, minute)
                }
            }
        }

        HOUR_ONLY.find(input)?.let { fund ->
            if (!fund.range.overlapsAny(treffer)) {
                val stunde = fund.groupValues[1].toIntOrNull() ?: return@let
                val minute = fund.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
                if (stunde in 0..23 && minute in 0..59) {
                    treffer += fund.range
                    return LocalTime.of(stunde, minute)
                }
            }
        }

        VAGUE_TIME.find(input)?.let { fund ->
            if (!fund.range.overlapsAny(treffer)) {
                val stunde = VAGUE_HOURS[fund.groupValues[1].lowercase()] ?: return@let
                treffer += fund.range
                return LocalTime.of(stunde, 0)
            }
        }

        return null
    }

    // ------------------------------------------------------------------------ Rest

    private fun IntRange.overlapsAny(others: List<IntRange>): Boolean =
        others.any { first <= it.last && it.first <= last }

    /** Entfernt die erkannten Stellen und räumt die übrig gebliebenen Leerzeichen auf. */
    private fun removeAll(input: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return input.trim()

        val builder = StringBuilder(input)
        ranges.sortedByDescending { it.first }.forEach { bereich ->
            builder.delete(bereich.first, bereich.last + 1)
        }
        return builder.toString()
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim(',', ';', '-', '–')
            .trim()
    }

    // -------------------------------------------------------------------- Wortlisten

    /**
     * Wortgrenzen, die Umlaute kennen — ausgeschrieben statt über einen Schalter.
     *
     * Das Problem: `\b` zählt in Javas Regex nur `[a-zA-Z0-9_]` als Wortzeichen. Vor „ü“
     * steht damit **keine** Wortgrenze, und `\bübermorgen\b` findet nie etwas. Derselbe
     * Fallstrick trifft „März“ und „nächsten“.
     *
     * Die naheliegende Lösung `(?U)` ist eine **Falle**: Auf dem Rechner funktioniert sie,
     * auf dem Telefon nicht. Android führt reguläre Ausdrücke seit Neuerem über ICU aus,
     * und ICU kennt diesen Schalter nicht — der Ausdruck lässt sich dort nicht übersetzen,
     * das ganze Objekt kommt nicht hoch, und die App startet nicht mehr. Ein JVM-Test
     * bemerkt davon nichts, weil er Javas eigene Regex-Maschine benutzt.
     *
     * Vorausschau und Rückschau auf Unicode-Klassen können beide Maschinen.
     */
    private const val WORD_START = """(?<![\p{L}\p{N}_])"""
    private const val WORD_END = """(?![\p{L}\p{N}_])"""

    private val OPTION = """(?:$WORD_START(?:am|an|bis|für)\s+)?"""

    private val RELATIVE_DAYS: List<Pair<Regex, Long>> = listOf(
        word("übermorgen") to 2L,
        word("uebermorgen") to 2L,
        word("morgen") to 1L,
        word("heute") to 0L,
    )

    private val IN_N = Regex(
        """${WORD_START}in\s+(\d{1,3})\s+(tagen|tage|tag|wochen|woche)$WORD_END""",
        RegexOption.IGNORE_CASE,
    )

    private val NEXT_WEEK = Regex(
        """${WORD_START}nächste[rn]?\s+woche$WORD_END""",
        RegexOption.IGNORE_CASE,
    )

    private val WEEKDAY = Regex(
        """$OPTION(nächsten|nächste|kommenden|kommende)?\s*$WORD_START(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonnabend|sonntag)$WORD_END""",
        RegexOption.IGNORE_CASE,
    )

    private val WEEKDAYS = mapOf(
        "montag" to DayOfWeek.MONDAY,
        "dienstag" to DayOfWeek.TUESDAY,
        "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY,
        "freitag" to DayOfWeek.FRIDAY,
        "samstag" to DayOfWeek.SATURDAY,
        "sonnabend" to DayOfWeek.SATURDAY,
        "sonntag" to DayOfWeek.SUNDAY,
    )

    /** `12.8.`, `12.08.2026`, `1.9.26` */
    private val NUMERIC_DATE = Regex(
        """$OPTION$WORD_START(\d{1,2})\.\s?(\d{1,2})\.(?:\s?(\d{2,4}))?""",
        RegexOption.IGNORE_CASE,
    )

    /** `12. August`, `3 Januar 2027` */
    private val MONTH_NAME_DATE = Regex(
        """$OPTION$WORD_START(\d{1,2})\.?\s+(januar|februar|märz|maerz|april|mai|juni|juli|august|september|oktober|november|dezember)(?:\s+(\d{4}))?$WORD_END""",
        RegexOption.IGNORE_CASE,
    )

    private val MONTHS = mapOf(
        "januar" to 1, "februar" to 2, "märz" to 3, "maerz" to 3, "april" to 4,
        "mai" to 5, "juni" to 6, "juli" to 7, "august" to 8, "september" to 9,
        "oktober" to 10, "november" to 11, "dezember" to 12,
    )

    /** `14:30`, `um 9:05` */
    private val CLOCK_TIME = Regex(
        """(?:${WORD_START}um\s+)?$WORD_START(\d{1,2}):(\d{2})$WORD_END""",
        RegexOption.IGNORE_CASE,
    )

    /** `9 Uhr`, `um 9 Uhr`, `9.30 Uhr` */
    private val HOUR_ONLY = Regex(
        """(?:${WORD_START}um\s+)?$WORD_START(\d{1,2})(?:[.:](\d{2}))?\s*uhr$WORD_END""",
        RegexOption.IGNORE_CASE,
    )

    private val VAGUE_TIME = Regex(
        """$WORD_START(morgens|vormittags|mittags|nachmittags|abends|nachts)$WORD_END""",
        RegexOption.IGNORE_CASE,
    )

    private val VAGUE_HOURS = mapOf(
        "morgens" to Balance.VAGUE_MORNING_HOUR,
        "vormittags" to Balance.VAGUE_MORNING_HOUR,
        "mittags" to Balance.VAGUE_NOON_HOUR,
        "nachmittags" to Balance.VAGUE_AFTERNOON_HOUR,
        "abends" to Balance.VAGUE_EVENING_HOUR,
        "nachts" to Balance.VAGUE_NIGHT_HOUR,
    )

    private fun word(text: String) =
        Regex("""$OPTION$WORD_START$text$WORD_END""", RegexOption.IGNORE_CASE)
}
