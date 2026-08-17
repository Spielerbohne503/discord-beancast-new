package uk.spielerbohne.petodo.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Aufgabe als reines Domänenobjekt. Die Room-Entity spiegelt dieselben Felder mit
 * primitiven Typen; die Umrechnung passiert an der Datenschicht-Grenze.
 *
 * Fälligkeit ist bewusst zweiteilig (Einbahnstraße):
 *  - [dueAt] als absoluter Zeitpunkt (UTC)
 *  - [dueTimeLocal] als lokale Uhrzeit, die der Nag als Anker benutzt
 * Ein Nag-Termin darf nie über +86.400.000 ms fortgeschrieben werden, sonst wandert die
 * 8-Uhr-Erinnerung bei jeder Zeitumstellung.
 */
data class Task(
    val id: String,
    val listId: String,
    val title: String,
    val note: String? = null,
    val dueAt: Instant? = null,
    val dueTimeLocal: LocalTime? = null,
    val hasTime: Boolean = false,
    val priority: Int = Priority.DEFAULT,
    val rrule: String? = null,
    val completedAt: Instant? = null,
    val sortKey: String,
    val nagCount: Int = 0,
    val nagLastAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
    val isCompleted: Boolean get() = completedAt != null
    val isOpen: Boolean get() = !isCompleted && !isDeleted

    /** Lokales Fälligkeitsdatum, sofern eine Fälligkeit gesetzt ist. */
    fun dueDate(zone: ZoneId): LocalDate? = dueAt?.atZone(zone)?.toLocalDate()

    /**
     * Überfällig heißt:
     *  - mit Uhrzeit: der Zeitpunkt ist vorbei
     *  - ohne Uhrzeit: der Tag ist vorbei (ein Tagestermin ist nicht um 00:01 überfällig)
     * Erledigte oder gelöschte Aufgaben sind nie überfällig.
     */
    fun isOverdue(now: Instant, zone: ZoneId): Boolean {
        if (!isOpen) return false
        val due = dueAt ?: return false
        return if (hasTime) {
            due.isBefore(now)
        } else {
            due.atZone(zone).toLocalDate().isBefore(now.atZone(zone).toLocalDate())
        }
    }

    /**
     * Wie viele volle Tage die Aufgabe überfällig ist (0, wenn nicht überfällig).
     * Gemessen in lokalen Kalendertagen, nicht in 24-Stunden-Blöcken.
     */
    fun overdueDays(now: Instant, zone: ZoneId): Long {
        if (!isOverdue(now, zone)) return 0
        val dueDate = dueAt?.atZone(zone)?.toLocalDate() ?: return 0
        val today = now.atZone(zone).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, today).coerceAtLeast(0)
    }
}
