package uk.spielerbohne.petodo.domain.filter

import uk.spielerbohne.petodo.domain.model.Task
import java.time.Instant
import java.time.ZoneId

/**
 * Welche Aufgaben eine Ansicht zeigt.
 *
 * Reine Auswahl, keine Speicherung: Smart Lists sind Filter über denselben Bestand,
 * keine eigenen Tabellen. Deshalb kostet eine neue Ansicht später keine Migration.
 */
sealed interface TaskScope {

    /** Alle offenen Aufgaben, egal welche Liste. */
    data object AllOpen : TaskScope

    /** Offene Aufgaben mit Fälligkeit bis einschließlich in sieben Tagen, plus überfällige. */
    data object NextSevenDays : TaskScope

    /** Zuletzt erledigte Aufgaben. */
    data object Completed : TaskScope

    /** Eine konkrete Liste. */
    data class InList(val listId: String) : TaskScope
}

object TaskFilter {

    /** Wie weit "7 Tage" reicht. */
    const val SEVEN_DAYS = 7L

    /**
     * Wendet Bereich und Suchtext an.
     *
     * Gesucht wird in Titel und Notiz, ohne Rücksicht auf Groß- und Kleinschreibung.
     * Unteraufgaben tauchen nur auf, wenn tatsächlich gesucht wird — sonst stünde eine
     * Unteraufgabe zusammenhanglos zwischen den Aufgaben.
     */
    fun apply(
        tasks: List<Task>,
        scope: TaskScope,
        query: String,
        now: Instant,
        zone: ZoneId,
    ): List<Task> {
        val trimmedQuery = query.trim()
        val searching = trimmedQuery.isNotEmpty()
        val today = now.atZone(zone).toLocalDate()

        return tasks
            .asSequence()
            .filterNot { it.isDeleted }
            .filter { searching || !it.isSubtask }
            .filter { matchesScope(it, scope, now, zone, today) }
            .filter { !searching || matchesQuery(it, trimmedQuery) }
            .sortedWith(orderFor(scope))
            .toList()
    }

    private fun matchesScope(
        task: Task,
        scope: TaskScope,
        now: Instant,
        zone: ZoneId,
        today: java.time.LocalDate,
    ): Boolean = when (scope) {
        TaskScope.AllOpen -> task.isOpen

        TaskScope.Completed -> task.isCompleted

        is TaskScope.InList -> task.isOpen && task.listId == scope.listId

        TaskScope.NextSevenDays -> {
            if (!task.isOpen) {
                false
            } else {
                val dueDate = task.dueDate(zone)
                when {
                    dueDate == null -> false
                    // Überfälliges gehört dazu — es ist das Dringendste, was es gibt.
                    task.isOverdue(now, zone) -> true
                    else -> !dueDate.isAfter(today.plusDays(SEVEN_DAYS))
                }
            }
        }
    }

    private fun matchesQuery(task: Task, query: String): Boolean =
        task.title.contains(query, ignoreCase = true) ||
            task.note?.contains(query, ignoreCase = true) == true

    /**
     * Eine konkrete Liste behält die Reihenfolge von Hand (Fractional Index) — nur dort
     * ergibt Ziehen einen Sinn. Smart Lists sortieren nach Fälligkeit, weil sie Aufgaben
     * aus mehreren Listen zusammenwürfeln. Erledigte: das zuletzt Erledigte oben.
     */
    fun orderFor(scope: TaskScope): Comparator<Task> = when (scope) {
        TaskScope.Completed -> compareByDescending<Task> { it.completedAt }.thenBy { it.sortKey }

        is TaskScope.InList -> compareBy { it.sortKey }

        else -> compareBy<Task> { it.dueAt == null }
            .thenBy { it.dueAt ?: Instant.EPOCH }
            .thenByDescending { it.priority }
            .thenBy { it.sortKey }
    }

    /** Ob in dieser Ansicht von Hand umsortiert werden darf. */
    fun isManuallyOrdered(scope: TaskScope, query: String): Boolean =
        scope is TaskScope.InList && query.isBlank()
}
