package uk.spielerbohne.petodo.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Wiederholungsregel nach RFC 5545 — kein Eigenbau-Format (Einbahnstraße aus dem
 * Projektplan).
 *
 * Unterstützt wird die Teilmenge, die eine Aufgabenverwaltung braucht:
 * `FREQ`, `INTERVAL`, `BYDAY`, `COUNT`, `UNTIL`. Alles andere wird beim Lesen
 * übergangen statt abgelehnt — eine fremde Regel aus einem späteren Sync darf die App
 * nicht lahmlegen.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    /** Nur bei [Frequency.WEEKLY] von Bedeutung. Leer = der Wochentag der Fälligkeit. */
    val byDay: Set<DayOfWeek> = emptySet(),
    /** Verbleibende Wiederholungen. `null` = unbegrenzt. */
    val count: Int? = null,
    /** Letzter zulässiger Termin. `null` = unbegrenzt. */
    val until: LocalDate? = null,
) {

    enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    /** Zurück in einen RFC-5545-String. */
    fun toRfc5545(): String = buildList {
        add("FREQ=${frequency.name}")
        if (interval != 1) add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) {
            add("BYDAY=" + DayOfWeek.entries.filter { it in byDay }.joinToString(",") { CODES.getValue(it) })
        }
        count?.let { add("COUNT=$it") }
        until?.let { add("UNTIL=${it.format(UNTIL_FORMAT)}") }
    }.joinToString(";")

    /** Regel mit einer verbrauchten Wiederholung. Bei `COUNT=1` ist danach Schluss. */
    fun consumeOne(): RecurrenceRule? = when {
        count == null -> this
        count <= 1 -> null
        else -> copy(count = count - 1)
    }

    companion object {
        private val UNTIL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        private val CODES = mapOf(
            DayOfWeek.MONDAY to "MO",
            DayOfWeek.TUESDAY to "TU",
            DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR",
            DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val DAYS = CODES.entries.associate { (day, code) -> code to day }

        val WEEKDAYS = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

        /**
         * Liest eine Regel. Gibt `null` zurück, wenn nichts Brauchbares darin steht —
         * eine kaputte Regel bedeutet "wiederholt sich nicht", nicht "Absturz".
         */
        fun parse(text: String?): RecurrenceRule? {
            val body = text?.trim()?.removePrefix("RRULE:")?.takeIf { it.isNotEmpty() } ?: return null

            val parts = body.split(';')
                .mapNotNull { part ->
                    val index = part.indexOf('=')
                    if (index <= 0) null else part.substring(0, index).uppercase() to part.substring(index + 1)
                }
                .toMap()

            val frequency = parts["FREQ"]
                ?.let { value -> Frequency.entries.firstOrNull { it.name == value.uppercase() } }
                ?: return null

            return RecurrenceRule(
                frequency = frequency,
                interval = parts["INTERVAL"]?.toIntOrNull()?.takeIf { it >= 1 } ?: 1,
                byDay = parts["BYDAY"]
                    ?.split(',')
                    // "2MO" (zweiter Montag) wird auf den Wochentag reduziert; die
                    // Ordnungszahl unterstützt v1 nicht.
                    ?.mapNotNull { DAYS[it.trim().takeLast(2).uppercase()] }
                    ?.toSet()
                    .orEmpty(),
                count = parts["COUNT"]?.toIntOrNull()?.takeIf { it >= 1 },
                until = parts["UNTIL"]?.let(::parseUntil),
            )
        }

        /** `UNTIL` darf laut Norm auch einen Zeitanteil tragen ("20261231T235959Z"). */
        private fun parseUntil(value: String): LocalDate? =
            runCatching { LocalDate.parse(value.take(8), UNTIL_FORMAT) }.getOrNull()
    }
}
