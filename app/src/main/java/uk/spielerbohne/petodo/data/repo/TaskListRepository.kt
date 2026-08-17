package uk.spielerbohne.petodo.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.spielerbohne.petodo.data.db.dao.TaskListDao
import uk.spielerbohne.petodo.data.db.entity.TaskListEntity
import uk.spielerbohne.petodo.data.mapper.toDomain
import uk.spielerbohne.petodo.domain.model.TaskList
import uk.spielerbohne.petodo.domain.sort.FractionalIndex
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Listen anlegen, umbenennen, einfärben.
 *
 * Die Inbox lässt sich nicht löschen — eine App ohne Eingangsliste hätte keinen Ort für
 * neue Aufgaben.
 */
class TaskListRepository(
    private val taskListDao: TaskListDao,
    private val clock: Clock,
) {

    fun observeLists(): Flow<List<TaskList>> =
        taskListDao.observeAll().map { lists -> lists.map(TaskListEntity::toDomain) }

    suspend fun lists(): List<TaskList> = taskListDao.all().map(TaskListEntity::toDomain)

    suspend fun createList(name: String, colorArgb: Int?, excludeFromNag: Boolean = false): String? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val now = Instant.now(clock).toEpochMilli()
        val id = UUID.randomUUID().toString()
        val sortKey = FractionalIndex.after(taskListDao.all().maxByOrNull { it.sortKey }?.sortKey)

        taskListDao.insertIfAbsent(
            TaskListEntity(
                id = id,
                name = trimmed,
                colorArgb = colorArgb,
                sortKey = sortKey,
                excludeFromNag = excludeFromNag,
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    suspend fun updateList(
        id: String,
        name: String? = null,
        colorArgb: Int? = null,
        excludeFromNag: Boolean? = null,
    ) {
        val entity = taskListDao.findById(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        taskListDao.update(
            entity.copy(
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: entity.name,
                colorArgb = colorArgb ?: entity.colorArgb,
                excludeFromNag = excludeFromNag ?: entity.excludeFromNag,
                updatedAt = now,
            )
        )
    }

    /** Tombstone. Die Inbox bleibt bestehen, egal was passiert. */
    suspend fun deleteList(id: String): Boolean {
        if (id == TaskList.ID_INBOX) return false
        val entity = taskListDao.findById(id) ?: return false
        val now = Instant.now(clock).toEpochMilli()
        taskListDao.update(entity.copy(deletedAt = now, updatedAt = now))
        return true
    }
}
