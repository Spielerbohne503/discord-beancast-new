package uk.spielerbohne.petodo.data.db

import uk.spielerbohne.petodo.domain.model.TaskList
import uk.spielerbohne.petodo.domain.sort.FractionalIndex

/**
 * Startdaten für den ersten App-Start: zwei Listen.
 *
 * "irgendwann" hat `excludeFromNag = true` — das ist das Druckventil. Die UI zeigt in v1
 * nur die Inbox, die zweite Liste existiert trotzdem im Modell (Einbahnstraße `listId`).
 *
 * Die Namen stehen hier als Rohtexte, weil sie in die Datenbank geschrieben werden und
 * nicht bei jedem Sprachwechsel wandern dürfen; die Anzeige benutzt `strings.xml`.
 */
object SeedData {

    val inboxSortKey: String = FractionalIndex.initial()
    val somedaySortKey: String = FractionalIndex.after(inboxSortKey)

    fun seedSql(now: Long): List<String> = listOf(
        insertList(TaskList.ID_INBOX, "Inbox", inboxSortKey, excludeFromNag = false, now = now),
        insertList(TaskList.ID_SOMEDAY, "Irgendwann", somedaySortKey, excludeFromNag = true, now = now),
    )

    private fun insertList(
        id: String,
        name: String,
        sortKey: String,
        excludeFromNag: Boolean,
        now: Long,
    ): String = """
        INSERT OR IGNORE INTO task_lists
            (id, name, colorArgb, sortKey, excludeFromNag, createdAt, updatedAt, deletedAt)
        VALUES ('$id', '$name', NULL, '$sortKey', ${if (excludeFromNag) 1 else 0}, $now, $now, NULL)
    """.trimIndent()
}
