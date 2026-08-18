package uk.spielerbohne.petodo.domain.today

import uk.spielerbohne.petodo.domain.Balance
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
    /**
     * Früher erledigt — der Rückblick ganz unten.
     *
     * Absichtlich ein eigener Block und nicht bei [doneToday] dabei: Was heute geschafft
     * wurde, ist der Tagesstand und gehört sichtbar dazu. Was gestern geschafft wurde,
     * ist Vergangenheit und darf die Liste nicht mehr füllen.
     */
    val doneEarlier: List<Task> = emptyList(),
) {
    val openCount: Int get() = overdue.size + today.size + later.size
    val isEmpty: Boolean get() = openCount == 0 && doneToday.isEmpty() && doneEarlier.isEmpty()
}

object TodayGrouping {

    /**
     * Sortiert offene Aufgaben in überfällig / heute / später.
     *
     * - Gelöschte Aufgaben (Tombstone) tauchen nirgends auf.
     * - Heute erledigte Aufgaben bleiben stehen — sonst verschwindet die gerade
     *   abgehakte Zeile sofort und man kann sie nicht zurückholen.
     * - Früher erledigte landen im Archiv ([TodayBoard.doneEarlier]) und fallen nach
     *   [Balance.ARCHIVE_DAYS] Tagen auch daraus heraus.
     * - Aufgaben ohne Fälligkeit landen unter "später"; sonst wären sie unsichtbar.
     */
    fun group(tasks: List<Task>, now: Instant, zone: ZoneId): TodayBoard {
        val today = now.atZone(zone).toLocalDate()

        val overdue = mutableListOf<Task>()
        val dueToday = mutableListOf<Task>()
        val later = mutableListOf<Task>()
        val doneToday = mutableListOf<Task>()
        val doneEarlier = mutableListOf<Task>()
        val archiveFrom = today.minusDays(Balance.ARCHIVE_DAYS)

        for (task in tasks) {
            if (task.isDeleted) continue
            // Unteraufgaben erscheinen unter ihrer Aufgabe, nicht als eigene Zeile.
            if (task.isSubtask) continue
            if (task.isCompleted) {
                val completedDate = task.completedAt?.atZone(zone)?.toLocalDate() ?: continue
                when {
                    completedDate == today -> doneToday += task
                    // Nicht in der Zukunft (verstellte Uhr) und nicht zu alt.
                    completedDate.isAfter(today) -> Unit
                    completedDate.isAfter(archiveFrom) -> doneEarlier += task
                }
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
            doneEarlier = doneEarlier
                .sortedByDescending { it.completedAt }
                .take(Balance.ARCHIVE_MAX_ROWS),
        )
    }

    /** Früheste Fälligkeit zuerst, Aufgaben ohne Fälligkeit ans Ende, dann Fractional Index. */
    private val byDueThenSortKey: Comparator<Task> =
        compareBy<Task> { it.dueAt == null }
            .thenBy { it.dueAt ?: Instant.EPOCH }
            .thenBy { it.sortKey }
}
