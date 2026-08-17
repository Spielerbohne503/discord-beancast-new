package uk.spielerbohne.petodo.domain.today

import uk.spielerbohne.petodo.domain.model.Task
import java.time.Instant
import java.time.ZoneId

/**
 * Die drei Blöcke des Heute-Screens plus die heute erledigten Aufgaben.
 *
 * Reine Funktion: gleiche Eingabe, gleiches Ergebnis. `now` und `zone` kommen von außen,
 * damit die Gruppierung ohne Emulator und ohne Systemuhr testbar bleibt.
 */
data class TodayBoard(
    val overdue: List<Task> = emptyList(),
    val today: List<Task> = emptyList(),
    val later: List<Task> = emptyList(),
    val doneToday: List<Task> = emptyList(),
) {
    val openCount: Int get() = overdue.size + today.size + later.size
    val isEmpty: Boolean get() = openCount == 0 && doneToday.isEmpty()
}

object TodayGrouping {

    /**
     * Sortiert offene Aufgaben in überfällig / heute / später.
     *
     * - Gelöschte Aufgaben (Tombstone) tauchen nirgends auf.
     * - Erledigte Aufgaben erscheinen nur, wenn sie *heute* erledigt wurden — sonst
     *   verschwindet die gerade abgehakte Zeile sofort und man kann sie nicht zurückholen.
     * - Aufgaben ohne Fälligkeit landen unter "später"; sonst wären sie unsichtbar.
     */
    fun group(tasks: List<Task>, now: Instant, zone: ZoneId): TodayBoard {
        val today = now.atZone(zone).toLocalDate()

        val overdue = mutableListOf<Task>()
        val dueToday = mutableListOf<Task>()
        val later = mutableListOf<Task>()
        val doneToday = mutableListOf<Task>()

        for (task in tasks) {
            if (task.isDeleted) continue
            if (task.isCompleted) {
                val completedDate = task.completedAt?.atZone(zone)?.toLocalDate()
                if (completedDate == today) doneToday += task
                continue
            }
            when {
                task.isOverdue(now, zone) -> overdue += task
                task.dueDate(zone) == today -> dueToday += task
                else -> later += task
            }
        }

        return TodayBoard(
            overdue = overdue.sortedWith(byDueThenSortKey),
            today = dueToday.sortedWith(byDueThenSortKey),
            later = later.sortedWith(byDueThenSortKey),
            doneToday = doneToday.sortedByDescending { it.completedAt },
        )
    }

    /** Früheste Fälligkeit zuerst, Aufgaben ohne Fälligkeit ans Ende, dann Fractional Index. */
    private val byDueThenSortKey: Comparator<Task> =
        compareBy<Task> { it.dueAt == null }
            .thenBy { it.dueAt ?: Instant.EPOCH }
            .thenBy { it.sortKey }
}
