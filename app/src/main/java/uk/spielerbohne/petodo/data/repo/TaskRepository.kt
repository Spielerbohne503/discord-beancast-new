package uk.spielerbohne.petodo.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.spielerbohne.petodo.data.db.dao.TaskDao
import uk.spielerbohne.petodo.data.db.dao.TaskListDao
import uk.spielerbohne.petodo.data.db.entity.TaskEntity
import uk.spielerbohne.petodo.data.mapper.toDomain
import uk.spielerbohne.petodo.data.mapper.toHhMmOrNull
import uk.spielerbohne.petodo.domain.model.Priority
import uk.spielerbohne.petodo.domain.model.Task
import uk.spielerbohne.petodo.domain.model.TaskList
import uk.spielerbohne.petodo.domain.nag.NagSchedule
import uk.spielerbohne.petodo.domain.recurrence.Recurrence
import uk.spielerbohne.petodo.domain.recurrence.RecurrenceRule
import uk.spielerbohne.petodo.domain.sort.FractionalIndex
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Flow-basierte Repository-Schicht über den Aufgaben.
 *
 * Regeln, die hier durchgesetzt werden:
 *  - Löschen ist immer ein Tombstone, nie ein DELETE.
 *  - Jede Änderung setzt `updatedAt` (Sync-Ticket).
 *  - Neue Aufgaben bekommen eine UUID und einen Fractional Index ans Listenende.
 *
 * Die Uhr kommt über [clock] herein, damit Zeitverhalten testbar bleibt.
 */
class TaskRepository(
    private val taskDao: TaskDao,
    private val taskListDao: TaskListDao,
    private val clock: Clock,
) {

    fun observeTasks(): Flow<List<Task>> =
        taskDao.observeAll().map { entities -> entities.map(TaskEntity::toDomain) }

    fun observeTask(id: String): Flow<Task?> =
        taskDao.observeById(id).map { it?.toDomain() }

    fun observeLists(): Flow<List<TaskList>> =
        taskListDao.observeAll().map { lists -> lists.map { it.toDomain() } }

    suspend fun findTask(id: String): Task? = taskDao.findById(id)?.toDomain()

    /**
     * Legt eine Aufgabe an und gibt ihre ID zurück.
     *
     * [dueDate] ohne [dueTime] bedeutet Tagestermin: gespeichert wird der Tagesbeginn,
     * überfällig wird die Aufgabe erst nach Ablauf des Tages.
     */
    suspend fun createTask(
        title: String,
        note: String? = null,
        dueDate: LocalDate? = null,
        dueTime: LocalTime? = null,
        listId: String = TaskList.ID_INBOX,
        priority: Int = Priority.DEFAULT,
        parentId: String? = null,
        zone: ZoneId = clock.zone,
    ): String {
        val now = Instant.now(clock)
        val id = UUID.randomUUID().toString()
        val sortKey = FractionalIndex.after(taskDao.highestSortKey())
        val dueAt = dueDate?.let { date ->
            (dueTime?.let { date.atTime(it) } ?: date.atStartOfDay())
                .atZone(zone)
                .toInstant()
        }

        taskDao.insert(
            TaskEntity(
                id = id,
                listId = listId,
                parentId = parentId,
                title = title.trim(),
                note = note?.takeIf { it.isNotBlank() },
                dueAt = dueAt?.toEpochMilli(),
                dueTimeLocal = dueTime.toHhMmOrNull(),
                hasTime = dueTime != null,
                priority = Priority.coerce(priority),
                rrule = null,
                completedAt = null,
                sortKey = sortKey,
                createdAt = now.toEpochMilli(),
                updatedAt = now.toEpochMilli(),
            )
        )
        return id
    }

    /**
     * Abhaken bzw. wieder öffnen.
     *
     * Bei einer wiederkehrenden Aufgabe heißt Abhaken: Die Serie rückt auf den nächsten
     * Termin vor, statt zu enden. Es bleibt genau eine offene Instanz — verpasste
     * Termine werden übersprungen und in `missedCount` gezählt.
     */
    suspend fun setCompleted(id: String, completed: Boolean, zone: ZoneId = clock.zone) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock)
        val millis = now.toEpochMilli()

        val rule = RecurrenceRule.parse(entity.rrule)
        val currentDue = entity.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

        if (completed && rule != null && currentDue != null) {
            val advance = Recurrence.advance(rule, currentDue, now.atZone(zone).toLocalDate())
            val nextDue = advance.nextDue

            if (nextDue != null) {
                val anchor = NagSchedule.anchorTime(entity.toDomain(), zone)
                taskDao.update(
                    entity.copy(
                        dueAt = nextDue.atTime(anchor).atZone(zone).toInstant().toEpochMilli(),
                        completedAt = null,
                        missedCount = entity.missedCount + advance.skipped,
                        nagCount = 0,
                        snoozedUntil = null,
                        rrule = advance.remainingRule?.toRfc5545(),
                        updatedAt = millis,
                    )
                )
                return
            }

            // Serienende: normal abhaken, Regel entfernen.
            taskDao.update(entity.copy(completedAt = millis, rrule = null, updatedAt = millis))
            return
        }

        taskDao.update(
            entity.copy(
                completedAt = if (completed) millis else null,
                updatedAt = millis,
            )
        )
    }

    /** Wiederholungsregel setzen oder entfernen. */
    suspend fun setRecurrence(id: String, rule: RecurrenceRule?) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        taskDao.update(entity.copy(rrule = rule?.toRfc5545(), updatedAt = now))
    }

    /** Titel, Notiz und Fälligkeit bearbeiten. */
    suspend fun updateTask(
        id: String,
        title: String,
        note: String?,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        zone: ZoneId = clock.zone,
    ) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        val dueAt = dueDate?.let { date ->
            (dueTime?.let { date.atTime(it) } ?: date.atStartOfDay())
                .atZone(zone)
                .toInstant()
        }
        taskDao.update(
            entity.copy(
                title = title.trim(),
                note = note?.takeIf { it.isNotBlank() },
                dueAt = dueAt?.toEpochMilli(),
                dueTimeLocal = dueTime.toHhMmOrNull(),
                hasTime = dueTime != null,
                // Fälligkeit geändert = neue Nag-Kette.
                nagCount = if (entity.dueAt != dueAt?.toEpochMilli()) 0 else entity.nagCount,
                updatedAt = now,
            )
        )
    }

    // ------------------------------------------------------ Priorität, Liste, Unteraufgaben

    /** Priorität setzen (0 niedrig · 1 normal · 2 hoch · 3 dringend). */
    suspend fun setPriority(id: String, priority: Int) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        taskDao.update(entity.copy(priority = Priority.coerce(priority), updatedAt = now))
    }

    /**
     * Aufgabe in eine andere Liste verschieben.
     *
     * Wandert sie in eine Liste mit `excludeFromNag`, verstummt der Nag — genau dafür ist
     * die "Irgendwann"-Liste da.
     */
    suspend fun moveToList(id: String, listId: String) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        taskDao.update(entity.copy(listId = listId, updatedAt = now))
    }

    /** Unteraufgaben einer Aufgabe. */
    fun observeSubtasks(parentId: String): Flow<List<Task>> =
        taskDao.observeSubtasks(parentId).map { entities -> entities.map(TaskEntity::toDomain) }

    // ------------------------------------------------------------------ Nag (Phase 2)

    /** Alle offenen Aufgaben mit Fälligkeit — Grundlage fürs Neuregistrieren der Alarme. */
    suspend fun openTasksWithDueDate(): List<Task> =
        taskDao.openWithDueDate().map(TaskEntity::toDomain)

    /** Überfällige offene Aufgaben, ohne die aus ausgenommenen Listen. */
    suspend fun overdueTasks(now: Instant, zone: ZoneId): List<Task> {
        val excluded = listIdsExcludedFromNag()
        return openTasksWithDueDate()
            .filter { it.listId !in excluded && it.isOverdue(now, zone) }
            .sortedBy { it.dueAt }
    }

    suspend fun listIdsExcludedFromNag(): Set<String> = taskListDao.idsExcludedFromNag().toSet()

    suspend fun isListExcludedFromNag(listId: String): Boolean =
        listId in listIdsExcludedFromNag()

    /** Nach einer gemeldeten Erinnerung: Zähler und Zeitpunkt fortschreiben. */
    suspend fun markNagged(id: String, nagCount: Int, at: Instant) {
        val entity = taskDao.findById(id) ?: return
        taskDao.update(
            entity.copy(
                nagCount = nagCount,
                nagLastAt = at.toEpochMilli(),
                updatedAt = at.toEpochMilli(),
            )
        )
    }

    /** "+1 Std" aus der Benachrichtigung: setzt `snoozedUntil`, ohne die Fälligkeit zu ändern. */
    suspend fun snooze(id: String, until: Instant) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        taskDao.update(entity.copy(snoozedUntil = until.toEpochMilli(), updatedAt = now))
    }

    /**
     * "Morgen" aus der Benachrichtigung: schiebt die Fälligkeit einen Kalendertag weiter
     * und beginnt die Nag-Kette von vorn.
     */
    suspend fun postponeToTomorrow(id: String, zone: ZoneId = clock.zone) {
        val entity = taskDao.findById(id) ?: return
        val task = entity.toDomain()
        val now = Instant.now(clock)
        val neuerTermin = NagSchedule.postponeToTomorrow(task, now, zone)
        taskDao.update(
            entity.copy(
                dueAt = neuerTermin.toEpochMilli(),
                nagCount = 0,
                snoozedUntil = null,
                updatedAt = now.toEpochMilli(),
            )
        )
    }

    /**
     * Mehrere Aufgaben auf einmal verschieben ("Verschieben" über dem Überfällig-Block).
     * Gibt zurück, wie viele tatsächlich bewegt wurden.
     */
    suspend fun postponeAllToTomorrow(ids: List<String>, zone: ZoneId = clock.zone): Int {
        var moved = 0
        ids.forEach { id ->
            val before = taskDao.findById(id)?.dueAt
            postponeToTomorrow(id, zone)
            if (taskDao.findById(id)?.dueAt != before) moved++
        }
        return moved
    }

    /** Löschen heißt Tombstone setzen — die Zeile bleibt für den späteren Sync stehen. */
    suspend fun delete(id: String) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        taskDao.update(entity.copy(deletedAt = now, updatedAt = now))
    }

    /** Rücknahme eines Tombstones (Rückgängig direkt nach dem Löschen). */
    suspend fun restore(id: String) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        taskDao.update(entity.copy(deletedAt = null, updatedAt = now))
    }
}
