package uk.spielerbohne.petodo.domain.model

import uk.spielerbohne.petodo.domain.habit.HabitSchedule
import java.time.Instant
import java.time.LocalDate

/**
 * Eine Gewohnheit.
 *
 * Bewusst **keine** Aufgabe: kein Fälligkeitsdatum, keine Priorität, keine Erinnerung.
 * Gewohnheiten werden nie überfällig, mahnen nie und machen das Pet nie krank
 * (Projektplan, v2.0). Dass sie in einer eigenen Tabelle stehen, ist keine Formsache —
 * es macht diese Regeln strukturell unumgehbar. Was keine Fälligkeit hat, kann nicht
 * überfällig werden.
 */
data class Habit(
    val id: String,
    val name: String,
    val schedule: HabitSchedule,
    val colorArgb: Int? = null,
    val sortKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    fun isDueOn(date: LocalDate): Boolean = schedule.isDueOn(date)
}

/** Eine Gewohnheit samt ihrer Einträge — was die Oberfläche braucht. */
data class HabitWithCheckins(
    val habit: Habit,
    val checkins: Set<LocalDate>,
)
