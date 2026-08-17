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

    /** Abhaken bzw. wieder öffnen. */
    suspend fun setCompleted(id: String, completed: Boolean) {
        val entity = taskDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        taskDao.update(
            entity.copy(
                completedAt = if (completed) now else null,
                updatedAt = now,
            )
        )
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
